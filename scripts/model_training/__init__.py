"""SafePath AI - Model Training Pipeline.

Provides scikit-learn model training, chronological evaluation,
imbalance handling, anti-leakage preprocessing, and artifact serialization.
"""

from scripts.model_training.schemas import (
    TrainingConfig,
    XGBoostConfig,
    EvaluationMetrics,
    ModelComparison,
    FEATURE_NAMES,
)
from scripts.model_training.trainer import RandomForestTrainer
from scripts.model_training.xgboost_trainer import XGBoostTrainer

__all__ = [
    "TrainingConfig",
    "XGBoostConfig",
    "EvaluationMetrics",
    "ModelComparison",
    "FEATURE_NAMES",
    "RandomForestTrainer",
    "XGBoostTrainer",
]

