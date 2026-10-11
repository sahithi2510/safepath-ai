"""Unit tests for leakage prevention and chronological dataset splitting."""

from datetime import datetime, timezone
import pytest
import pandas as pd

from scripts.feature_pipeline.leakage_guard import TemporalBoundaryManager


def test_mask_future_crimes_prevents_leakage():
    """Verify crimes occurring after evaluation time are strictly masked out."""
    crimes = pd.DataFrame([
        {"id": "C1", "incident_datetime": "2026-03-10T12:00:00Z"},
        {"id": "C2", "incident_datetime": "2026-03-15T12:00:00Z"},
        {"id": "C3", "incident_datetime": "2026-03-20T12:00:00Z"},  # Future relative to eval_time
        {"id": "C4", "incident_datetime": "2026-03-25T12:00:00Z"},  # Future relative to eval_time
    ])

    eval_time = pd.Timestamp("2026-03-18T00:00:00Z")
    masked = TemporalBoundaryManager.mask_future_crimes(crimes, eval_time)

    assert len(masked) == 2
    assert set(masked["id"]) == {"C1", "C2"}


def test_chronological_split_preserves_temporal_order():
    """Verify chronological split guarantees zero future information in train/val sets."""
    dates = pd.date_range("2026-01-01", periods=100, freq="D", tz="UTC")
    df = pd.DataFrame({
        "snapshot_time": dates,
        "feature_val": range(100),
    })

    train, val, test = TemporalBoundaryManager.chronological_split(
        df, time_col="snapshot_time", train_frac=0.70, val_frac=0.15
    )

    assert len(train) == 70
    assert len(val) == 15
    assert len(test) == 15

    # Temporal boundary assertion: train < val < test
    assert train["snapshot_time"].max() < val["snapshot_time"].min()
    assert val["snapshot_time"].max() < test["snapshot_time"].min()
