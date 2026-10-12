#!/usr/bin/env python3
"""SafePath AI - XGBoost Training & Evaluation CLI.

Trains and evaluates a reproducible XGBoost model for relative road segment
risk prediction, with anti-leakage guards, class imbalance handling,
and fair comparison against Random Forest and Naive baselines.

Usage Examples:
    # 1. Train and evaluate with comparison on synthetic fixture (dry-run)
    python scripts/train_xgboost.py --compare --dry-run

    # 2. Train and save XGBoost artifacts to models/xgboost/
    python scripts/train_xgboost.py --compare --output-dir models/xgboost

    # 3. Train regression model
    python scripts/train_xgboost.py --task regressor --output-dir models/xgboost
"""

from __future__ import annotations

import argparse
import json
import logging
from pathlib import Path
import sys

# Ensure project root is in sys.path
PROJECT_ROOT = Path(__file__).resolve().parent.parent
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

import pandas as pd

from scripts.model_training.schemas import XGBoostConfig
from scripts.model_training.xgboost_trainer import XGBoostTrainer


def setup_logging(verbose: bool = False):
    level = logging.DEBUG if verbose else logging.INFO
    logging.basicConfig(
        level=level,
        format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
        datefmt="%Y-%m-%d %H:%M:%S",
    )


def print_single_report(metrics_obj, dry_run: bool):
    """Print ASCII report of single XGBoost training evaluation."""
    m = metrics_obj.to_dict()
    border = "=" * 70
    divider = "-" * 70

    print(f"\n{border}")
    print(f"  SafePath AI -- XGBoost Model Evaluation Report")
    print(f"  Model Type: {m['model_type']} ({m['task_type']}) | Version: {m['version']}")
    print(f"  Dataset Provenance: {m['training_dataset']}")
    if m["is_synthetic_evaluation"]:
        print(f"  WARNING: [SYNTHETIC TEST FIXTURE] Metrics verify pipeline, NOT real-world accuracy.")
    print(f"  Mode: {'DRY-RUN (In-memory verification)' if dry_run else 'ARTIFACTS PERSISTED'}")
    print(border)

    print(f"  Dataset Partition (Chronological anti-leakage split):")
    print(f"    - Training Set  : {m['records_used']['train']} records")
    print(f"    - Validation Set: {m['records_used']['val']} records")
    print(f"    - Test Set      : {m['records_used']['test']} records")
    print(divider)

    print(f"  {'Metric':<35} | {'XGBoost':<15} | {'Naive Baseline':<15}")
    print(divider)

    if m["task_type"] == "classifier":
        base_acc = m["baseline_metrics"].get("accuracy", 0.0)
        xgb_acc = m["metrics"].get("accuracy", 0.0)
        print(f"  {'Accuracy':<35} | {xgb_acc:<15.4f} | {base_acc:<15.4f}")
        print(f"  {'Precision':<35} | {m['metrics'].get('precision', 0.0):<15.4f} | {'N/A':<15}")
        print(f"  {'Recall':<35} | {m['metrics'].get('recall', 0.0):<15.4f} | {'N/A':<15}")
        print(f"  {'F1 Score':<35} | {m['metrics'].get('f1_score', 0.0):<15.4f} | {'N/A':<15}")
        print(f"  {'ROC-AUC':<35} | {m['metrics'].get('roc_auc', 0.0):<15.4f} | {'0.5000':<15}")
        print(divider)
        print(f"  Confusion Matrix (Test Set): {m['metrics'].get('confusion_matrix')}")
    else:
        base_mae = m["baseline_metrics"].get("mae", 0.0)
        xgb_mae = m["metrics"].get("mae", 0.0)
        print(f"  {'Mean Absolute Error (MAE)':<35} | {xgb_mae:<15.4f} | {base_mae:<15.4f}")
        print(f"  {'Root Mean Squared Error (RMSE)':<35} | {m['metrics'].get('rmse', 0.0):<15.4f} | {'N/A':<15}")
        print(f"  {'R-squared (R2)':<35} | {m['metrics'].get('r2', 0.0):<15.4f} | {'0.0000':<15}")

    print(divider)
    print("  Top 5 Most Important Features:")
    sorted_feats = sorted(m["feature_importances"].items(), key=lambda x: x[1], reverse=True)
    for feat_name, imp in sorted_feats[:5]:
        print(f"    - {feat_name:<28}: {imp:.4f}")
    print(border + "\n")


def print_comparison_report(comparison_obj, dry_run: bool):
    """Print ASCII comparison report comparing XGBoost, Random Forest, and Naive Baseline."""
    cd = comparison_obj.to_dict()
    border = "=" * 80
    divider = "-" * 80

    print(f"\n{border}")
    print(f"  SafePath AI -- Model Comparison Report (XGBoost vs Random Forest vs Baseline)")
    print(f"  Task Type: {cd['task_type']} | Fixed Seed: 42")
    if cd["is_synthetic"]:
        print(f"  WARNING: [SYNTHETIC TEST FIXTURE] Metrics verify pipeline, NOT real-world accuracy.")
    print(f"  Mode: {'DRY-RUN (In-memory verification)' if dry_run else 'XGBOOST ARTIFACTS PERSISTED'}")
    print(border)

    xgb_m = cd["xgboost"]
    rf_m = cd["random_forest"]
    base_m = cd["naive_baseline"]

    if cd["task_type"] == "classifier":
        print(f"  {'Metric':<25} | {'XGBoost':<15} | {'Random Forest':<15} | {'Naive Baseline':<15}")
        print(divider)
        print(f"  {'Accuracy':<25} | {xgb_m.get('accuracy', 0.0):<15.4f} | {rf_m.get('accuracy', 0.0):<15.4f} | {base_m.get('accuracy', 0.0):<15.4f}")
        print(f"  {'Precision':<25} | {xgb_m.get('precision', 0.0):<15.4f} | {rf_m.get('precision', 0.0):<15.4f} | {'N/A':<15}")
        print(f"  {'Recall':<25} | {xgb_m.get('recall', 0.0):<15.4f} | {rf_m.get('recall', 0.0):<15.4f} | {'N/A':<15}")
        print(f"  {'F1 Score':<25} | {xgb_m.get('f1_score', 0.0):<15.4f} | {rf_m.get('f1_score', 0.0):<15.4f} | {'N/A':<15}")
        print(f"  {'ROC-AUC':<25} | {xgb_m.get('roc_auc', 0.0):<15.4f} | {rf_m.get('roc_auc', 0.0):<15.4f} | {'0.5000':<15}")
        print(divider)
        print(f"  XGBoost Confusion Matrix      : {xgb_m.get('confusion_matrix')}")
        print(f"  Random Forest Confusion Matrix: {rf_m.get('confusion_matrix')}")
    else:
        print(f"  {'Metric':<25} | {'XGBoost':<15} | {'Random Forest':<15} | {'Naive Baseline':<15}")
        print(divider)
        print(f"  {'MAE':<25} | {xgb_m.get('mae', 0.0):<15.4f} | {rf_m.get('mae', 0.0):<15.4f} | {base_m.get('mae', 0.0):<15.4f}")
        print(f"  {'RMSE':<25} | {xgb_m.get('rmse', 0.0):<15.4f} | {rf_m.get('rmse', 0.0):<15.4f} | {'N/A':<15}")
        print(f"  {'R2':<25} | {xgb_m.get('r2', 0.0):<15.4f} | {rf_m.get('r2', 0.0):<15.4f} | {'0.0000':<15}")

    print(divider)
    print("  Top Features by Model:")
    xgb_top = sorted(cd["feature_importances"]["xgboost"].items(), key=lambda x: x[1], reverse=True)[:3]
    rf_top = sorted(cd["feature_importances"]["random_forest"].items(), key=lambda x: x[1], reverse=True)[:3]
    print(f"    - XGBoost Top 3      : {', '.join(f'{k} ({v:.3f})' for k, v in xgb_top)}")
    print(f"    - Random Forest Top 3: {', '.join(f'{k} ({v:.3f})' for k, v in rf_top)}")
    print(border + "\n")


def main():
    parser = argparse.ArgumentParser(
        description="SafePath AI XGBoost Model Training CLI",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument(
        "--data",
        type=str,
        default="scripts/fixtures/mock_training_dataset.csv",
        help="Path to feature training dataset (.csv)",
    )
    parser.add_argument(
        "--output-dir",
        type=str,
        default="models/xgboost",
        help="Directory to save model artifacts (default: models/xgboost)",
    )
    parser.add_argument(
        "--task",
        choices=["classifier", "regressor"],
        default="classifier",
        help="Task type: classifier or regressor (default: classifier)",
    )
    parser.add_argument(
        "--n-estimators",
        type=int,
        default=100,
        help="Number of boosting rounds (default: 100)",
    )
    parser.add_argument(
        "--max-depth",
        type=int,
        default=6,
        help="Maximum tree depth (default: 6)",
    )
    parser.add_argument(
        "--learning-rate",
        type=float,
        default=0.1,
        help="Learning rate (eta, default: 0.1)",
    )
    parser.add_argument(
        "--compare",
        action="store_true",
        help="Run 3-way model comparison (XGBoost vs Random Forest vs Naive Baseline)",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Train and evaluate without saving model artifacts to disk",
    )
    parser.add_argument(
        "-v", "--verbose",
        action="store_true",
        help="Enable verbose debug logging",
    )

    args = parser.parse_args()
    setup_logging(args.verbose)

    data_path = Path(args.data)
    if not data_path.exists():
        print(f"Error: Training dataset not found at {data_path}")
        sys.exit(1)

    df = pd.read_csv(data_path, comment="#")
    is_synthetic = "mock" in data_path.name.lower() or "fixtures" in str(data_path).lower()

    config = XGBoostConfig(
        task_type=args.task,
        n_estimators=args.n_estimators,
        max_depth=args.max_depth,
        learning_rate=args.learning_rate,
    )
    trainer = XGBoostTrainer(config)

    if args.compare:
        comparison = trainer.compare_models(
            df=df,
            dataset_provenance=data_path.name,
            is_synthetic=is_synthetic,
        )
        print_comparison_report(comparison, dry_run=args.dry_run)
        metrics = comparison.xgb_metrics
    else:
        metrics = trainer.train_and_evaluate(
            df=df,
            dataset_provenance=data_path.name,
            is_synthetic=is_synthetic,
        )
        print_single_report(metrics, dry_run=args.dry_run)

    if not args.dry_run:
        saved_paths = trainer.save_artifacts(args.output_dir, metrics)
        print(f"Saved XGBoost model artifacts to {args.output_dir}:")
        for key, p in saved_paths.items():
            print(f"  - {key}: {p}")
        print()


if __name__ == "__main__":
    main()
