"""Unit tests for database manager parameter mapping and batch deduplication."""

import pytest
from psycopg2.extras import Json

from scripts.crime_pipeline.db import DatabaseManager
from scripts.crime_pipeline.schemas import (
    LocationPrecision,
    NormalizedCrimeRecord,
)


@pytest.fixture
def sample_record():
    return NormalizedCrimeRecord(
        external_id="REC_001",
        crime_type="harassment",
        crime_subtype="street harassment",
        severity=3,
        source="test_source",
        latitude=28.61,
        longitude=77.21,
        location_precision=LocationPrecision.EXACT_COORDINATE,
        location_confidence=1.0,
        original_address="Janpath, New Delhi",
        district="New Delhi",
        locality="Janpath",
        police_station_name="Connaught Place PS",
        city="New Delhi",
        state="Delhi",
        country="India",
        raw_source_data={"fir_no": "123/2026", "external_id": "REC_001"},
    )


def test_record_to_params(sample_record):
    """Verify record fields translate cleanly into PostgreSQL query parameters."""
    params = DatabaseManager._record_to_params(sample_record)

    assert params["crime_type"] == "harassment"
    assert params["severity"] == 3
    assert params["latitude"] == pytest.approx(28.61)
    assert params["longitude"] == pytest.approx(77.21)
    assert params["location_precision"] == "EXACT_COORDINATE"
    assert params["source"] == "test_source"
    assert isinstance(params["raw_source_data"], Json)


def test_batch_deduplication_dry_run(sample_record):
    """Verify duplicate external IDs are detected and skipped idempotently."""
    db = DatabaseManager()

    # Create batch with 2 unique records and 1 duplicate of REC_001
    rec1 = sample_record
    rec2 = sample_record.model_copy(update={"external_id": "REC_002"})
    rec3_duplicate = sample_record.model_copy(update={"external_id": "REC_001", "crime_subtype": "duplicate copy"})

    batch = [rec1, rec2, rec3_duplicate]

    # In dry-run mode without live DB, batch-level deduplication is verified
    ins, upd, skip = db.insert_batch(batch, dry_run=True, update_existing=False)

    assert ins == 2  # REC_001 and REC_002
    assert upd == 0
    assert skip == 1  # rec3_duplicate skipped
