"""SafePath AI - SHAP Explainability Schemas.

Data models for SHAP local and global explanations, feature attributions,
additive reconstruction verification, and scientific non-causal disclosures.
"""

from __future__ import annotations

from dataclasses import asdict, dataclass, field
from datetime import datetime, timezone
from typing import Any, Dict, List, Optional


DISCLAIMER_TEXT = (
    "These explanations reflect the machine learning model's statistical feature "
    "attributions, NOT direct physical measurements or real-world causal proofs. "
    "Features identify correlation in training distributions, not real-world causation. "
    "All risk categories are application-defined. See project documentation for limitations."
)

SYNTHETIC_WARNING_TEXT = (
    "[SYNTHETIC TEST DEMONSTRATION - NOT REAL CRIME EXPLANATION] "
    "The underlying model was evaluated on synthetic test fixtures. "
    "These feature contributions demonstrate software pipeline correctness and "
    "explainability mechanics, NOT validated real-world street safety."
)


@dataclass
class FeatureContribution:
    """A single feature's SHAP-based attribution to a prediction."""

    name: str
    value: float  # Normalized/scaled value consumed by model
    raw_value: float  # Raw input value before scaling
    contribution: float  # SHAP value (phi_i)
    direction: str  # "increases_risk", "decreases_risk", or "neutral"
    description: str  # Strictly non-causal associative phrasing
    is_imputed: bool = False

    def to_dict(self) -> Dict[str, Any]:
        return {
            "name": self.name,
            "value": round(float(self.value), 4),
            "raw_value": round(float(self.raw_value), 4),
            "contribution": round(float(self.contribution), 4),
            "direction": self.direction,
            "description": self.description,
            "is_imputed": self.is_imputed,
        }


@dataclass
class LocalExplanation:
    """Complete local explanation for an individual road segment prediction."""

    segment_id: str
    model_type: str  # "xgboost" or "random_forest"
    task_type: str  # "classifier" or "regressor"
    model_version: str
    model_output: float  # Raw model output (margin for XGBoost, probability for RF, continuous for regressor)
    predicted_risk_score: float  # Normalized risk score in [0.0, 1.0]
    safety_score: float  # Mapped score: 1.0 - predicted_risk_score
    risk_level: str  # VERY_LOW, LOW, MODERATE, HIGH, VERY_HIGH
    expected_value: float  # Base value E[f(X)]
    sum_contributions: float  # Sum of all 15 phi_i
    reconstruction_value: float  # expected_value + sum_contributions
    reconstruction_error: float  # |reconstruction_value - model_output|
    reconstruction_valid: bool  # True if reconstruction_error <= tolerance
    top_risk_factors: List[FeatureContribution]
    top_protective_factors: List[FeatureContribution]
    all_contributions: List[FeatureContribution]
    is_synthetic_demonstration: bool = True
    disclaimer: str = DISCLAIMER_TEXT
    causality_warning: str = (
        "SHAP values describe model decision mechanisms, not physical real-world causation. "
        "A feature attribution does not imply that altering the feature directly causes an outcome."
    )
    timestamp: str = field(
        default_factory=lambda: datetime.now(timezone.utc).isoformat()
    )

    def to_dict(self) -> Dict[str, Any]:
        return {
            "segment_id": self.segment_id,
            "model_type": self.model_type,
            "task_type": self.task_type,
            "model_version": self.model_version,
            "model_output": round(float(self.model_output), 4),
            "predicted_risk_score": round(float(self.predicted_risk_score), 4),
            "safety_score": round(float(self.safety_score), 4),
            "risk_level": self.risk_level,
            "expected_value": round(float(self.expected_value), 4),
            "sum_contributions": round(float(self.sum_contributions), 4),
            "reconstruction_value": round(float(self.reconstruction_value), 4),
            "reconstruction_error": round(float(self.reconstruction_error), 6),
            "reconstruction_valid": self.reconstruction_valid,
            "top_risk_factors": [f.to_dict() for f in self.top_risk_factors],
            "top_protective_factors": [f.to_dict() for f in self.top_protective_factors],
            "all_contributions": [f.to_dict() for f in self.all_contributions],
            "is_synthetic_demonstration": self.is_synthetic_demonstration,
            "disclaimer": self.disclaimer,
            "causality_warning": self.causality_warning,
            "timestamp": self.timestamp,
        }


@dataclass
class GlobalExplanation:
    """Global feature importance summary derived from SHAP values across a dataset."""

    model_type: str
    task_type: str
    model_version: str
    n_samples: int
    mean_abs_shap: Dict[str, float]  # {feature_name: mean(|phi_i|)}
    ranked_features: List[Dict[str, Any]]
    is_synthetic_demonstration: bool = True
    disclaimer: str = DISCLAIMER_TEXT
    timestamp: str = field(
        default_factory=lambda: datetime.now(timezone.utc).isoformat()
    )

    def to_dict(self) -> Dict[str, Any]:
        return {
            "model_type": self.model_type,
            "task_type": self.task_type,
            "model_version": self.model_version,
            "n_samples": self.n_samples,
            "mean_abs_shap": {k: round(v, 4) for k, v in self.mean_abs_shap.items()},
            "ranked_features": self.ranked_features,
            "is_synthetic_demonstration": self.is_synthetic_demonstration,
            "disclaimer": self.disclaimer,
            "timestamp": self.timestamp,
        }
