"""End-to-end integration tests for Crime Data Ingestion pipeline using mock fixture."""

from pathlib import Path
import pytest

from scripts.crime_pipeline.cleaner import CrimeDataCleaner
from scripts.crime_pipeline.db import DatabaseManager
from scripts.crime_pipeline.geocoder import MockGeocoder
from scripts.crime_pipeline.ingester import CrimeDataIngester
from scripts.crime_pipeline.schemas import LocationPrecision


@pytest.fixture
def mock_fixture_path():
    path = Path(__file__).resolve().parent.parent / "fixtures" / "mock_crime_records.csv"
    assert path.exists(), f"Mock fixture missing at {path}"
    return path


def test_dry_run_ingestion_mock_fixture(mock_fixture_path):
    """Verify dry-run ingestion processes all 10 mock records with accurate precision tags."""
    cleaner = CrimeDataCleaner()
    ingester = CrimeDataIngester(cleaner=cleaner)

    summary = ingester.ingest_file(
        file_path=mock_fixture_path,
        source="synthetic_fixture",
        enable_geocoding=False,
        dry_run=True,
    )

    assert summary.total_records == 10
    assert summary.valid_records == 10
    assert summary.validation_errors == 0
    assert summary.geocoded_records == 0

    breakdown = summary.precision_breakdown
    assert breakdown[LocationPrecision.EXACT_COORDINATE.value] >= 3
    assert breakdown[LocationPrecision.LOCALITY.value] >= 3
    assert breakdown[LocationPrecision.POLICE_STATION.value] == 1
    assert breakdown[LocationPrecision.DISTRICT.value] == 1
    assert breakdown[LocationPrecision.UNKNOWN.value] >= 1


def test_mock_fixture_with_geocoding(mock_fixture_path):
    """Verify unlocated addresses are geocoded and tagged ADDRESS_GEOCODED."""
    cleaner = CrimeDataCleaner()
    mock_geocoder = MockGeocoder()
    # Register MOCK_002's address in mock geocoder
    mock_geocoder.register("barakhamba road", 28.6290, 77.2250)

    ingester = CrimeDataIngester(
        cleaner=cleaner,
        geocoder=mock_geocoder,
    )

    summary = ingester.ingest_file(
        file_path=mock_fixture_path,
        source="synthetic_fixture",
        enable_geocoding=True,
        dry_run=True,
    )

    assert summary.total_records == 10
    assert summary.valid_records == 10
    assert summary.geocoded_records == 1  # MOCK_002 was geocoded
    assert summary.precision_breakdown[LocationPrecision.ADDRESS_GEOCODED.value] == 1
