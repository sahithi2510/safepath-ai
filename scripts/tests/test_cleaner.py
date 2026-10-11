"""Unit tests for crime data cleaner, normalizer, and precision enforcement."""

from datetime import date, datetime
import pytest
from pydantic import ValidationError

from scripts.crime_pipeline.cleaner import CrimeDataCleaner
from scripts.crime_pipeline.schemas import (
    LocationPrecision,
    NormalizedCrimeRecord,
)


@pytest.fixture
def cleaner():
    return CrimeDataCleaner(default_source="test_source")


def test_severity_mapping(cleaner):
    """Verify severity resolution maps crimes to 1-5 academic scale."""
    assert cleaner.resolve_severity("rape") == 5
    assert cleaner.resolve_severity("homicide") == 5
    assert cleaner.resolve_severity("physical assault") == 4
    assert cleaner.resolve_severity("stalking") == 4
    assert cleaner.resolve_severity("eve teasing") == 3
    assert cleaner.resolve_severity("snatching") == 3
    assert cleaner.resolve_severity("theft") == 2
    assert cleaner.resolve_severity("burglary") == 2
    assert cleaner.resolve_severity("public nuisance") == 1

    # Explicit valid severity overrides mapping
    assert cleaner.resolve_severity("theft", explicit_severity=4) == 4

    # Unknown crime type falls back to default 3
    assert cleaner.resolve_severity("obscure unknown offense") == 3


def test_coordinate_validation(cleaner):
    """Verify valid coordinates pass while invalid/Null-Island coordinates are filtered out."""
    # Valid coordinates (Delhi)
    lat, lon = cleaner.validate_coordinates(28.6139, 77.2090)
    assert lat == pytest.approx(28.6139)
    assert lon == pytest.approx(77.2090)

    # Valid string coordinates
    lat, lon = cleaner.validate_coordinates("28.6139", "77.2090")
    assert lat == pytest.approx(28.6139)
    assert lon == pytest.approx(77.2090)

    # Null Island (0, 0) MUST be filtered to None
    lat, lon = cleaner.validate_coordinates(0.0, 0.0)
    assert lat is None
    assert lon is None

    # Out of bounds latitude (> 90)
    lat, lon = cleaner.validate_coordinates(120.0, 77.0)
    assert lat is None
    assert lon is None

    # Out of bounds longitude (> 180)
    lat, lon = cleaner.validate_coordinates(28.0, 200.0)
    assert lat is None
    assert lon is None

    # None / Missing
    lat, lon = cleaner.validate_coordinates(None, 77.0)
    assert lat is None
    assert lon is None


def test_datetime_parsing(cleaner):
    """Verify robust parsing of various temporal formats."""
    # ISO 8601
    dt, d = cleaner.parse_datetime("2026-03-15T21:30:00Z")
    assert dt is not None
    assert d == date(2026, 3, 15)
    assert dt.hour == 21

    # Date only YYYY-MM-DD
    dt, d = cleaner.parse_datetime("2026-03-15")
    assert dt is not None
    assert d == date(2026, 3, 15)

    # DD/MM/YYYY
    dt, d = cleaner.parse_datetime("15/03/2026")
    assert dt is not None
    assert d == date(2026, 3, 15)

    # Empty / null
    dt, d = cleaner.parse_datetime(None)
    assert dt is None
    assert d is None


def test_precision_enforcement_exact_coordinate(cleaner):
    """Records with valid coordinates are tagged EXACT_COORDINATE."""
    raw = {
        "external_id": "TEST_001",
        "crime_type": "snatching",
        "latitude": 28.6315,
        "longitude": 77.2167,
        "original_address": "Connaught Place",
    }
    cleaned = cleaner.clean_record(raw)
    assert cleaned.location_precision == LocationPrecision.EXACT_COORDINATE.value
    assert cleaned.latitude == pytest.approx(28.6315)
    assert cleaned.longitude == pytest.approx(77.2167)
    assert cleaned.location_confidence == 1.0


def test_precision_enforcement_coarse_records(cleaner):
    """Coarse location records MUST have NULL coordinates per AGENTS.md §2.2."""
    # Locality only
    raw_loc = {
        "external_id": "TEST_002",
        "crime_type": "assault",
        "locality": "Karol Bagh",
        "latitude": 28.65,  # Even if raw data had approximate lat, coarse precision MUST strip it
        "longitude": 77.19,
        "location_precision": "LOCALITY",
    }
    cleaned = cleaner.clean_record(raw_loc)
    assert cleaned.location_precision == LocationPrecision.LOCALITY.value
    assert cleaned.latitude is None
    assert cleaned.longitude is None

    # Police station only
    raw_ps = {
        "external_id": "TEST_003",
        "crime_type": "stalking",
        "police_station_name": "Hauz Khas PS",
    }
    cleaned_ps = cleaner.clean_record(raw_ps)
    assert cleaned_ps.location_precision == LocationPrecision.POLICE_STATION.value
    assert cleaned_ps.latitude is None
    assert cleaned_ps.longitude is None

    # District only
    raw_dist = {
        "external_id": "TEST_004",
        "crime_type": "burglary",
        "district": "South Delhi",
    }
    cleaned_dist = cleaner.clean_record(raw_dist)
    assert cleaned_dist.location_precision == LocationPrecision.DISTRICT.value
    assert cleaned_dist.latitude is None
    assert cleaned_dist.longitude is None


def test_pydantic_schema_integrity_violation_guard():
    """Verify that creating NormalizedCrimeRecord with coarse precision + coordinates raises ValueError."""
    with pytest.raises(ValidationError) as exc_info:
        NormalizedCrimeRecord(
            crime_type="theft",
            severity=2,
            source="test",
            latitude=28.5,
            longitude=77.2,
            location_precision=LocationPrecision.DISTRICT,
        )
    assert "Integrity Violation" in str(exc_info.value)
