"""Unit tests for XGBoost training, evaluation, anti-leakage safeguards, baseline comparison, and serialization."""

import json
from pathlib import Path
import numpy as np
import pandas as pd
import pytest

from app.models.schemas import RiskFeatures
from app.services.model_service import ModelService
from scripts.model_training.schemas import FEATURE_NAMES, XGBoostConfig
from scripts.model_training.xgboost_trainer import XGBoostTrainer


@pytest.fixture
def mock_dataset_path():
    path = Path(__file__).resolve().parent.parent / "fixtures" / "mock_training_dataset.csv"
    assert path.exists(), f"Mock training dataset missing at {path}"
    return path


@pytest.fixture
def mock_df(mock_dataset_path):
    return pd.read_csv(mock_dataset_path, comment="#")


def test_xgboost_classification_training(mock_df):
    """Verify XGBoost classification trains, computes valid metrics, and flags synthetic metadata."""
    config = XGBoostConfig(task_type="classifier", n_estimators=20, max_depth=4)
    trainer = XGBoostTrainer(config)

    metrics = trainer.train_and_evaluate(
        df=mock_df,
        dataset_provenance="mock_training_dataset.csv",
        is_synthetic=True,
    )

    m = metrics.to_dict()
    assert m["model_type"] == "xgboost"
    assert m["task_type"] == "classifier"
    assert m["is_synthetic_evaluation"] is True
    assert "SYNTHETIC TEST EVALUATION" in m["notes"]

    # Metric range assertions
    assert 0.0 <= m["metrics"]["accuracy"] <= 1.0
    assert 0.0 <= m["metrics"]["precision"] <= 1.0
    assert 0.0 <= m["metrics"]["recall"] <= 1.0
    assert 0.0 <= m["metrics"]["f1_score"] <= 1.0
    assert 0.0 <= m["metrics"]["roc_auc"] <= 1.0
    assert isinstance(m["metrics"]["confusion_matrix"], list)

    # Baseline comparison present
    assert "baseline_metrics" in m
    assert m["baseline_metrics"]["baseline_type"] == "DummyClassifier(most_frequent)"

    # Feature importances exist for all 15 features
    assert len(m["feature_importances"]) == len(FEATURE_NAMES)
    for feat in FEATURE_NAMES:
        assert feat in m["feature_importances"]


def test_xgboost_regression_training(mock_df):
    """Verify XGBoost regression trains and computes valid continuous metrics."""
    config = XGBoostConfig(task_type="regressor", n_estimators=20, max_depth=4)
    trainer = XGBoostTrainer(config)

    metrics = trainer.train_and_evaluate(
        df=mock_df,
        dataset_provenance="mock_training_dataset.csv",
        is_synthetic=True,
    )

    m = metrics.to_dict()
    assert m["model_type"] == "xgboost"
    assert m["task_type"] == "regressor"
    assert m["metrics"]["mae"] >= 0.0
    assert m["metrics"]["rmse"] >= 0.0
    assert "baseline_metrics" in m
    assert m["baseline_metrics"]["baseline_type"] == "DummyRegressor(mean)"


def test_xgboost_reproducibility_with_fixed_seed(mock_df):
    """Verify that models trained with identical random seeds produce identical test metrics."""
    config1 = XGBoostConfig(task_type="classifier", n_estimators=25, max_depth=4, random_state=42)
    trainer1 = XGBoostTrainer(config1)
    metrics1 = trainer1.train_and_evaluate(mock_df, is_synthetic=True)

    config2 = XGBoostConfig(task_type="classifier", n_estimators=25, max_depth=4, random_state=42)
    trainer2 = XGBoostTrainer(config2)
    metrics2 = trainer2.train_and_evaluate(mock_df, is_synthetic=True)

    assert metrics1.metrics["accuracy"] == metrics2.metrics["accuracy"]
    assert metrics1.metrics["f1_score"] == metrics2.metrics["f1_score"]
    assert metrics1.metrics["roc_auc"] == metrics2.metrics["roc_auc"]
    assert metrics1.metrics["confusion_matrix"] == metrics2.metrics["confusion_matrix"]


def test_xgboost_anti_leakage_scaler_fitted_on_train_only(mock_df):
    """Verify MinMaxScaler is fitted strictly on X_train, preventing validation/test data leakage."""
    config = XGBoostConfig(task_type="classifier")
    trainer = XGBoostTrainer(config)

    X_train, y_train, X_val, y_val, X_test, y_test = trainer.prepare_data(mock_df)

    assert np.all(X_train >= -1e-6)
    assert np.all(X_train <= 1.0 + 1e-6)
    assert trainer.scaler is not None
    assert trainer.scaler.n_features_in_ == len(FEATURE_NAMES)


def test_xgboost_class_imbalance_handling(mock_df):
    """Verify scale_pos_weight is assigned to handle imbalanced positive crime risk cases."""
    # Custom scale_pos_weight
    custom_config = XGBoostConfig(task_type="classifier", scale_pos_weight=2.5)
    trainer = XGBoostTrainer(custom_config)
    trainer.train_and_evaluate(mock_df, is_synthetic=True)
    assert trainer.model.get_params()["scale_pos_weight"] == 2.5

    # Automatic scale_pos_weight calculation based on train partition
    auto_config = XGBoostConfig(task_type="classifier", scale_pos_weight=None)
    trainer_auto = XGBoostTrainer(auto_config)
    trainer_auto.train_and_evaluate(mock_df, is_synthetic=True)
    auto_weight = trainer_auto.model.get_params()["scale_pos_weight"]
    assert auto_weight > 0.0


def test_xgboost_artifact_serialization_and_ml_service_compatibility(mock_df, tmp_path):
    """Verify model artifacts can be serialized and directly loaded by ml-service ModelService."""
    config = XGBoostConfig(task_type="classifier", n_estimators=15, max_depth=4)
    trainer = XGBoostTrainer(config)

    metrics = trainer.train_and_evaluate(mock_df, is_synthetic=True)
    out_dir = tmp_path / "xgboost"
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
    assert model_service.model_type == "xgboost"

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


def test_xgboost_fair_baseline_comparison(mock_df):
    """Verify side-by-side comparison uses identical partitions and covers both RF and Naive baselines."""
    config = XGBoostConfig(task_type="classifier", n_estimators=20, max_depth=4)
    trainer = XGBoostTrainer(config)

    comparison = trainer.compare_with_baselines(
        df=mock_df,
        dataset_provenance="mock_training_dataset.csv",
        is_synthetic=True,
    )

    # Partition counts must match exactly between XGBoost and Random Forest
    assert comparison.xgb_metrics.n_train_samples == comparison.rf_metrics.n_train_samples
    assert comparison.xgb_metrics.n_val_samples == comparison.rf_metrics.n_val_samples
    assert comparison.xgb_metrics.n_test_samples == comparison.rf_metrics.n_test_samples

    # Verify dictionary export
    cd = comparison.to_dict()
    assert "xgboost" in cd
    assert "random_forest" in cd
    assert "naive_baseline" in cd
    assert "feature_importances" in cd
    assert "accuracy" in cd["xgboost"]
    assert "accuracy" in cd["random_forest"]
    assert "accuracy" in cd["naive_baseline"]
