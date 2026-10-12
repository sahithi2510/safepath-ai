"""
SafePath AI ML Service — Model Service

Loads trained model artifacts and serves predictions.
Handles the case where models are not yet trained gracefully.

IMPORTANT: Metrics are NOT hard-coded here. They are read from model metadata
and only populated after actual training and evaluation.
"""
from __future__ import annotations

import json
import logging
from pathlib import Path
from typing import Any, Dict, List, Optional

import joblib
import numpy as np
import shap

from app.services.preprocessor import Preprocessor, FEATURE_NAMES
from app.models.schemas import (
    RiskFeatures, PredictRiskResponse, ExplainResponse, FactorContribution
)

logger = logging.getLogger(__name__)


# ------------------------------------------------------------------
# Application-defined risk categories
# IMPLEMENTATION CHOICE: These thresholds are not universal scientific
# standards. See docs/limitations.md Section 2.3.
# ------------------------------------------------------------------
RISK_THRESHOLDS = {
    "VERY_LOW":  0.2,
    "LOW":       0.4,
    "MODERATE":  0.6,
    "HIGH":      0.8,
    # Above 0.8 → VERY_HIGH
}


def risk_to_level(risk_score: float) -> str:
    if risk_score < RISK_THRESHOLDS["VERY_LOW"]:
        return "VERY_LOW"
    elif risk_score < RISK_THRESHOLDS["LOW"]:
        return "LOW"
    elif risk_score < RISK_THRESHOLDS["MODERATE"]:
        return "MODERATE"
    elif risk_score < RISK_THRESHOLDS["HIGH"]:
        return "HIGH"
    return "VERY_HIGH"


class ModelService:
    """
    Loads and serves ML model predictions.

    If no models are found, is_ready() returns False and
    prediction endpoints return 503.
    """

    def __init__(self, model_dir: str):
        self.model_dir = Path(model_dir)
        self.model = None
        self.explainer = None
        self.preprocessor: Optional[Preprocessor] = None
        self.metadata: Dict[str, Any] = {}
        self.model_type: Optional[str] = None

    def load(self) -> None:
        """Try to load XGBoost model first, then Random Forest as fallback."""
        for model_type in ["xgboost", "random_forest"]:
            model_path = self.model_dir / model_type / "model.joblib"
            scaler_path = self.model_dir / model_type / "scaler.joblib"
            meta_path = self.model_dir / model_type / "metadata.json"

            if not model_path.exists():
                logger.debug("Model not found at %s, trying next.", model_path)
                continue

            try:
                self.model = joblib.load(model_path)
                self.preprocessor = Preprocessor(scaler_path if scaler_path.exists() else None)
                self.model_type = model_type

                if meta_path.exists():
                    with open(meta_path, "r") as f:
                        self.metadata = json.load(f)

                # Initialize SHAP explainer
                try:
                    self.explainer = shap.TreeExplainer(self.model)
                    logger.info("SHAP TreeExplainer initialized for %s.", model_type)
                except Exception as e:
                    logger.warning("SHAP explainer init failed: %s. Explain endpoint will be unavailable.", e)

                logger.info("Model loaded: %s (version: %s)", model_type,
                            self.metadata.get("version", "unknown"))
                return

            except Exception as e:
                logger.error("Failed to load model from %s: %s", model_path, e)
                continue

        logger.warning(
            "No models could be loaded from '%s'. "
            "Run training scripts to generate models. "
            "See scripts/ directory.",
            self.model_dir
        )

    def is_ready(self) -> bool:
        return self.model is not None

    def is_explainer_ready(self) -> bool:
        return self.explainer is not None

    def predict(self, segment_id: str, features: RiskFeatures) -> PredictRiskResponse:
        """Predict risk for a single segment."""
        arr, completeness = self.preprocessor.transform(features)
        risk_score = float(self._predict_raw(arr)[0])
        risk_score = max(0.0, min(1.0, risk_score))

        return PredictRiskResponse(
            segment_id=segment_id,
            risk_score=risk_score,
            safety_score=round(1.0 - risk_score, 4),
            risk_level=risk_to_level(risk_score),
            model_version=self.metadata.get("version", "unknown"),
            model_trained_at=self.metadata.get("trained_at"),
            feature_completeness=round(completeness, 3),
            demo=False,
        )

    def predict_batch(self, segments: list) -> list:
        """Batch predict for multiple segments."""
        results = []
        for seg in segments:
            results.append(self.predict(seg.segment_id, seg.features))
        return results

    def explain(self, segment_id: str, features: RiskFeatures) -> ExplainResponse:
        """Generate SHAP explanation for a single segment."""
        arr, _ = self.preprocessor.transform(features)
        is_xgb = (self.model_type == "xgboost") or hasattr(self.model, "get_booster")

        if hasattr(self.model, "predict_proba"):
            if is_xgb:
                # XGBoost default margin output
                model_output = float(self.model.predict(arr, output_margin=True)[0])
                risk_score = float(1.0 / (1.0 + np.exp(-model_output)))
            else:
                # Random Forest / sklearn
                risk_score = float(self.model.predict_proba(arr)[:, 1][0])
                model_output = risk_score
        else:
            # Regressor
            pred = float(self.model.predict(arr)[0])
            risk_score = max(0.0, min(1.0, pred))
            model_output = pred

        risk_score = max(0.0, min(1.0, risk_score))
        safety_score = round(1.0 - risk_score, 4)

        raw_shap = self.explainer.shap_values(arr)
        # Robust extraction across SHAP versions & model architectures
        if isinstance(raw_shap, list):
            sv = raw_shap[1][0] if len(raw_shap) >= 2 else raw_shap[0][0]
        elif isinstance(raw_shap, np.ndarray):
            if raw_shap.ndim == 3:
                sv = raw_shap[0, :, 1] if raw_shap.shape[2] >= 2 else raw_shap[0, :, 0]
            elif raw_shap.ndim == 2:
                sv = raw_shap[0]
            else:
                sv = raw_shap
        else:
            sv = np.array(raw_shap, dtype=np.float64)

        # Extract base value safely
        ev = self.explainer.expected_value
        if isinstance(ev, (list, np.ndarray)):
            base_value = float(ev[1]) if len(ev) >= 2 else float(ev[0])
        else:
            base_value = float(ev)

        # Verification of additive efficiency
        sum_phi = float(np.sum(sv))
        reconstruction_error = abs((base_value + sum_phi) - model_output)
        reconstruction_valid = reconstruction_error <= 1e-4

        factors = []
        feature_values = arr[0].tolist()
        for i, name in enumerate(FEATURE_NAMES):
            c_val = float(sv[i])
            if abs(c_val) > 0.001:  # Only include meaningful contributions
                factors.append(FactorContribution(
                    name=name,
                    value=round(feature_values[i], 4),
                    contribution=round(c_val, 4),
                    description=self._describe_factor(name, c_val),
                ))

        # Sort by absolute contribution magnitude
        factors.sort(key=lambda f: abs(f.contribution), reverse=True)

        return ExplainResponse(
            segment_id=segment_id,
            risk_score=round(risk_score, 4),
            safety_score=safety_score,
            base_risk=round(base_value, 4),
            model_type=self.model_type or "xgboost",
            factors=factors[:10],  # Top 10 factors
            reconstruction_valid=reconstruction_valid,
            is_synthetic_demonstration=self.metadata.get("is_synthetic_evaluation", True),
            model_version=self.metadata.get("version", "unknown"),
        )

    def _predict_raw(self, arr: np.ndarray) -> np.ndarray:
        """Get raw model prediction as a 1D array of risk scores."""
        if hasattr(self.model, "predict_proba"):
            # Classifier: return probability of high-risk class
            return self.model.predict_proba(arr)[:, 1]
        else:
            # Regressor: return direct prediction
            return self.model.predict(arr)

    def _describe_factor(self, name: str, contribution: float) -> str:
        """Generate a strictly non-causal associative description of a feature's contribution."""
        direction = "higher" if contribution > 0 else "lower"
        descriptions = {
            "crime_density_500m":        f"Localized crime density associates with {direction} model predicted risk",
            "crime_density_1km":         f"Broader area crime density associates with {direction} model predicted risk",
            "severity_weighted_density": f"Severe crime history associates with {direction} model predicted risk",
            "recent_crime_density":      f"Recent crime activity associates with {direction} model predicted risk",
            "night_crime_density":       f"Night-time crime patterns associate with {direction} model predicted risk",
            "lighting_score":            f"Street lighting level associates with {direction} model predicted risk",
            "activity_proxy":            f"Pedestrian activity level associates with {direction} model predicted risk",
            "nearest_police_m":          f"Police station proximity associates with {direction} model predicted risk",
            "nearest_hospital_m":        f"Hospital proximity associates with {direction} model predicted risk",
            "nearest_transport_m":       f"Public transit proximity associates with {direction} model predicted risk",
            "weather_precipitation":     f"Precipitation level associates with {direction} model predicted risk",
            "weather_visibility":        f"Atmospheric visibility associates with {direction} model predicted risk",
            "verified_report_count":     f"Verified community safety reports associate with {direction} model predicted risk",
            "hour_of_day":              f"Time of day correlates with {direction} risk in training distribution",
            "day_of_week":              f"Day of week correlates with {direction} risk in training distribution",
        }
        return descriptions.get(name, f"{name} associates with {direction} model predicted risk")

