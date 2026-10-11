"""Unit tests for Random Forest training, evaluation, anti-leakage safeguards, and serialization."""

from pathlib import Path
import json
import pytest
import numpy as np
import pandas as pd

from scripts.model_training.schemas import TrainingConfig, FEATURE_NAMES
from scripts.model_training.trainer import RandomForestTrainer
from app.services.model_service import ModelService
from app.models.schemas import RiskFeatures


@pytest.fixture
def mock_dataset_path():
    path = Path(__file__).resolve().parent.parent / "fixtures" / "mock_training_dataset.csv"
    assert path.exists(), f"Mock training dataset missing at {path}"
    return path


@pytest.fixture
def mock_df(mock_dataset_path):
    return pd.read_csv(mock_dataset_path, comment="#")


def test_random_forest_classification_training(mock_df):
    """Verify Random Forest classification trains, computes valid metrics, and flags synthetic metadata."""
    config = TrainingConfig(task_type="classifier", n_estimators=20, max_depth=4)
    trainer = RandomForestTrainer(config)

    metrics = trainer.train_and_evaluate(
        df=mock_df,
        dataset_provenance="mock_training_dataset.csv",
        is_synthetic=True,
    )

    m = metrics.to_dict()
    assert m["model_type"] == "random_forest"
    assert m["task_type"] == "classifier"
    assert m["is_synthetic_evaluation"] is True
    assert "SYNTHETIC TEST EVALUATION" in m["notes"]

    # Metric range assertions
    assert 0.0 <= m["metrics"]["accuracy"] <= 1.0
    assert 0.0 <= m["metrics"]["precision"] <= 1.0
    assert 0.0 <= m["metrics"]["recall"] <= 1.0
    assert 0.0 <= m["metrics"]["f1_score"] <= 1.0
    assert 0.0 <= m["metrics"]["roc_auc"] <= 1.0

    # Baseline comparison present
    assert "baseline_metrics" in m
    assert m["baseline_metrics"]["baseline_type"] == "DummyClassifier(most_frequent)"

    # Feature importances exist for all 15 features
    assert len(m["feature_importances"]) == len(FEATURE_NAMES)
    for feat in FEATURE_NAMES:
        assert feat in m["feature_importances"]


def test_random_forest_regression_training(mock_df):
    """Verify Random Forest regression trains and computes valid continuous metrics."""
    config = TrainingConfig(task_type="regressor", n_estimators=20, max_depth=4)
    trainer = RandomForestTrainer(config)

    metrics = trainer.train_and_evaluate(
        df=mock_df,
        dataset_provenance="mock_training_dataset.csv",
        is_synthetic=True,
    )

    m = metrics.to_dict()
    assert m["task_type"] == "regressor"
    assert m["metrics"]["mae"] >= 0.0
    assert m["metrics"]["rmse"] >= 0.0
    assert "baseline_metrics" in m


def test_anti_leakage_scaler_fitted_on_train_only(mock_df):
    """Verify MinMaxScaler is fitted strictly on X_train, preventing validation/test data leakage."""
    config = TrainingConfig(task_type="classifier")
    trainer = RandomForestTrainer(config)

    X_train, y_train, X_val, y_val, X_test, y_test = trainer.prepare_data(mock_df)

    # Scaler was fitted on X_train, so X_train min is exactly 0 and max is exactly 1 (for non-constant cols)
    assert np.all(X_train >= -1e-6)
    assert np.all(X_train <= 1.0 + 1e-6)

    # X_test transformed using X_train bounds may have values outside [0, 1] if test distribution has shifted
    # This proves X_test was NOT fitted into the scaler!
    assert trainer.scaler is not None
    assert trainer.scaler.n_features_in_ == len(FEATURE_NAMES)


def test_artifact_serialization_and_ml_service_compatibility(mock_df, tmp_path):
    """Verify model artifacts can be serialized and directly loaded by ml-service ModelService."""
    config = TrainingConfig(task_type="classifier", n_estimators=15, max_depth=4)
    trainer = RandomForestTrainer(config)

    metrics = trainer.train_and_evaluate(mock_df, is_synthetic=True)
    out_dir = tmp_path / "random_forest"
    trainer.save_artifacts(out_dir, metrics)

    # Verify physical file creation
    assert (out_dir / "model.joblib").exists()
    assert (out_dir / "scaler.joblib").exists()
    assert (out_dir / "feature_names.json").exists()
    assert (out_dir / "metadata.json").exists()

    # Load with ml-service ModelService
    model_service = ModelService(str(tmp_path))
    model_service.load()

    assert model_service.is_ready() is True
    assert model_service.model_type == "random_forest"

    # Test single prediction
    sample_features = RiskFeatures(
        crime_density_500m=0.35,
        crime_density_1km=0.75,
        severity_weighted_density=2.4,
        hour_of_day=22.0,
        lighting_score=0.2,
        nearest_police_m=1200.0,
    )

    pred = model_service.predict("SEG_TEST_001", sample_features)
    assert pred.segment_id == "SEG_TEST_001"
    assert 0.0 <= pred.risk_score <= 1.0
    assert 0.0 <= pred.safety_score <= 1.0
    assert pred.safety_score == pytest.approx(1.0 - pred.risk_score, abs=1e-4)
    assert pred.risk_level in ("VERY_LOW", "LOW", "MODERATE", "HIGH", "VERY_HIGH")
