#!/usr/bin/env python3
"""SafePath AI - Spatial/Temporal Feature Generation CLI.

Derives model-ready spatial and temporal features for road segments
from crime incidents and safe zone datasets.

Usage Examples:
    # 1. Dry run on synthetic test fixtures
    python scripts/generate_features.py \\
        --crimes-file scripts/fixtures/mock_crime_records.csv \\
        --segments-file scripts/fixtures/mock_road_segments.csv \\
        --safe-zones-file scripts/fixtures/mock_safe_zones.csv \\
        --dry-run

    # 2. Generate and export feature dataset to CSV
    python scripts/generate_features.py \\
        --crimes-file datasets/processed/cleaned_crimes.csv \\
        --segments-file datasets/raw/road_segments.csv \\
        --output datasets/processed/segment_features.csv
"""

from __future__ import annotations

import argparse
import logging
import os
from pathlib import Path
import sys

# Ensure project root is in sys.path
PROJECT_ROOT = Path(__file__).resolve().parent.parent
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

import pandas as pd

from scripts.feature_pipeline.pipeline import FeaturePipeline
from scripts.feature_pipeline.schemas import FEATURE_NAMES, FeaturePipelineConfig


def setup_logging(verbose: bool = False):
    level = logging.DEBUG if verbose else logging.INFO
    logging.basicConfig(
        level=level,
        format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
        datefmt="%Y-%m-%d %H:%M:%S",
    )


def load_dataset(file_path: Path) -> pd.DataFrame:
    """Load CSV dataset ignoring comment lines."""
    for encoding in ("utf-8-sig", "utf-8", "latin-1"):
        try:
            return pd.read_csv(file_path, comment="#", encoding=encoding)
        except UnicodeDecodeError:
            continue
    return pd.read_csv(file_path, comment="#", errors="replace")


def print_feature_summary(df: pd.DataFrame, eval_time: str, dry_run: bool):
    """Print clean ASCII summary of generated features."""
    border = "=" * 70
    divider = "-" * 70

    print(f"\n{border}")
    print(f"  SafePath AI -- Spatial/Temporal Feature Generation Report")
    print(f"  Evaluation Timestamp: {eval_time}")
    print(f"  Execution Mode: {'DRY-RUN (In-memory verification)' if dry_run else 'PERSISTENCE'}")
    print(border)
    print(f"  {'Metric':<40} | {'Value':<25}")
    print(divider)
    print(f"  {'Total Road Segments Processed':<40} | {len(df):<25}")
    print(f"  {'Features Generated per Segment':<40} | {len(FEATURE_NAMES):<25}")

    mean_comp = df["feature_completeness"].mean() if "feature_completeness" in df.columns else 1.0
    print(f"  {'Average Feature Completeness':<40} | {mean_comp * 100:.1f}%")

    if "crime_density_500m" in df.columns:
        active_segments = (df["crime_density_500m"] > 0).sum()
        print(f"  {'Segments with Crime Density > 0':<40} | {active_segments:<25}")

    print(divider)
    print("  Feature Value Ranges across Processed Segments:")
    for col in FEATURE_NAMES:
        if col in df.columns:
            s = pd.to_numeric(df[col], errors="coerce")
            print(f"    - {col:<26}: min={s.min():.2f}, mean={s.mean():.2f}, max={s.max():.2f}")
    print(border + "\n")


def main():
    parser = argparse.ArgumentParser(
        description="SafePath AI Spatial & Temporal Feature Generation CLI",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument(
        "--crimes-file",
        type=str,
        default="scripts/fixtures/mock_crime_records.csv",
        help="Path to crime incidents file (.csv)",
    )
    parser.add_argument(
        "--segments-file",
        type=str,
        default="scripts/fixtures/mock_road_segments.csv",
        help="Path to road segments file (.csv)",
    )
    parser.add_argument(
        "--safe-zones-file",
        type=str,
        default="scripts/fixtures/mock_safe_zones.csv",
        help="Path to safe zones file (.csv)",
    )
    parser.add_argument(
        "--time",
        type=str,
        default=None,
        help="Evaluation timestamp (ISO format, e.g. 2026-03-25T21:00:00Z)",
    )
    parser.add_argument(
        "--output",
        type=str,
        default=None,
        help="Output CSV path for feature dataset",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Validate feature generation without exporting files",
    )
    parser.add_argument(
        "-v", "--verbose",
        action="store_true",
        help="Enable verbose debug logging",
    )

    args = parser.parse_args()
    setup_logging(args.verbose)

    crimes_path = Path(args.crimes_file)
    segments_path = Path(args.segments_file)
    safe_zones_path = Path(args.safe_zones_file) if args.safe_zones_file else None

    if not crimes_path.exists():
        print(f"Error: Crimes file not found: {crimes_path}")
        sys.exit(1)
    if not segments_path.exists():
        print(f"Error: Segments file not found: {segments_path}")
        sys.exit(1)

    crimes_df = load_dataset(crimes_path)
    segments_df = load_dataset(segments_path)
    safe_zones_df = load_dataset(safe_zones_path) if safe_zones_path and safe_zones_path.exists() else None

    pipeline = FeaturePipeline()
    eval_time_str = args.time or "2026-03-25T21:00:00Z"

    features_df = pipeline.generate_features_for_segments(
        segments_df=segments_df,
        crimes_df=crimes_df,
        safe_zones_df=safe_zones_df,
        evaluation_time=eval_time_str,
    )

    print_feature_summary(features_df, eval_time_str, args.dry_run)

    if args.output and not args.dry_run:
        out_path = Path(args.output)
        out_path.parent.mkdir(parents=True, exist_ok=True)
        features_df.to_csv(out_path, index=False)
        print(f"Feature dataset successfully saved to: {out_path}\n")


if __name__ == "__main__":
    main()
