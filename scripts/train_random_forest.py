#!/usr/bin/env python3
"""SafePath AI - Random Forest Training CLI.

Trains and evaluates a reproducible Random Forest baseline model for relative
road segment risk prediction, with anti-leakage guards and artifact serialization.

Usage Examples:
    # 1. Train and evaluate on synthetic test fixture (Dry run)
    python scripts/train_random_forest.py --data scripts/fixtures/mock_training_dataset.csv --dry-run

    # 2. Train and save model artifacts to models/random_forest/
    python scripts/train_random_forest.py \\
        --data scripts/fixtures/mock_training_dataset.csv \\
        --output-dir models/random_forest

    # 3. Train regression model
    python scripts/train_random_forest.py --task regressor --output-dir models/random_forest
"""

from __future__ import annotations

import argparse
import json
import logging
import os
from pathlib import Path
import sys

# Ensure project root is in sys.path
PROJECT_ROOT = Path(__file__).resolve().parent.parent
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

import pandas as pd

from scripts.model_training.schemas import TrainingConfig
from scripts.model_training.trainer import RandomForestTrainer


def setup_logging(verbose: bool = False):
    level = logging.DEBUG if verbose else logging.INFO
    logging.basicConfig(
        level=level,
        format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
        datefmt="%Y-%m-%d %H:%M:%S",
    )


def print_evaluation_report(metrics_obj, dry_run: bool):
    """Print clean ASCII report of training evaluation and baseline comparison."""
    m = metrics_obj.to_dict()
    border = "=" * 70
    divider = "-" * 70

    print(f"\n{border}")
    print(f"  SafePath AI -- Random Forest Baseline Evaluation Report")
    print(f"  Model Type: {m['model_type']} ({m['task_type']}) | Version: {m['version']}")
    print(f"  Dataset Provenance: {m['training_dataset']}")
    if m["is_synthetic_evaluation"]:
        print(f"  WARNING: [SYNTHETIC TEST FIXTURE] Metrics verify code execution, NOT real-world accuracy.")
    print(f"  Mode: {'DRY-RUN (In-memory verification)' if dry_run else 'ARTIFACTS PERSISTED'}")
    print(border)

    print(f"  Dataset Partition (Chronological anti-leakage split):")
    print(f"    - Training Set  : {m['records_used']['train']} records")
    print(f"    - Validation Set: {m['records_used']['val']} records")
    print(f"    - Test Set      : {m['records_used']['test']} records")
    print(divider)

    print(f"  {'Metric':<35} | {'Random Forest':<15} | {'Naive Baseline':<15}")
    print(divider)

    if m["task_type"] == "classifier":
        base_acc = m["baseline_metrics"].get("accuracy", 0.0)
        rf_acc = m["metrics"].get("accuracy", 0.0)
        print(f"  {'Accuracy':<35} | {rf_acc:<15.4f} | {base_acc:<15.4f}")
        print(f"  {'Precision':<35} | {m['metrics'].get('precision', 0.0):<15.4f} | {'N/A':<15}")
        print(f"  {'Recall':<35} | {m['metrics'].get('recall', 0.0):<15.4f} | {'N/A':<15}")
        print(f"  {'F1 Score':<35} | {m['metrics'].get('f1_score', 0.0):<15.4f} | {'N/A':<15}")
        print(f"  {'ROC-AUC':<35} | {m['metrics'].get('roc_auc', 0.0):<15.4f} | {'0.5000':<15}")
        print(divider)
        print(f"  Confusion Matrix (Test Set): {m['metrics'].get('confusion_matrix')}")
    else:
        base_mae = m["baseline_metrics"].get("mae", 0.0)
        rf_mae = m["metrics"].get("mae", 0.0)
        print(f"  {'Mean Absolute Error (MAE)':<35} | {rf_mae:<15.4f} | {base_mae:<15.4f}")
        print(f"  {'Root Mean Squared Error (RMSE)':<35} | {m['metrics'].get('rmse', 0.0):<15.4f} | {'N/A':<15}")
        print(f"  {'R-squared (R2)':<35} | {m['metrics'].get('r2', 0.0):<15.4f} | {'0.0000':<15}")

    print(divider)
    print("  Top 5 Most Important Features:")
    sorted_feats = sorted(m["feature_importances"].items(), key=lambda x: x[1], reverse=True)
    for feat_name, imp in sorted_feats[:5]:
        print(f"    - {feat_name:<28}: {imp:.4f}")
    print(border + "\n")


def main():
    parser = argparse.ArgumentParser(
        description="SafePath AI Random Forest Baseline Model Training CLI",
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
        default="models/random_forest",
        help="Directory to save model artifacts (default: models/random_forest)",
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
        help="Number of trees (default: 100)",
    )
    parser.add_argument(
        "--max-depth",
        type=int,
        default=8,
        help="Maximum tree depth (default: 8)",
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

    # Read data ignoring comment headers
    df = pd.read_csv(data_path, comment="#")

    # Detect if dataset is synthetic
    is_synthetic = "mock" in data_path.name.lower() or "fixtures" in str(data_path).lower()

    config = TrainingConfig(
        task_type=args.task,
        n_estimators=args.n_estimators,
        max_depth=args.max_depth,
    )

    trainer = RandomForestTrainer(config)
    metrics = trainer.train_and_evaluate(
        df=df,
        dataset_provenance=data_path.name,
        is_synthetic=is_synthetic,
    )

    print_evaluation_report(metrics, dry_run=args.dry_run)

    if not args.dry_run:
        saved_paths = trainer.save_artifacts(args.output_dir, metrics)
        print(f"Saved model artifacts to {args.output_dir}:")
        for key, p in saved_paths.items():
            print(f"  - {key}: {p}")
        print()


if __name__ == "__main__":
    main()
