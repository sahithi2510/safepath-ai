"""SafePath AI - SHAP Explainability Package.

Provides local and global feature attribution, additive verification,
and non-causal explanation generation for road-segment risk models.
"""

from scripts.explainability.schemas import (
    FeatureContribution,
    GlobalExplanation,
    LocalExplanation,
    DISCLAIMER_TEXT,
    SYNTHETIC_WARNING_TEXT,
)
from scripts.explainability.shap_engine import (
    ShapExplainerEngine,
    risk_to_level,
)

__all__ = [
    "FeatureContribution",
    "GlobalExplanation",
    "LocalExplanation",
    "DISCLAIMER_TEXT",
    "SYNTHETIC_WARNING_TEXT",
    "ShapExplainerEngine",
    "risk_to_level",
]
