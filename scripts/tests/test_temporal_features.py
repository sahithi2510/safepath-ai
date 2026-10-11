"""Unit tests for temporal feature extraction and cyclical representations."""

from datetime import datetime, timezone
import pytest
import pandas as pd

from scripts.feature_pipeline.temporal import TemporalFeatureExtractor


@pytest.fixture
def extractor():
    return TemporalFeatureExtractor()


def test_temporal_features_weekend_night(extractor):
    """Verify Saturday night timestamp derives correct temporal indicators."""
    # 2026-03-21 is a Saturday, 22:30 is night and non-rush hour
    ts = datetime(2026, 3, 21, 22, 30, tzinfo=timezone.utc)
    feats, valid = extractor.extract_features(ts)

    assert valid is True
    assert feats["hour_of_day"] == 22.0
    assert feats["day_of_week"] == 5.0  # Saturday
    assert feats["is_weekend"] == 1.0
    assert feats["is_night"] == 1.0
    assert feats["is_rush_hour"] == 0.0
    assert feats["month"] == 3.0


def test_temporal_features_weekday_rush_hour(extractor):
    """Verify Monday morning rush hour timestamp."""
    # 2026-03-23 is a Monday, 08:30 is rush hour
    ts = datetime(2026, 3, 23, 8, 30, tzinfo=timezone.utc)
    feats, valid = extractor.extract_features(ts)

    assert valid is True
    assert feats["hour_of_day"] == 8.0
    assert feats["day_of_week"] == 0.0  # Monday
    assert feats["is_weekend"] == 0.0
    assert feats["is_night"] == 0.0
    assert feats["is_rush_hour"] == 1.0


def test_temporal_missing_timestamp_imputation(extractor):
    """Verify missing/null timestamp produces documented defaults and flags imputation."""
    feats, valid = extractor.extract_features(None)

    assert valid is False
    assert feats["hour_of_day"] == 12.0  # Midday default
    assert feats["day_of_week"] == 0.0   # Monday default
    assert feats["is_weekend"] == 0.0


def test_temporal_dataframe_vectorized(extractor):
    """Verify vectorized DataFrame temporal feature extraction."""
    df = pd.DataFrame({
        "incident_datetime": [
            "2026-03-21T22:30:00Z",
            "2026-03-23T08:30:00Z",
            None,
        ]
    })
    res = extractor.extract_dataframe(df)

    assert len(res) == 3
    assert res.loc[0, "is_weekend"] == 1.0
    assert res.loc[1, "is_rush_hour"] == 1.0
    assert res.loc[2, "temporal_valid"] == False
    assert res.loc[2, "hour_of_day"] == 12.0
