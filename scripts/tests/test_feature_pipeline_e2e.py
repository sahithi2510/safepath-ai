"""End-to-end integration tests for spatial and temporal feature pipeline."""

from pathlib import Path
import pytest
import pandas as pd
import numpy as np

from scripts.feature_pipeline.pipeline import FeaturePipeline
from scripts.feature_pipeline.schemas import FEATURE_NAMES


@pytest.fixture
def fixtures_dir():
    return Path(__file__).resolve().parent.parent / "fixtures"


def test_feature_pipeline_e2e_with_mock_fixtures(fixtures_dir):
    """Verify end-to-end feature derivation produces complete feature matrix matching FEATURE_NAMES."""
    crimes_file = fixtures_dir / "mock_crime_records.csv"
    segments_file = fixtures_dir / "mock_road_segments.csv"
    safe_zones_file = fixtures_dir / "mock_safe_zones.csv"

    assert crimes_file.exists()
    assert segments_file.exists()
    assert safe_zones_file.exists()

    crimes_df = pd.read_csv(crimes_file, comment="#")
    segments_df = pd.read_csv(segments_file, comment="#")
    safe_zones_df = pd.read_csv(safe_zones_file, comment="#")

    pipeline = FeaturePipeline()
    features_df = pipeline.generate_features_for_segments(
        segments_df=segments_df,
        crimes_df=crimes_df,
        safe_zones_df=safe_zones_df,
        evaluation_time="2026-03-25T21:30:00Z",
    )

    # 1. Row count matches segments
    assert len(features_df) == len(segments_df)

    # 2. All FEATURE_NAMES are present
    for feat_name in FEATURE_NAMES:
        assert feat_name in features_df.columns, f"Missing expected feature: {feat_name}"

    # 3. No NaNs or infinities in generated features
    for feat_name in FEATURE_NAMES:
        assert features_df[feat_name].notna().all(), f"NaN found in feature {feat_name}"
        assert not np.isinf(features_df[feat_name]).any(), f"Infinity found in feature {feat_name}"

    # 4. Feature completeness is 1.0 (all 15 features populated)
    assert (features_df["feature_completeness"] == 1.0).all()

    # 5. Precision notes accurately document record exclusions
    first_note = features_df.loc[0, "precision_notes"]
    assert "Excluded" in first_note
    assert "coarse records" in first_note


def test_feature_pipeline_empty_segments():
    """Verify empty segment DataFrame returns empty feature set gracefully."""
    pipeline = FeaturePipeline()
    df = pipeline.generate_features_for_segments(
        segments_df=pd.DataFrame(),
        crimes_df=pd.DataFrame(),
    )
    assert df.empty
