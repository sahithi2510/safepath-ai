"""
SafePath AI ML Service — Feature Preprocessor

Transforms RiskFeatures into a normalized numpy array for model input.
Imputes missing values with documented defaults.

IMPORTANT: This must be IDENTICAL to training-time preprocessing.
The fitted scaler is loaded from the model directory.
"""
from __future__ import annotations

import json
import logging
from pathlib import Path
from typing import List, Optional, Tuple

import joblib
import numpy as np
import pandas as pd

from app.models.schemas import RiskFeatures

logger = logging.getLogger(__name__)

# -------------------------------------------------------
# Feature ordering — MUST match training order exactly
# -------------------------------------------------------
FEATURE_NAMES: List[str] = [
    "crime_density_500m",
    "crime_density_1km",
    "severity_weighted_density",
    "recent_crime_density",
    "night_crime_density",
    "hour_of_day",
    "day_of_week",
    "lighting_score",
    "activity_proxy",
    "nearest_police_m",
    "nearest_hospital_m",
    "nearest_transport_m",
    "weather_precipitation",
    "weather_visibility",
    "verified_report_count",
]

# -------------------------------------------------------
# Default imputation values for missing features
# These are DOCUMENTED ASSUMPTIONS — see docs/limitations.md
# -------------------------------------------------------
FEATURE_DEFAULTS = {
    "crime_density_500m":        0.0,   # No crimes recorded nearby → low density
    "crime_density_1km":         0.0,
    "severity_weighted_density": 0.0,
    "recent_crime_density":      0.0,
    "night_crime_density":       0.0,
    "hour_of_day":               12,    # Midday as neutral default
    "day_of_week":               0,     # Monday as neutral default
    "lighting_score":            0.5,   # ASSUMPTION: moderate lighting when OSM tag absent
    "activity_proxy":            0.5,   # ASSUMPTION: moderate activity when unknown
    "nearest_police_m":          2000.0, # 2km as conservative default
    "nearest_hospital_m":        2000.0,
    "nearest_transport_m":       500.0,
    "weather_precipitation":     0.0,   # No rain as default
    "weather_visibility":        10.0,  # Clear visibility as default
    "verified_report_count":     0,
}


class Preprocessor:
    """
    Converts RiskFeatures → normalized feature array.

    Usage:
        preprocessor = Preprocessor(scaler_path)
        array, completeness = preprocessor.transform(features)
    """

    def __init__(self, scaler_path: Optional[Path] = None):
        self.scaler = None
        if scaler_path and scaler_path.exists():
            self.scaler = joblib.load(scaler_path)
            logger.info("Scaler loaded from %s", scaler_path)
        else:
            logger.warning(
                "No scaler found at %s. Features will not be scaled. "
                "Only acceptable if using a model that does not require scaling.",
                scaler_path
            )

    def transform(self, features: RiskFeatures) -> Tuple[np.ndarray, float, List[str]]:
        """
        Transform features into a model-ready numpy array.

        Returns:
            (feature_array, feature_completeness, imputed_features)
            feature_completeness: fraction of features that were present (not imputed)
            imputed_features: list of feature names imputed with documented defaults
        """
        feature_dict = features.model_dump()
        present_count = sum(1 for v in feature_dict.values() if v is not None)
        completeness = present_count / len(FEATURE_NAMES)

        # Build row in exact feature order, tracking imputed features
        row = []
        imputed_features = []
        for name in FEATURE_NAMES:
            val = feature_dict.get(name)
            if val is None:
                val = FEATURE_DEFAULTS[name]
                imputed_features.append(name)
            row.append(float(val))

        arr = np.array(row, dtype=np.float64).reshape(1, -1)

        if self.scaler is not None:
            arr = self.scaler.transform(arr)

        return arr, completeness, imputed_features

    def transform_batch(self, features_list: list) -> Tuple[np.ndarray, List[float], List[List[str]]]:
        """Transform a list of RiskFeatures into a batch array."""
        arrays = []
        completeness_list = []
        imputed_list = []
        for features in features_list:
            arr, comp, imp = self.transform(features)
            arrays.append(arr)
            completeness_list.append(comp)
            imputed_list.append(imp)
        return np.vstack(arrays), completeness_list, imputed_list
