"""SafePath AI - Model Training Schemas & Configuration.

Defines model training configurations, feature schemas, missing-value defaults,
and evaluation metric reporting models matching ml-service expectations.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Any, Dict, List, Optional

# Must match ml-service FEATURE_NAMES exactly
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

# Imputation defaults matching ml-service FEATURE_DEFAULTS exactly
FEATURE_DEFAULTS: Dict[str, float] = {
    "crime_density_500m": 0.0,
    "crime_density_1km": 0.0,
    "severity_weighted_density": 0.0,
    "recent_crime_density": 0.0,
    "night_crime_density": 0.0,
    "hour_of_day": 12.0,
    "day_of_week": 0.0,
    "lighting_score": 0.5,
    "activity_proxy": 0.5,
    "nearest_police_m": 2000.0,
    "nearest_hospital_m": 2000.0,
    "nearest_transport_m": 500.0,
    "weather_precipitation": 0.0,
    "weather_visibility": 10.0,
    "verified_report_count": 0.0,
}


@dataclass
class TrainingConfig:
    """Hyperparameter and partition configuration for Random Forest training."""

    model_type: str = "random_forest"
    task_type: str = "classifier"  # "classifier" or "regressor"
    n_estimators: int = 100
    max_depth: Optional[int] = 8
    min_samples_split: int = 4
    min_samples_leaf: int = 2
    class_weight: Optional[str] = "balanced"  # Handles class imbalance explicitly
    random_state: int = 42

    # Chronological partition ratios
    train_ratio: float = 0.70
    val_ratio: float = 0.15
    test_ratio: float = 0.15

    # Column mappings
    target_column: str = "risk_label"  # Binary high-risk indicator (0 or 1)
    continuous_target_column: str = "target_risk"  # Continuous risk score [0, 1]
    time_column: str = "snapshot_time"


@dataclass
class XGBoostConfig:
    """Hyperparameter and partition configuration for XGBoost training."""

    model_type: str = "xgboost"
    task_type: str = "classifier"  # "classifier" or "regressor"
    n_estimators: int = 100
    max_depth: int = 4
    learning_rate: float = 0.05
    subsample: float = 0.8
    colsample_bytree: float = 0.8
    scale_pos_weight: Optional[float] = None  # Auto-computed from y_train class imbalance if None
    random_state: int = 42

    # Chronological partition ratios
    train_ratio: float = 0.70
    val_ratio: float = 0.15
    test_ratio: float = 0.15

    # Column mappings
    target_column: str = "risk_label"
    continuous_target_column: str = "target_risk"
    time_column: str = "snapshot_time"


@dataclass
class EvaluationMetrics:
    """Metrics container tracking test performance and dataset provenance."""

    model_type: str
    task_type: str
    version: str
    n_train_samples: int
    n_val_samples: int
    n_test_samples: int
    metrics: Dict[str, Any]
    baseline_metrics: Dict[str, Any]
    feature_importances: Dict[str, float]
    dataset_provenance: str
    is_synthetic_evaluation: bool = False
    notes: str = ""
    timestamp: str = field(
        default_factory=lambda: datetime.now(timezone.utc).isoformat()
    )

    def to_dict(self) -> Dict[str, Any]:
        return {
            "model_type": self.model_type,
            "version": self.version,
            "task_type": self.task_type,
            "trained_at": self.timestamp,
            "training_dataset": self.dataset_provenance,
            "is_synthetic_evaluation": self.is_synthetic_evaluation,
            "records_used": {
                "train": self.n_train_samples,
                "val": self.n_val_samples,
                "test": self.n_test_samples,
                "total": self.n_train_samples + self.n_val_samples + self.n_test_samples,
            },
            "features": FEATURE_NAMES,
            "metrics": self.metrics,
            "baseline_metrics": self.baseline_metrics,
            "feature_importances": self.feature_importances,
            "notes": self.notes,
        }


@dataclass
class ModelComparison:
    """Side-by-side comparison of XGBoost vs Random Forest vs Naive Baseline on identical splits."""

    xgb_metrics: EvaluationMetrics
    rf_metrics: EvaluationMetrics
    baseline_metrics: Dict[str, Any]
    task_type: str
    is_synthetic: bool = True

    def to_dict(self) -> Dict[str, Any]:
        return {
            "task_type": self.task_type,
            "is_synthetic": self.is_synthetic,
            "xgboost": self.xgb_metrics.metrics,
            "random_forest": self.rf_metrics.metrics,
            "naive_baseline": self.baseline_metrics,
            "feature_importances": {
                "xgboost": self.xgb_metrics.feature_importances,
                "random_forest": self.rf_metrics.feature_importances,
            },
        }
