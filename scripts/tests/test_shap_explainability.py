"""Unit tests for SHAP Explainability Engine.

Verifies:
- TreeExplainer initialization for XGBoost and Random Forest.
- Local feature attributions and exact additive reconstruction checks.
- Alignment with exact 15 FEATURE_NAMES and missing-value imputation.
- Reproducibility with deterministic inputs.
- Safe handling of out-of-range and missing inputs.
- Non-causal associative phrasing and synthetic demonstration flagging.
- Global feature importance ranking.
- Integration and schema compatibility with ml-service ModelService.
"""

from pathlib import Path
import pytest
import numpy as np
import pandas as pd

from app.models.schemas import RiskFeatures, ExplainResponse
from app.services.model_service import ModelService
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
from scripts.model_training.schemas import FEATURE_NAMES, FEATURE_DEFAULTS


@pytest.fixture
def mock_dataset():
    path = Path("scripts/fixtures/mock_training_dataset.csv")
    assert path.exists(), f"Fixture missing at {path}"
    return pd.read_csv(path, comment="#")


@pytest.fixture
def sample_features():
    return {
        "crime_density_500m": 0.85,
        "crime_density_1km": 1.45,
        "severity_weighted_density": 4.20,
        "recent_crime_density": 0.60,
        "night_crime_density": 0.35,
        "hour_of_day": 22.0,
        "day_of_week": 5.0,
        "lighting_score": 0.20,
        "activity_proxy": 0.30,
        "nearest_police_m": 1500.0,
        "nearest_hospital_m": 2200.0,
        "nearest_transport_m": 800.0,
        "weather_precipitation": 0.0,
        "weather_visibility": 9.5,
        "verified_report_count": 2.0,
    }


def test_shap_engine_initialization_xgboost():
    """Verify engine initializes with trained XGBoost artifacts."""
    engine = ShapExplainerEngine(model_dir="models/xgboost")
    assert engine.model is not None
    assert engine.scaler is not None
    assert engine.model_type == "xgboost"
    assert engine.task_type == "classifier"
    assert len(engine.feature_names) == 15
    assert engine.is_synthetic is True


def test_shap_engine_initialization_random_forest():
    """Verify engine initializes with trained Random Forest artifacts."""
    engine = ShapExplainerEngine(model_dir="models/random_forest")
    assert engine.model is not None
    assert engine.scaler is not None
    assert engine.model_type == "random_forest"
    assert engine.task_type == "classifier"
    assert len(engine.feature_names) == 15


def test_local_explanation_additive_reconstruction_xgboost(sample_features):
    """Verify that expected_value + sum(shap_values) == model_output for XGBoost."""
    engine = ShapExplainerEngine(model_dir="models/xgboost")
    local_exp = engine.explain_local(sample_features, segment_id="SEG_XGB_TEST")

    # Additive reconstruction check
    assert local_exp.reconstruction_valid is True
    assert local_exp.reconstruction_error <= 1e-4
    assert local_exp.reconstruction_value == pytest.approx(local_exp.model_output, abs=1e-4)

    # Risk score and safety score range and mapping
    assert 0.0 <= local_exp.predicted_risk_score <= 1.0
    assert 0.0 <= local_exp.safety_score <= 1.0
    assert local_exp.safety_score == pytest.approx(1.0 - local_exp.predicted_risk_score, abs=1e-4)
    assert local_exp.risk_level in ("VERY_LOW", "LOW", "MODERATE", "HIGH", "VERY_HIGH")


def test_local_explanation_additive_reconstruction_random_forest(sample_features):
    """Verify that expected_value + sum(shap_values) == model_output for Random Forest."""
    engine = ShapExplainerEngine(model_dir="models/random_forest")
    local_exp = engine.explain_local(sample_features, segment_id="SEG_RF_TEST")

    assert local_exp.reconstruction_valid is True
    assert local_exp.reconstruction_error <= 1e-4
    assert local_exp.reconstruction_value == pytest.approx(local_exp.model_output, abs=1e-4)
    assert local_exp.safety_score == pytest.approx(1.0 - local_exp.predicted_risk_score, abs=1e-4)


def test_correct_feature_names_and_ordering(sample_features):
    """Verify all 15 features are present in exact order."""
    engine = ShapExplainerEngine(model_dir="models/xgboost")
    local_exp = engine.explain_local(sample_features)

    assert len(local_exp.all_contributions) == 15
    for idx, feat_name in enumerate(FEATURE_NAMES):
        assert local_exp.all_contributions[idx].name == feat_name


def test_reproducibility_with_fixed_input(sample_features):
    """Verify deterministic explanations for identical inputs."""
    engine = ShapExplainerEngine(model_dir="models/xgboost")

    exp1 = engine.explain_local(sample_features)
    exp2 = engine.explain_local(sample_features)

    assert exp1.model_output == pytest.approx(exp2.model_output, abs=1e-7)
    assert exp1.predicted_risk_score == pytest.approx(exp2.predicted_risk_score, abs=1e-7)
    assert exp1.sum_contributions == pytest.approx(exp2.sum_contributions, abs=1e-7)

    for c1, c2 in zip(exp1.all_contributions, exp2.all_contributions):
        assert c1.contribution == pytest.approx(c2.contribution, abs=1e-7)


def test_missing_and_imputed_inputs():
    """Verify missing inputs are safely imputed with FEATURE_DEFAULTS and marked."""
    engine = ShapExplainerEngine(model_dir="models/xgboost")

    # Pass completely empty feature set
    local_exp = engine.explain_local({}, segment_id="SEG_EMPTY")

    assert local_exp.reconstruction_valid is True
    for c in local_exp.all_contributions:
        assert c.is_imputed is True
        assert c.raw_value == pytest.approx(FEATURE_DEFAULTS[c.name], abs=1e-5)


def test_out_of_range_inputs():
    """Verify out-of-range values are sanitized without crashing."""
    engine = ShapExplainerEngine(model_dir="models/xgboost")

    malformed = {
        "lighting_score": 9.9,  # Should clamp to 1.0
        "hour_of_day": 99.0,    # Should clamp to 23.0
        "nearest_police_m": -500.0,  # Should clamp to 0.0
    }
    local_exp = engine.explain_local(malformed)
    assert local_exp.reconstruction_valid is True

    # Find lighting_score
    lighting_contrib = next(c for c in local_exp.all_contributions if c.name == "lighting_score")
    assert lighting_contrib.raw_value == 1.0

    hour_contrib = next(c for c in local_exp.all_contributions if c.name == "hour_of_day")
    assert hour_contrib.raw_value == 23.0


def test_synthetic_warning_propagation_and_non_causal_disclaimer(sample_features):
    """Verify synthetic provenance flag and non-causal language."""
    engine = ShapExplainerEngine(model_dir="models/xgboost")
    local_exp = engine.explain_local(sample_features)

    assert local_exp.is_synthetic_demonstration is True
    assert "statistical feature attributions" in local_exp.disclaimer
    assert "not physical real-world causation" in local_exp.causality_warning

    # Check descriptions: must NOT claim causality
    for c in local_exp.all_contributions:
        assert "caused" not in c.description.lower()
        assert "associate" in c.description.lower() or "correlate" in c.description.lower()


def test_global_explanation(mock_dataset):
    """Verify global feature ranking aggregates absolute SHAP values."""
    engine = ShapExplainerEngine(model_dir="models/xgboost")
    g_exp = engine.explain_global(mock_dataset, n_samples=25)

    assert isinstance(g_exp, GlobalExplanation)
    assert g_exp.n_samples == 25
    assert len(g_exp.ranked_features) == 15
    assert g_exp.is_synthetic_demonstration is True

    # Check ranks are ordered descending
    for i in range(len(g_exp.ranked_features) - 1):
        assert g_exp.ranked_features[i]["mean_abs_shap"] >= g_exp.ranked_features[i + 1]["mean_abs_shap"]


def test_ml_service_model_service_compatibility():
    """Verify ml-service ModelService produces valid ExplainResponse matching schema."""
    ms = ModelService("models")
    ms.load()
    assert ms.is_ready() is True
    assert ms.is_explainer_ready() is True

    features = RiskFeatures(
        crime_density_500m=0.7,
        crime_density_1km=1.2,
        lighting_score=0.4,
    )

    exp_response = ms.explain("SEG_INTEG_001", features)
    assert isinstance(exp_response, ExplainResponse)
    assert exp_response.segment_id == "SEG_INTEG_001"
    assert 0.0 <= exp_response.risk_score <= 1.0
    assert 0.0 <= exp_response.safety_score <= 1.0
    assert exp_response.safety_score == pytest.approx(1.0 - exp_response.risk_score, abs=1e-4)
    assert exp_response.reconstruction_valid is True
    assert exp_response.is_synthetic_demonstration is True
    assert len(exp_response.factors) > 0
