"""SafePath AI - Model Training Pipeline.

Provides scikit-learn model training, chronological evaluation,
imbalance handling, anti-leakage preprocessing, and artifact serialization.
"""

from scripts.model_training.schemas import (
    TrainingConfig,
    EvaluationMetrics,
    FEATURE_NAMES,
)
from scripts.model_training.trainer import RandomForestTrainer

__all__ = [
    "TrainingConfig",
    "EvaluationMetrics",
    "FEATURE_NAMES",
    "RandomForestTrainer",
]
