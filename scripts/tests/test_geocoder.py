"""Unit tests for geocoding service, persistent cache, and usage constraints."""

import time
from unittest.mock import patch
import pytest

from scripts.crime_pipeline.geocoder import (
    GeocodingResult,
    MockGeocoder,
    NominatimGeocoder,
    SQLiteGeocodeCache,
)


def test_sqlite_geocode_cache(tmp_path):
    """Verify persistent SQLite cache stores and retrieves geocoded results."""
    cache_path = tmp_path / "cache.sqlite"
    cache = SQLiteGeocodeCache(cache_path)

    res = GeocodingResult(
        query="Connaught Place, New Delhi",
        latitude=28.6315,
        longitude=77.2167,
        display_name="Connaught Place, New Delhi, Delhi, India",
        confidence=0.82,
        provider="nominatim",
        success=True,
    )
    cache.set(res)

    retrieved = cache.get("connaught place, new delhi")
    assert retrieved is not None
    assert retrieved.cached is True
    assert retrieved.latitude == pytest.approx(28.6315)
    assert retrieved.longitude == pytest.approx(77.2167)
    assert retrieved.confidence == pytest.approx(0.82)
    assert retrieved.provider == "nominatim"

    # Missing query returns None
    assert cache.get("non-existent place") is None


def test_mock_geocoder():
    """Verify MockGeocoder provides deterministic test coordinates and records failures."""
    geocoder = MockGeocoder()
    geocoder.register("connaught place", 28.6315, 77.2167)

    # Registered address succeeds
    res1 = geocoder.geocode("Inner Circle Connaught Place, New Delhi")
    assert res1.success is True
    assert res1.latitude == pytest.approx(28.6315)
    assert res1.longitude == pytest.approx(77.2167)
    assert res1.confidence > 0.8

    # Unregistered address fails cleanly without guessing
    res2 = geocoder.geocode("Unknown Village Road, Nowhere")
    assert res2.success is False
    assert res2.latitude is None
    assert res2.longitude is None
    assert "not registered" in res2.error_message


def test_nominatim_low_confidence_rejection(tmp_path):
    """Verify low-confidence matches are rejected to prevent imprecise points."""
    cache_path = tmp_path / "test_nominatim.sqlite"
    geocoder = NominatimGeocoder(
        cache_file=str(cache_path),
        min_confidence_threshold=0.5,
    )

    # Mock response returning importance=0.2 (below 0.5 threshold)
    mock_data = [
        {
            "lat": "28.5",
            "lon": "77.2",
            "display_name": "Broad Region, India",
            "importance": 0.2,
        }
    ]

    with patch.object(geocoder, "_request_nominatim", return_value=GeocodingResult(
        query="Vague Locality",
        latitude=None,
        longitude=None,
        display_name="Broad Region, India",
        confidence=0.2,
        provider="nominatim",
        success=False,
        error_message="Match rejected: confidence 0.20 below threshold 0.50",
    )):
        result = geocoder.geocode("Vague Locality")
        assert result.success is False
        assert result.latitude is None
        assert result.longitude is None
