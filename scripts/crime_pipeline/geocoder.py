"""SafePath AI - Geocoding Service.

Implements rate-limited, cached geocoding using OpenStreetMap Nominatim.
Adheres to AGENTS.md data integrity rules:
- No paid API required.
- Respects Nominatim rate limit (1 req/sec) and User-Agent policy.
- Uses persistent local SQLite cache to prevent redundant queries.
- Records failures and provenance; does not silently guess coordinates.
"""

from __future__ import annotations

from abc import ABC, abstractmethod
from dataclasses import dataclass
from datetime import datetime, timezone
import json
import logging
import os
from pathlib import Path
import sqlite3
import time
from typing import Any, Dict, Optional

import httpx

logger = logging.getLogger(__name__)


@dataclass
class GeocodingResult:
    """Detailed result of a geocoding attempt including provenance."""
    query: str
    latitude: Optional[float]
    longitude: Optional[float]
    display_name: Optional[str]
    confidence: float
    provider: str
    success: bool
    error_message: Optional[str] = None
    timestamp: str = ""
    cached: bool = False

    def __post_init__(self):
        if not self.timestamp:
            self.timestamp = datetime.now(timezone.utc).isoformat()

    def to_dict(self) -> Dict[str, Any]:
        return {
            "query": self.query,
            "latitude": self.latitude,
            "longitude": self.longitude,
            "display_name": self.display_name,
            "confidence": self.confidence,
            "provider": self.provider,
            "success": self.success,
            "error_message": self.error_message,
            "timestamp": self.timestamp,
            "cached": self.cached,
        }


class BaseGeocoder(ABC):
    """Abstract geocoder contract."""

    @abstractmethod
    def geocode(
        self,
        query: str,
        city: Optional[str] = None,
        state: Optional[str] = None,
        country: str = "India",
    ) -> GeocodingResult:
        """Geocode an address into coordinates with confidence score."""
        pass


class SQLiteGeocodeCache:
    """Thread-safe persistent cache for geocoding results."""

    def __init__(self, cache_path: Union[str, Path] = ".geocoding_cache.sqlite"):
        self.cache_path = str(cache_path)
        self._init_db()

    def _init_db(self):
        with sqlite3.connect(self.cache_path) as conn:
            conn.execute(
                """
                CREATE TABLE IF NOT EXISTS geocoding_cache (
                    normalized_query TEXT PRIMARY KEY,
                    latitude REAL,
                    longitude REAL,
                    display_name TEXT,
                    confidence REAL,
                    provider TEXT,
                    success INTEGER,
                    error_message TEXT,
                    created_at TEXT
                )
                """
            )
            conn.commit()

    def get(self, query: str) -> Optional[GeocodingResult]:
        key = self._normalize_key(query)
        try:
            with sqlite3.connect(self.cache_path) as conn:
                cursor = conn.cursor()
                cursor.execute(
                    """
                    SELECT latitude, longitude, display_name, confidence,
                           provider, success, error_message, created_at
                    FROM geocoding_cache WHERE normalized_query = ?
                    """,
                    (key,),
                )
                row = cursor.fetchone()
                if row:
                    lat, lon, d_name, conf, prov, succ, err, c_at = row
                    return GeocodingResult(
                        query=query,
                        latitude=lat,
                        longitude=lon,
                        display_name=d_name,
                        confidence=conf,
                        provider=prov,
                        success=bool(succ),
                        error_message=err,
                        timestamp=c_at,
                        cached=True,
                    )
        except sqlite3.Error as exc:
            logger.warning("Cache read error for '%s': %s", query, exc)
        return None

    def set(self, result: GeocodingResult):
        key = self._normalize_key(result.query)
        try:
            with sqlite3.connect(self.cache_path) as conn:
                conn.execute(
                    """
                    INSERT OR REPLACE INTO geocoding_cache
                    (normalized_query, latitude, longitude, display_name,
                     confidence, provider, success, error_message, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    (
                        key,
                        result.latitude,
                        result.longitude,
                        result.display_name,
                        result.confidence,
                        result.provider,
                        1 if result.success else 0,
                        result.error_message,
                        result.timestamp,
                    ),
                )
                conn.commit()
        except sqlite3.Error as exc:
            logger.warning("Cache write error for '%s': %s", result.query, exc)

    @staticmethod
    def _normalize_key(query: str) -> str:
        return " ".join(query.strip().lower().split())


class NominatimGeocoder(BaseGeocoder):
    """OpenStreetMap Nominatim geocoder respecting usage policies.

    Requirements enforced:
    - Custom User-Agent header (academic identification).
    - Rate limit: at least 1.0 second between network queries.
    - Persistent SQLite caching.
    - Minimum confidence threshold (rejects ambiguous or national/continental matches).
    """

    def __init__(
        self,
        user_agent: Optional[str] = None,
        base_url: Optional[str] = None,
        cache_file: str = ".geocoding_cache.sqlite",
        rate_limit_delay: float = 1.05,
        min_confidence_threshold: float = 0.35,
        timeout: float = 10.0,
    ):
        self.user_agent = (
            user_agent
            or os.getenv("NOMINATIM_USER_AGENT")
            or "SafePathAI-AcademicResearch/1.0 (academic-project; contact: safepath@example.edu)"
        )
        self.base_url = (
            base_url
            or os.getenv("NOMINATIM_BASE_URL")
            or "https://nominatim.openstreetmap.org"
        ).rstrip("/")
        self.cache = SQLiteGeocodeCache(cache_file)
        self.rate_limit_delay = rate_limit_delay
        self.min_confidence = min_confidence_threshold
        self.timeout = timeout
        self._last_request_time: float = 0.0

    def geocode(
        self,
        query: str,
        city: Optional[str] = None,
        state: Optional[str] = None,
        country: str = "India",
    ) -> GeocodingResult:
        full_query = self._build_query_string(query, city, state, country)
        if not full_query:
            return GeocodingResult(
                query=query,
                latitude=None,
                longitude=None,
                display_name=None,
                confidence=0.0,
                provider="nominatim",
                success=False,
                error_message="Empty address query.",
            )

        # 1. Check persistent cache
        cached = self.cache.get(full_query)
        if cached:
            logger.debug("Geocoding cache hit for: '%s'", full_query)
            return cached

        # 2. Rate limiting check (Nominatim ToS: max 1 request/sec)
        elapsed = time.time() - self._last_request_time
        if elapsed < self.rate_limit_delay:
            time.sleep(self.rate_limit_delay - elapsed)

        # 3. Perform network call
        result = self._request_nominatim(full_query)
        self._last_request_time = time.time()

        # 4. Save to persistent cache
        self.cache.set(result)
        return result

    def _request_nominatim(self, full_query: str) -> GeocodingResult:
        endpoint = f"{self.base_url}/search"
        headers = {
            "User-Agent": self.user_agent,
            "Accept": "application/json",
        }
        params = {
            "q": full_query,
            "format": "jsonv2",
            "addressdetails": 1,
            "limit": 1,
        }

        try:
            with httpx.Client(timeout=self.timeout) as client:
                response = client.get(endpoint, headers=headers, params=params)

            if response.status_code != 200:
                msg = f"HTTP {response.status_code}: {response.text[:200]}"
                logger.warning("Nominatim error: %s", msg)
                return GeocodingResult(
                    query=full_query,
                    latitude=None,
                    longitude=None,
                    display_name=None,
                    confidence=0.0,
                    provider="nominatim",
                    success=False,
                    error_message=msg,
                )

            data = response.json()
            if not data or not isinstance(data, list) or len(data) == 0:
                return GeocodingResult(
                    query=full_query,
                    latitude=None,
                    longitude=None,
                    display_name=None,
                    confidence=0.0,
                    provider="nominatim",
                    success=False,
                    error_message="No match found in OpenStreetMap.",
                )

            item = data[0]
            lat = float(item.get("lat"))
            lon = float(item.get("lon"))
            display_name = item.get("display_name", "")
            raw_importance = float(item.get("importance", 0.4))

            # Bound confidence between 0.0 and 1.0
            confidence = max(0.0, min(1.0, raw_importance))

            # Quality check: Reject low-confidence results per AGENTS.md
            if confidence < self.min_confidence:
                return GeocodingResult(
                    query=full_query,
                    latitude=None,
                    longitude=None,
                    display_name=display_name,
                    confidence=confidence,
                    provider="nominatim",
                    success=False,
                    error_message=(
                        f"Match rejected: confidence {confidence:.2f} below threshold {self.min_confidence:.2f}"
                    ),
                )

            return GeocodingResult(
                query=full_query,
                latitude=lat,
                longitude=lon,
                display_name=display_name,
                confidence=confidence,
                provider="nominatim",
                success=True,
            )

        except Exception as exc:
            logger.error("Exception querying Nominatim for '%s': %s", full_query, exc)
            return GeocodingResult(
                query=full_query,
                latitude=None,
                longitude=None,
                display_name=None,
                confidence=0.0,
                provider="nominatim",
                success=False,
                error_message=str(exc),
            )

    @staticmethod
    def _build_query_string(
        query: str,
        city: Optional[str] = None,
        state: Optional[str] = None,
        country: str = "India",
    ) -> str:
        parts = [query.strip()]
        if city and city.lower() not in query.lower():
            parts.append(city.strip())
        if state and state.lower() not in query.lower():
            parts.append(state.strip())
        if country and country.lower() not in query.lower():
            parts.append(country.strip())
        return ", ".join(p for p in parts if p)


class MockGeocoder(BaseGeocoder):
    """Deterministic, offline geocoder for synthetic test fixtures and unit tests."""

    def __init__(self, predefined_results: Optional[Dict[str, Tuple[float, float]]] = None):
        self.predefined = predefined_results or {}
        self.call_count = 0

    def register(self, query: str, lat: float, lon: float):
        self.predefined[query.strip().lower()] = (lat, lon)

    def geocode(
        self,
        query: str,
        city: Optional[str] = None,
        state: Optional[str] = None,
        country: str = "India",
    ) -> GeocodingResult:
        self.call_count += 1
        q_norm = query.strip().lower()

        for key, (lat, lon) in self.predefined.items():
            if key in q_norm:
                return GeocodingResult(
                    query=query,
                    latitude=lat,
                    longitude=lon,
                    display_name=f"Mock Match for {query}",
                    confidence=0.85,
                    provider="mock_geocoder",
                    success=True,
                )

        # Failure simulation for unknown addresses
        return GeocodingResult(
            query=query,
            latitude=None,
            longitude=None,
            display_name=None,
            confidence=0.0,
            provider="mock_geocoder",
            success=False,
            error_message="Mock geocoder address not registered.",
        )
