"""SafePath AI - XGBoost Model Training and Evaluation Pipeline.

Implements XGBoost training for road-segment risk prediction with:
- Strict chronological dataset partitioning (anti-leakage).
- Preprocessing (MinMaxScaler) fitted strictly on training partition only.
- Class imbalance handling via scale_pos_weight.
- Direct side-by-side comparison against Random Forest and Naive baselines.
- Production artifact serialization compatible with ml-service.
"""

from __future__ import annotations

import json
import logging
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple, Union

import joblib
import numpy as np
import pandas as pd
from sklearn.dummy import DummyClassifier, DummyRegressor
from sklearn.metrics import (
    accuracy_score,
    confusion_matrix,
    f1_score,
    mean_absolute_error,
    mean_squared_error,
    precision_score,
    r2_score,
    recall_score,
    roc_auc_score,
)
from sklearn.preprocessing import MinMaxScaler
from xgboost import XGBClassifier, XGBRegressor

from scripts.feature_pipeline.leakage_guard import TemporalBoundaryManager
from scripts.model_training.schemas import (
    FEATURE_DEFAULTS,
    FEATURE_NAMES,
    EvaluationMetrics,
    ModelComparison,
    TrainingConfig,
    XGBoostConfig,
)
from scripts.model_training.trainer import RandomForestTrainer

logger = logging.getLogger(__name__)


class XGBoostTrainer:
    """Manages training, evaluation, comparison, and serialization of XGBoost models."""

    def __init__(self, config: Optional[XGBoostConfig] = None):
        self.config = config or XGBoostConfig()
        self.scaler: Optional[MinMaxScaler] = None
        self.model: Optional[Union[XGBClassifier, XGBRegressor]] = None

    def prepare_data(
        self,
        df: pd.DataFrame,
        is_synthetic: bool = False,
    ) -> Tuple[
        np.ndarray, np.ndarray, np.ndarray,
        np.ndarray, np.ndarray, np.ndarray,
    ]:
        """Prepare feature matrices and target arrays with anti-leakage boundaries.

        Returns:
            (X_train, y_train, X_val, y_val, X_test, y_test)
        """
        if df.empty:
            raise ValueError("Input DataFrame is empty.")

        target_col = (
            self.config.target_column
            if self.config.task_type == "classifier"
            else self.config.continuous_target_column
        )

        if target_col not in df.columns:
            raise KeyError(f"Target column '{target_col}' not found in DataFrame.")

        # 1. Chronological partition to prevent temporal leakage
        train_df, val_df, test_df = TemporalBoundaryManager.chronological_split(
            df=df,
            time_col=self.config.time_column,
            train_frac=self.config.train_ratio,
            val_frac=self.config.val_ratio,
        )

        if len(train_df) == 0 or len(test_df) == 0:
            raise ValueError("Insufficient rows for train/test partitioning.")

        # 2. Extract feature arrays with documented imputation
        X_train_raw = self._extract_features_array(train_df)
        X_val_raw = self._extract_features_array(val_df)
        X_test_raw = self._extract_features_array(test_df)

        y_train = train_df[target_col].values.astype(float if self.config.task_type == "regressor" else int)
        y_val = val_df[target_col].values.astype(float if self.config.task_type == "regressor" else int)
        y_test = test_df[target_col].values.astype(float if self.config.task_type == "regressor" else int)

        # 3. Fit scaler ONLY on X_train (strict anti-leakage rule)
        self.scaler = MinMaxScaler()
        X_train = self.scaler.fit_transform(X_train_raw)
        X_val = self.scaler.transform(X_val_raw)
        X_test = self.scaler.transform(X_test_raw)

        return X_train, y_train, X_val, y_val, X_test, y_test

    def train_and_evaluate(
        self,
        df: pd.DataFrame,
        dataset_provenance: str = "synthetic_fixture",
        is_synthetic: bool = True,
    ) -> EvaluationMetrics:
        """Run XGBoost training and evaluation pipeline."""
        X_train, y_train, X_val, y_val, X_test, y_test = self.prepare_data(
            df=df, is_synthetic=is_synthetic
        )

        logger.info(
            "Training XGBoost (%s) on %d samples (val=%d, test=%d)",
            self.config.task_type,
            len(X_train),
            len(X_val),
            len(X_test),
        )

        if self.config.task_type == "classifier":
            return self._train_classifier(
                X_train, y_train, X_val, y_val, X_test, y_test,
                dataset_provenance=dataset_provenance,
                is_synthetic=is_synthetic,
            )
        else:
            return self._train_regressor(
                X_train, y_train, X_val, y_val, X_test, y_test,
                dataset_provenance=dataset_provenance,
                is_synthetic=is_synthetic,
            )

    def compare_with_baselines(
        self,
        df: pd.DataFrame,
        dataset_provenance: str = "synthetic_fixture",
        is_synthetic: bool = True,
    ) -> ModelComparison:
        """Fair, side-by-side comparison of XGBoost, Random Forest, and Naive Baseline.

        Uses the exact same data partitions and target definitions.
        """
        # 1. Train & evaluate XGBoost
        xgb_metrics = self.train_and_evaluate(
            df=df,
            dataset_provenance=dataset_provenance,
            is_synthetic=is_synthetic,
        )

        # 2. Train & evaluate Random Forest on exact same task
        rf_config = TrainingConfig(
            task_type=self.config.task_type,
            random_state=self.config.random_state,
            train_ratio=self.config.train_ratio,
            val_ratio=self.config.val_ratio,
            test_ratio=self.config.test_ratio,
            target_column=self.config.target_column,
            continuous_target_column=self.config.continuous_target_column,
            time_column=self.config.time_column,
        )
        rf_trainer = RandomForestTrainer(rf_config)
        rf_metrics = rf_trainer.train_and_evaluate(
            df=df,
            dataset_provenance=dataset_provenance,
            is_synthetic=is_synthetic,
        )

        return ModelComparison(
            xgb_metrics=xgb_metrics,
            rf_metrics=rf_metrics,
            baseline_metrics=xgb_metrics.baseline_metrics,
            task_type=self.config.task_type,
            is_synthetic=is_synthetic,
        )

    # Alias for convenience
    compare_models = compare_with_baselines


    def _train_classifier(
        self,
        X_train: np.ndarray, y_train: np.ndarray,
        X_val: np.ndarray, y_val: np.ndarray,
        X_test: np.ndarray, y_test: np.ndarray,
        dataset_provenance: str,
        is_synthetic: bool,
    ) -> EvaluationMetrics:
        # Calculate class imbalance scale_pos_weight
        n_pos = int(np.sum(y_train == 1))
        n_neg = int(np.sum(y_train == 0))
        scale_pos = (
            self.config.scale_pos_weight
            if self.config.scale_pos_weight is not None
            else (float(n_neg / n_pos) if n_pos > 0 else 1.0)
        )

        self.model = XGBClassifier(
            n_estimators=self.config.n_estimators,
            max_depth=self.config.max_depth,
            learning_rate=self.config.learning_rate,
            subsample=self.config.subsample,
            colsample_bytree=self.config.colsample_bytree,
            scale_pos_weight=scale_pos,
            random_state=self.config.random_state,
            eval_metric="logloss",
            n_jobs=-1,
        )
        self.model.fit(
            X_train, y_train,
            eval_set=[(X_val, y_val)],
            verbose=False,
        )

        # Baseline: Dummy majority class predictor
        dummy = DummyClassifier(strategy="most_frequent")
        dummy.fit(X_train, y_train)
        dummy_preds = dummy.predict(X_test)
        baseline_acc = float(accuracy_score(y_test, dummy_preds))

        # Model evaluation on test set
        test_preds = self.model.predict(X_test)
        has_two_classes = len(np.unique(y_test)) > 1

        if has_two_classes:
            test_probs = self.model.predict_proba(X_test)[:, 1]
            try:
                roc_auc = float(roc_auc_score(y_test, test_probs))
            except Exception:
                roc_auc = 0.5
        else:
            roc_auc = 0.5

        metrics = {
            "accuracy": round(float(accuracy_score(y_test, test_preds)), 4),
            "precision": round(float(precision_score(y_test, test_preds, zero_division=0)), 4),
            "recall": round(float(recall_score(y_test, test_preds, zero_division=0)), 4),
            "f1_score": round(float(f1_score(y_test, test_preds, zero_division=0)), 4),
            "roc_auc": round(roc_auc, 4),
            "confusion_matrix": confusion_matrix(y_test, test_preds).tolist(),
        }

        baseline_metrics = {
            "baseline_type": "DummyClassifier(most_frequent)",
            "accuracy": round(baseline_acc, 4),
        }

        importances = dict(
            zip(FEATURE_NAMES, [round(float(v), 4) for v in self.model.feature_importances_])
        )

        note_prefix = "[SYNTHETIC TEST EVALUATION - NOT REAL CRIME ACCURACY] " if is_synthetic else "[REAL DATA EVALUATION] "

        return EvaluationMetrics(
            model_type="xgboost",
            task_type="classifier",
            version="1.0.0",
            n_train_samples=len(X_train),
            n_val_samples=len(X_val),
            n_test_samples=len(X_test),
            metrics=metrics,
            baseline_metrics=baseline_metrics,
            feature_importances=importances,
            dataset_provenance=dataset_provenance,
            is_synthetic_evaluation=is_synthetic,
            notes=f"{note_prefix}XGBoost classifier with scale_pos_weight={scale_pos:.2f} for class imbalance.",
        )

    def _train_regressor(
        self,
        X_train: np.ndarray, y_train: np.ndarray,
        X_val: np.ndarray, y_val: np.ndarray,
        X_test: np.ndarray, y_test: np.ndarray,
        dataset_provenance: str,
        is_synthetic: bool,
    ) -> EvaluationMetrics:
        self.model = XGBRegressor(
            n_estimators=self.config.n_estimators,
            max_depth=self.config.max_depth,
            learning_rate=self.config.learning_rate,
            subsample=self.config.subsample,
            colsample_bytree=self.config.colsample_bytree,
            random_state=self.config.random_state,
            eval_metric="rmse",
            n_jobs=-1,
        )
        self.model.fit(
            X_train, y_train,
            eval_set=[(X_val, y_val)],
            verbose=False,
        )

        dummy = DummyRegressor(strategy="mean")
        dummy.fit(X_train, y_train)
        dummy_preds = dummy.predict(X_test)
        baseline_mae = float(mean_absolute_error(y_test, dummy_preds))

        test_preds = self.model.predict(X_test)
        mae = float(mean_absolute_error(y_test, test_preds))
        mse = float(mean_squared_error(y_test, test_preds))
        r2 = float(r2_score(y_test, test_preds))

        metrics = {
            "mae": round(mae, 4),
            "rmse": round(float(np.sqrt(mse)), 4),
            "r2": round(r2, 4),
        }

        baseline_metrics = {
            "baseline_type": "DummyRegressor(mean)",
            "mae": round(baseline_mae, 4),
        }

        importances = dict(
            zip(FEATURE_NAMES, [round(float(v), 4) for v in self.model.feature_importances_])
        )

        note_prefix = "[SYNTHETIC TEST EVALUATION - NOT REAL CRIME ACCURACY] " if is_synthetic else "[REAL DATA EVALUATION] "

        return EvaluationMetrics(
            model_type="xgboost",
            task_type="regressor",
            version="1.0.0",
            n_train_samples=len(X_train),
            n_val_samples=len(X_val),
            n_test_samples=len(X_test),
            metrics=metrics,
            baseline_metrics=baseline_metrics,
            feature_importances=importances,
            dataset_provenance=dataset_provenance,
            is_synthetic_evaluation=is_synthetic,
            notes=f"{note_prefix}XGBoost regression model.",
        )

    def save_artifacts(
        self,
        output_dir: Union[str, Path],
        metrics: EvaluationMetrics,
    ) -> Dict[str, Path]:
        """Save production XGBoost model artifacts matching ml-service requirements."""
        out = Path(output_dir)
        out.mkdir(parents=True, exist_ok=True)

        model_path = out / "model.joblib"
        scaler_path = out / "scaler.joblib"
        feature_path = out / "feature_names.json"
        metadata_path = out / "metadata.json"

        # 1. Model binary
        joblib.dump(self.model, model_path)

        # 2. Scaler binary
        joblib.dump(self.scaler, scaler_path)

        # 3. Feature names JSON
        with open(feature_path, "w", encoding="utf-8") as f:
            json.dump(FEATURE_NAMES, f, indent=2)

        # 4. Metadata JSON
        with open(metadata_path, "w", encoding="utf-8") as f:
            json.dump(metrics.to_dict(), f, indent=2)

        logger.info("Saved XGBoost artifacts to %s", out)
        return {
            "model": model_path,
            "scaler": scaler_path,
            "features": feature_path,
            "metadata": metadata_path,
        }

    @staticmethod
    def _extract_features_array(df: pd.DataFrame) -> np.ndarray:
        """Extract features in exact FEATURE_NAMES order, imputing missing values."""
        rows = []
        for _, row in df.iterrows():
            feat_vals = []
            for col in FEATURE_NAMES:
                val = row.get(col)
                if pd.isna(val) or val is None:
                    feat_vals.append(FEATURE_DEFAULTS[col])
                else:
                    feat_vals.append(float(val))
            rows.append(feat_vals)
        return np.array(rows, dtype=np.float64)
