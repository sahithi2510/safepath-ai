"""SafePath AI - SHAP Explainability Engine.

Provides mathematically verified SHAP explanations for road-segment risk predictions
using TreeExplainer on XGBoost (primary) and Random Forest (baseline) models.

Enforces:
- Exact 15-feature schema alignment and training-scaler transformation.
- Additive reconstruction check: expected_value + sum(shap_values) == model_output.
- Strictly non-causal associative phrasing (no causal claims).
- Transparent handling of missing/imputed and out-of-range features.
- Synthetic training provenance flagging.
"""

from __future__ import annotations

import json
import logging
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple, Union

import joblib
import numpy as np
import pandas as pd
import shap
from sklearn.preprocessing import MinMaxScaler

from scripts.explainability.schemas import (
    DISCLAIMER_TEXT,
    SYNTHETIC_WARNING_TEXT,
    FeatureContribution,
    GlobalExplanation,
    LocalExplanation,
)
from scripts.model_training.schemas import FEATURE_DEFAULTS, FEATURE_NAMES

logger = logging.getLogger(__name__)

# Application-defined risk thresholds (matching ml-service)
RISK_THRESHOLDS = {
    "VERY_LOW": 0.2,
    "LOW": 0.4,
    "MODERATE": 0.6,
    "HIGH": 0.8,
}


def risk_to_level(risk_score: float) -> str:
    """Map normalized continuous risk score to categorical level."""
    if risk_score < RISK_THRESHOLDS["VERY_LOW"]:
        return "VERY_LOW"
    elif risk_score < RISK_THRESHOLDS["LOW"]:
        return "LOW"
    elif risk_score < RISK_THRESHOLDS["MODERATE"]:
        return "MODERATE"
    elif risk_score < RISK_THRESHOLDS["HIGH"]:
        return "HIGH"
    return "VERY_HIGH"


# Non-causal associative phrasing templates
FEATURE_DESCRIPTIONS = {
    "crime_density_500m": {
        "pos": "Higher localized crime density (500m) associates with higher model predicted risk.",
        "neg": "Low localized crime density (500m) associates with lower model predicted risk.",
    },
    "crime_density_1km": {
        "pos": "Broader area crime density (1km) associates with higher model predicted risk.",
        "neg": "Low broader area crime density (1km) associates with lower model predicted risk.",
    },
    "severity_weighted_density": {
        "pos": "Historical incident severity weighting associates with higher model predicted risk.",
        "neg": "Absence of severe historical incidents associates with lower model predicted risk.",
    },
    "recent_crime_density": {
        "pos": "Recent temporal incident clustering associates with higher model predicted risk.",
        "neg": "Lack of recent incident activity associates with lower model predicted risk.",
    },
    "night_crime_density": {
        "pos": "Historical nighttime incident density associates with higher model predicted risk.",
        "neg": "Low historical nighttime incidents associates with lower model predicted risk.",
    },
    "hour_of_day": {
        "pos": "Current hour of travel correlates with higher risk periods in training distribution.",
        "neg": "Current hour of travel correlates with lower risk periods in training distribution.",
    },
    "day_of_week": {
        "pos": "Day of week correlates with higher activity periods in training distribution.",
        "neg": "Day of week correlates with lower activity periods in training distribution.",
    },
    "lighting_score": {
        "pos": "Reduced street lighting score associates with higher model predicted risk.",
        "neg": "Active street lighting score associates with lower model predicted risk.",
    },
    "activity_proxy": {
        "pos": "Lower pedestrian/commercial activity proxy associates with higher model predicted risk.",
        "neg": "Higher pedestrian/commercial activity proxy associates with lower model predicted risk.",
    },
    "nearest_police_m": {
        "pos": "Greater distance to nearest police facility associates with higher model predicted risk.",
        "neg": "Closer proximity to nearest police facility associates with lower model predicted risk.",
    },
    "nearest_hospital_m": {
        "pos": "Greater distance to nearest hospital associates with higher model predicted risk.",
        "neg": "Closer proximity to nearest hospital associates with lower model predicted risk.",
    },
    "nearest_transport_m": {
        "pos": "Greater distance to public transit associates with higher model predicted risk.",
        "neg": "Closer proximity to public transit associates with lower model predicted risk.",
    },
    "weather_precipitation": {
        "pos": "Adverse precipitation conditions associate with higher model predicted risk.",
        "neg": "Clear weather conditions associate with lower model predicted risk.",
    },
    "weather_visibility": {
        "pos": "Low atmospheric visibility associates with higher model predicted risk.",
        "neg": "High atmospheric visibility associates with lower model predicted risk.",
    },
    "verified_report_count": {
        "pos": "Verified community safety reports associate with higher model predicted risk.",
        "neg": "Absence of community safety reports associates with lower model predicted risk.",
    },
}


class ShapExplainerEngine:
    """Manages TreeExplainer initialization, local attribution, additive verification,

    and global importance summaries.
    """

    def __init__(
        self,
        model_dir: Optional[Union[str, Path]] = None,
        model: Optional[Any] = None,
        scaler: Optional[MinMaxScaler] = None,
        metadata: Optional[Dict[str, Any]] = None,
        feature_names: Optional[List[str]] = None,
    ):
        self.model = model
        self.scaler = scaler
        self.metadata = metadata or {}
        self.feature_names = feature_names or FEATURE_NAMES

        if model_dir:
            self._load_from_dir(Path(model_dir))

        if self.model is None:
            raise ValueError("No model provided or found.")

        # Determine model characteristics
        self.model_type = self.metadata.get("model_type", type(self.model).__name__.lower())
        self.task_type = self.metadata.get("task_type", "classifier")
        self.model_version = self.metadata.get("version", "1.0.0")
        self.is_synthetic = self.metadata.get("is_synthetic_evaluation", True)

        # Initialize TreeExplainer
        self.explainer = shap.TreeExplainer(self.model)
        # Warm-up call so TreeExplainer finalizes its internal base_value/expected_value
        try:
            dummy_x = np.zeros((1, len(self.feature_names)), dtype=np.float64)
            self.explainer.shap_values(dummy_x)
        except Exception:
            pass
        self.expected_value = self._extract_base_value()

    def _load_from_dir(self, directory: Path) -> None:
        """Load model, scaler, feature names, and metadata from directory."""
        model_path = directory / "model.joblib"
        scaler_path = directory / "scaler.joblib"
        meta_path = directory / "metadata.json"
        feat_path = directory / "feature_names.json"

        if not model_path.exists():
            raise FileNotFoundError(f"Model binary not found at {model_path}")

        self.model = joblib.load(model_path)

        if scaler_path.exists():
            self.scaler = joblib.load(scaler_path)

        if meta_path.exists():
            with open(meta_path, "r", encoding="utf-8") as f:
                self.metadata = json.load(f)

        if feat_path.exists():
            with open(feat_path, "r", encoding="utf-8") as f:
                self.feature_names = json.load(f)

    def _extract_base_value(self) -> float:
        """Extract scalar expected value E[f(X)] for the positive/target class."""
        ev = self.explainer.expected_value
        if isinstance(ev, (list, np.ndarray)):
            # Binary classifier with classes [0, 1] -> take class 1
            if len(ev) >= 2:
                return float(ev[1])
            return float(ev[0])
        return float(ev)

    def _preprocess_input(
        self,
        features: Union[Dict[str, Any], Any, pd.Series],
    ) -> Tuple[np.ndarray, np.ndarray, Dict[str, bool]]:
        """Validate, impute, and scale input features in exact FEATURE_NAMES order.

        Returns:
            (X_raw, X_scaled, imputed_flags)
        """
        raw_vals = []
        imputed_flags = {}

        # Handle Pydantic models or dicts or pandas Series
        get_val = (
            lambda k: getattr(features, k, None)
            if hasattr(features, k)
            else (features.get(k) if isinstance(features, (dict, pd.Series)) else None)
        )

        for col in self.feature_names:
            v = get_val(col)
            if v is None or pd.isna(v):
                raw_vals.append(FEATURE_DEFAULTS.get(col, 0.0))
                imputed_flags[col] = True
            else:
                try:
                    num_val = float(v)
                    # Out-of-range sanitization
                    if col in ("nearest_police_m", "nearest_hospital_m", "nearest_transport_m") and num_val < 0:
                        logger.warning("Clamping negative distance for %s: %f -> 0.0", col, num_val)
                        num_val = 0.0
                    elif col == "lighting_score" and (num_val < 0.0 or num_val > 1.0):
                        num_val = max(0.0, min(1.0, num_val))
                    elif col == "hour_of_day" and (num_val < 0.0 or num_val > 23.0):
                        num_val = max(0.0, min(23.0, num_val))

                    raw_vals.append(num_val)
                    imputed_flags[col] = False
                except (ValueError, TypeError):
                    raw_vals.append(FEATURE_DEFAULTS.get(col, 0.0))
                    imputed_flags[col] = True

        X_raw = np.array([raw_vals], dtype=np.float64)

        if self.scaler is not None:
            X_scaled = self.scaler.transform(X_raw)
        else:
            X_scaled = X_raw.copy()

        return X_raw, X_scaled, imputed_flags

    def explain_local(
        self,
        features: Union[Dict[str, Any], Any, pd.Series],
        segment_id: str = "SEG_QUERY",
        tolerance: float = 1e-4,
    ) -> LocalExplanation:
        """Generate a complete, mathematically verified local SHAP explanation."""
        X_raw, X_scaled, imputed_flags = self._preprocess_input(features)

        # 1. Compute SHAP attributions
        raw_shap = self.explainer.shap_values(X_scaled)

        # Robust extraction across SHAP versions & model architectures
        if isinstance(raw_shap, list):
            # List of arrays per class (e.g., [class_0, class_1])
            phi = raw_shap[1][0] if len(raw_shap) >= 2 else raw_shap[0][0]
        elif isinstance(raw_shap, np.ndarray):
            if raw_shap.ndim == 3:
                # Shape (1, n_features, n_classes)
                phi = raw_shap[0, :, 1] if raw_shap.shape[2] >= 2 else raw_shap[0, :, 0]
            elif raw_shap.ndim == 2:
                # Shape (1, n_features)
                phi = raw_shap[0]
            else:
                phi = raw_shap
        else:
            raise TypeError(f"Unexpected SHAP values type: {type(raw_shap)}")

        phi = np.array(phi, dtype=np.float64)
        self.expected_value = self._extract_base_value()

        # 2. Compute model output and normalized risk score
        is_xgb = "xgb" in self.model_type.lower() or hasattr(self.model, "get_booster")

        if self.task_type == "classifier":
            if is_xgb:
                # XGBoost default margin output
                model_output = float(self.model.predict(X_scaled, output_margin=True)[0])
                predicted_risk = float(1.0 / (1.0 + np.exp(-model_output)))
            else:
                # Random Forest / scikit-learn classifier
                probs = self.model.predict_proba(X_scaled)[0]
                predicted_risk = float(probs[1] if len(probs) >= 2 else probs[0])
                model_output = predicted_risk
        else:
            # Regressor
            pred = float(self.model.predict(X_scaled)[0])
            model_output = pred
            predicted_risk = max(0.0, min(1.0, pred))

        predicted_risk = max(0.0, min(1.0, predicted_risk))
        safety_score = round(1.0 - predicted_risk, 4)
        risk_lvl = risk_to_level(predicted_risk)

        # 3. Additive reconstruction check
        sum_phi = float(np.sum(phi))
        reconstruction_value = self.expected_value + sum_phi
        reconstruction_error = abs(reconstruction_value - model_output)
        reconstruction_valid = reconstruction_error <= tolerance

        # 4. Construct feature contributions
        contributions: List[FeatureContribution] = []
        raw_vals = X_raw[0]
        scaled_vals = X_scaled[0]

        for i, name in enumerate(self.feature_names):
            c_val = float(phi[i])
            direction = (
                "increases_risk"
                if c_val > 0.001
                else ("decreases_risk" if c_val < -0.001 else "neutral")
            )

            # Retrieve non-causal associative phrasing
            desc_dict = FEATURE_DESCRIPTIONS.get(name, {})
            if c_val >= 0:
                desc = desc_dict.get(
                    "pos",
                    f"Feature {name} associates with increased model predicted risk."
                )
            else:
                desc = desc_dict.get(
                    "neg",
                    f"Feature {name} associates with decreased model predicted risk."
                )

            contributions.append(
                FeatureContribution(
                    name=name,
                    value=scaled_vals[i],
                    raw_value=raw_vals[i],
                    contribution=c_val,
                    direction=direction,
                    description=desc,
                    is_imputed=imputed_flags.get(name, False),
                )
            )

        # Partition top factors
        risk_factors = sorted(
            [c for c in contributions if c.contribution > 0.001],
            key=lambda x: x.contribution,
            reverse=True,
        )
        protective_factors = sorted(
            [c for c in contributions if c.contribution < -0.001],
            key=lambda x: x.contribution,
        )

        return LocalExplanation(
            segment_id=segment_id,
            model_type=self.model_type,
            task_type=self.task_type,
            model_version=self.model_version,
            model_output=model_output,
            predicted_risk_score=predicted_risk,
            safety_score=safety_score,
            risk_level=risk_lvl,
            expected_value=self.expected_value,
            sum_contributions=sum_phi,
            reconstruction_value=reconstruction_value,
            reconstruction_error=reconstruction_error,
            reconstruction_valid=reconstruction_valid,
            top_risk_factors=risk_factors,
            top_protective_factors=protective_factors,
            all_contributions=contributions,
            is_synthetic_demonstration=self.is_synthetic,
        )

    def explain_global(
        self,
        df: pd.DataFrame,
        n_samples: int = 100,
    ) -> GlobalExplanation:
        """Compute global feature importance by averaging absolute SHAP values across samples."""
        if df.empty:
            raise ValueError("Input DataFrame is empty.")

        sample_df = df.head(n_samples)
        raw_rows = []
        for _, row in sample_df.iterrows():
            X_raw, _, _ = self._preprocess_input(row)
            raw_rows.append(X_raw[0])

        X_mat_raw = np.array(raw_rows, dtype=np.float64)
        X_mat_scaled = (
            self.scaler.transform(X_mat_raw)
            if self.scaler is not None
            else X_mat_raw
        )

        raw_shap = self.explainer.shap_values(X_mat_scaled)

        # Extract 2D matrix (n_samples, n_features)
        if isinstance(raw_shap, list):
            phi_mat = raw_shap[1] if len(raw_shap) >= 2 else raw_shap[0]
        elif isinstance(raw_shap, np.ndarray):
            if raw_shap.ndim == 3:
                phi_mat = raw_shap[:, :, 1] if raw_shap.shape[2] >= 2 else raw_shap[:, :, 0]
            else:
                phi_mat = raw_shap
        else:
            raise TypeError(f"Unexpected SHAP values type: {type(raw_shap)}")

        mean_abs = np.mean(np.abs(phi_mat), axis=0)
        mean_abs_dict = dict(zip(self.feature_names, [float(v) for v in mean_abs]))

        ranked = sorted(mean_abs_dict.items(), key=lambda x: x[1], reverse=True)
        ranked_list = [
            {"rank": idx + 1, "feature": k, "mean_abs_shap": round(v, 4)}
            for idx, (k, v) in enumerate(ranked)
        ]

        return GlobalExplanation(
            model_type=self.model_type,
            task_type=self.task_type,
            model_version=self.model_version,
            n_samples=len(sample_df),
            mean_abs_shap=mean_abs_dict,
            ranked_features=ranked_list,
            is_synthetic_demonstration=self.is_synthetic,
        )
