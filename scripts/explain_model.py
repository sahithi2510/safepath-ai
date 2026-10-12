#!/usr/bin/env python3
"""SafePath AI - SHAP Model Explanation CLI.

Generates local and global feature attribution explanations for XGBoost
and Random Forest risk models with mathematical additive reconstruction verification.

Usage Examples:
    # 1. Local explanation for a sample road segment using XGBoost
    python scripts/explain_model.py --model-dir models/xgboost --sample-index 0

    # 2. Local explanation using Random Forest baseline
    python scripts/explain_model.py --model-dir models/random_forest --sample-index 0

    # 3. Global feature attribution summary
    python scripts/explain_model.py --model-dir models/xgboost --global
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

from scripts.explainability.schemas import SYNTHETIC_WARNING_TEXT
from scripts.explainability.shap_engine import ShapExplainerEngine


def setup_logging(verbose: bool = False):
    level = logging.DEBUG if verbose else logging.INFO
    logging.basicConfig(
        level=level,
        format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
        datefmt="%Y-%m-%d %H:%M:%S",
    )


def print_local_explanation(exp):
    """Print clean ASCII report of local SHAP explanation."""
    e = exp.to_dict()
    border = "=" * 78
    divider = "-" * 78

    print(f"\n{border}")
    print(f"  SafePath AI -- SHAP Local Feature Attribution Report")
    print(f"  Segment ID: {e['segment_id']} | Model: {e['model_type']} ({e['task_type']}) v{e['model_version']}")
    if e["is_synthetic_demonstration"]:
        print(f"  WARNING: {SYNTHETIC_WARNING_TEXT}")
    print(border)

    print(f"  Prediction Summary:")
    print(f"    - Model Output (Raw)      : {e['model_output']:.4f}")
    print(f"    - Predicted Risk Score     : {e['predicted_risk_score']:.4f}  [{e['risk_level']}]")
    print(f"    - Mapped Safety Score      : {e['safety_score']:.4f}  (1.0 - Risk Score)")
    print(f"    - Expected Value (Base)    : {e['expected_value']:.4f}")
    print(f"    - Sum of SHAP Values       : {e['sum_contributions']:.4f}")
    print(divider)

    print(f"  Additive Efficiency / Reconstruction Check:")
    print(f"    - Base + Sum(SHAP)         : {e['reconstruction_value']:.4f}")
    print(f"    - Actual Model Output      : {e['model_output']:.4f}")
    print(f"    - Absolute Error           : {e['reconstruction_error']:.6f}")
    status_str = "PASS (Within tolerance <= 1e-4)" if e["reconstruction_valid"] else "FAIL (Exceeds tolerance)"
    print(f"    - Verification Status      : {status_str}")
    print(divider)

    print(f"  Top Risk-Increasing Feature Attributions (SHAP > 0):")
    if not e["top_risk_factors"]:
        print("    (None observed for this segment)")
    for f in e["top_risk_factors"][:5]:
        imputed_tag = " [IMPUTED]" if f["is_imputed"] else ""
        print(f"    [+{f['contribution']:.4f}] {f['name']:<24} (Raw: {f['raw_value']:.2f}){imputed_tag}")
        print(f"            Description: {f['description']}")

    print()
    print(f"  Top Risk-Decreasing Protective Attributions (SHAP < 0):")
    if not e["top_protective_factors"]:
        print("    (None observed for this segment)")
    for f in e["top_protective_factors"][:5]:
        imputed_tag = " [IMPUTED]" if f["is_imputed"] else ""
        print(f"    [{f['contribution']:.4f}] {f['name']:<24} (Raw: {f['raw_value']:.2f}){imputed_tag}")
        print(f"            Description: {f['description']}")

    print(divider)
    print(f"  SCIENTIFIC & CAUSALITY DISCLAIMER:")
    print(f"    {e['disclaimer']}")
    print(f"    {e['causality_warning']}")
    print(border + "\n")


def print_global_explanation(g_exp):
    """Print clean ASCII report of global SHAP feature importance."""
    g = g_exp.to_dict()
    border = "=" * 78
    divider = "-" * 78

    print(f"\n{border}")
    print(f"  SafePath AI -- SHAP Global Feature Importance Report")
    print(f"  Model: {g['model_type']} ({g['task_type']}) v{g['model_version']} | Samples Analyzed: {g['n_samples']}")
    if g["is_synthetic_demonstration"]:
        print(f"  WARNING: {SYNTHETIC_WARNING_TEXT}")
    print(border)

    print(f"  {'Rank':<6} | {'Feature Name':<30} | {'Mean |SHAP Value|':<18}")
    print(divider)
    for item in g["ranked_features"]:
        print(f"  {item['rank']:<6} | {item['feature']:<30} | {item['mean_abs_shap']:<18.4f}")

    print(divider)
    print(f"  SCIENTIFIC DISCLAIMER:")
    print(f"    {g['disclaimer']}")
    print(border + "\n")


def main():
    parser = argparse.ArgumentParser(
        description="SafePath AI SHAP Model Explanation CLI",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument(
        "--model-dir",
        type=str,
        default="models/xgboost",
        help="Path to trained model directory (default: models/xgboost)",
    )
    parser.add_argument(
        "--data",
        type=str,
        default="scripts/fixtures/mock_training_dataset.csv",
        help="Path to feature dataset (.csv) for background or samples",
    )
    parser.add_argument(
        "--segment-id",
        type=str,
        default="SEG_QUERY_001",
        help="Segment ID to tag the local explanation",
    )
    parser.add_argument(
        "--sample-index",
        type=int,
        default=0,
        help="Row index from dataset to explain (default: 0)",
    )
    parser.add_argument(
        "--global",
        dest="show_global",
        action="store_true",
        help="Compute and display global feature importance summary",
    )
    parser.add_argument(
        "--features-json",
        type=str,
        default=None,
        help="Optional JSON string of feature key-value pairs",
    )
    parser.add_argument(
        "-v", "--verbose",
        action="store_true",
        help="Enable verbose debug logging",
    )

    args = parser.parse_args()
    setup_logging(args.verbose)

    model_dir = Path(args.model_dir)
    if not model_dir.exists():
        print(f"Error: Model directory not found at {model_dir}")
        sys.exit(1)

    explainer = ShapExplainerEngine(model_dir=model_dir)

    data_path = Path(args.data)
    df = pd.read_csv(data_path, comment="#") if data_path.exists() else pd.DataFrame()

    if args.show_global:
        if df.empty:
            print("Error: Dataset required for global explanation.")
            sys.exit(1)
        g_exp = explainer.explain_global(df=df, n_samples=min(100, len(df)))
        print_global_explanation(g_exp)
    else:
        if args.features_json:
            features = json.loads(args.features_json)
        elif not df.empty and args.sample_index < len(df):
            features = df.iloc[args.sample_index].to_dict()
        else:
            features = {}

        local_exp = explainer.explain_local(
            features=features,
            segment_id=args.segment_id,
        )
        print_local_explanation(local_exp)


if __name__ == "__main__":
    main()
