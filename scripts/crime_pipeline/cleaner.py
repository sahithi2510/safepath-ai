"""SafePath AI - Crime Data Cleaner & Field Normalizer.

Standardizes raw crime records, validates spatial constraints, resolves severity scores,
and enforces location precision taxonomy without fabricating data.
"""

from __future__ import annotations

from datetime import date, datetime, timezone
import logging
import math
import re
from typing import Any, Dict, Optional, Tuple, Union

from dateutil import parser as date_parser

from scripts.crime_pipeline.schemas import (
    DEFAULT_SEVERITY,
    LocationPrecision,
    NormalizedCrimeRecord,
    SEVERITY_MAPPINGS,
)

logger = logging.getLogger(__name__)


class CrimeDataCleaner:
    """Normalizes and cleans raw crime incident data for SafePath AI."""

    def __init__(self, default_source: str = "unspecified_source"):
        self.default_source = default_source

    def clean_record(
        self,
        raw: Dict[str, Any],
        source_override: Optional[str] = None,
        already_geocoded: bool = False,
    ) -> NormalizedCrimeRecord:
        """Clean and normalize a single raw record into a NormalizedCrimeRecord.

        Args:
            raw: Dictionary containing raw fields from CSV, JSON, or DB.
            source_override: Optional source identifier to override raw data.
            already_geocoded: True if this record was already geocoded.

        Returns:
            NormalizedCrimeRecord ready for PostGIS persistence.
        """
        # 1. Source provenance
        source = (
            source_override
            or raw.get("source")
            or raw.get("source_dataset")
            or self.default_source
        )

        # 2. External identifier preservation
        external_id = (
            str(raw.get("external_id") or raw.get("id") or raw.get("fir_number") or raw.get("case_id") or "").strip()
            or None
        )

        # 3. Crime category & type standardization
        raw_type = str(
            raw.get("crime_type")
            or raw.get("crime_category")
            or raw.get("offense")
            or raw.get("ipc_section")
            or "unspecified"
        ).strip()
        crime_type = raw_type if raw_type else "unspecified"

        raw_subtype = raw.get("crime_subtype") or raw.get("sub_category") or raw.get("description")
        crime_subtype = str(raw_subtype).strip() if raw_subtype else None

        # 4. Severity resolution (1 - 5)
        raw_severity = raw.get("severity") or raw.get("severity_weight")
        severity = self.resolve_severity(crime_type, crime_subtype, raw_severity)

        # 5. Temporal normalization
        incident_datetime, incident_date = self.parse_datetime(
            raw.get("incident_datetime")
            or raw.get("incident_date")
            or raw.get("date")
            or raw.get("timestamp")
            or raw.get("occurrence_date")
        )

        # 6. Coordinate validation
        raw_lat = raw.get("latitude") if raw.get("latitude") is not None else raw.get("lat")
        raw_lon = raw.get("longitude") if raw.get("longitude") is not None else raw.get("lon")
        lat, lon = self.validate_coordinates(raw_lat, raw_lon)
        has_coords = lat is not None and lon is not None

        # 7. Text location fields (preserved exactly)
        original_address = self._clean_str(
            raw.get("original_address") or raw.get("address") or raw.get("incident_address") or raw.get("location_text")
        )
        district = self._clean_str(raw.get("district") or raw.get("district_name"))
        locality = self._clean_str(raw.get("locality") or raw.get("neighbourhood") or raw.get("area"))
        police_station_name = self._clean_str(
            raw.get("police_station_name")
            or raw.get("police_station")
            or raw.get("ps_name")
            or raw.get("thana")
        )
        city = self._clean_str(raw.get("city") or raw.get("town"))
        state = self._clean_str(raw.get("state") or raw.get("state_name"))
        country = self._clean_str(raw.get("country")) or "India"

        # 8. Location precision taxonomy determination
        raw_precision = raw.get("location_precision")
        location_precision = self.determine_precision(
            raw_precision=raw_precision,
            has_coords=has_coords,
            is_geocoded=already_geocoded,
            has_address=bool(original_address),
            has_locality=bool(locality),
            has_police_station=bool(police_station_name),
            has_district=bool(district),
        )

        # 9. Confidence score
        raw_conf = raw.get("location_confidence")
        location_confidence = self._parse_confidence(raw_conf, location_precision)

        # AGENTS.md §2.2 check: Coarse precisions MUST NOT retain coordinates
        if location_precision in {
            LocationPrecision.LOCALITY,
            LocationPrecision.POLICE_STATION,
            LocationPrecision.DISTRICT,
            LocationPrecision.UNKNOWN,
        }:
            lat = None
            lon = None

        # 10. Raw source preservation for complete auditability
        raw_source_data = dict(raw)
        if external_id and "external_id" not in raw_source_data:
            raw_source_data["external_id"] = external_id

        return NormalizedCrimeRecord(
            external_id=external_id,
            crime_type=crime_type,
            crime_subtype=crime_subtype,
            severity=severity,
            incident_datetime=incident_datetime,
            incident_date=incident_date,
            source=source,
            latitude=lat,
            longitude=lon,
            location_precision=location_precision,
            location_confidence=location_confidence,
            original_address=original_address,
            district=district,
            locality=locality,
            police_station_name=police_station_name,
            city=city,
            state=state,
            country=country,
            raw_source_data=raw_source_data,
        )

    def resolve_severity(
        self,
        crime_type: str,
        crime_subtype: Optional[str] = None,
        explicit_severity: Optional[Any] = None,
    ) -> int:
        """Resolve crime severity on a 1-5 integer scale."""
        if explicit_severity is not None:
            try:
                val = int(explicit_severity)
                if 1 <= val <= 5:
                    return val
            except (ValueError, TypeError):
                pass

        # Try matching lower-cased strings
        type_key = crime_type.strip().lower()
        if type_key in SEVERITY_MAPPINGS:
            return SEVERITY_MAPPINGS[type_key]

        if crime_subtype:
            sub_key = crime_subtype.strip().lower()
            if sub_key in SEVERITY_MAPPINGS:
                return SEVERITY_MAPPINGS[sub_key]

        # Partial substring match
        for key, score in SEVERITY_MAPPINGS.items():
            if key in type_key or (crime_subtype and key in crime_subtype.lower()):
                return score

        logger.debug(
            "No explicit severity rule for crime_type='%s'. Defaulting to %d.",
            crime_type,
            DEFAULT_SEVERITY,
        )
        return DEFAULT_SEVERITY

    def parse_datetime(
        self, raw_val: Any
    ) -> Tuple[Optional[datetime], Optional[date]]:
        """Parse various date/time formats into UTC datetime and date objects."""
        if raw_val is None or (isinstance(raw_val, float) and math.isnan(raw_val)):
            return None, None

        if isinstance(raw_val, datetime):
            dt = raw_val if raw_val.tzinfo else raw_val.replace(tzinfo=timezone.utc)
            return dt, dt.date()

        if isinstance(raw_val, date):
            dt = datetime.combine(raw_val, datetime.min.time(), tzinfo=timezone.utc)
            return dt, raw_val

        str_val = str(raw_val).strip()
        if not str_val or str_val.lower() in ("nan", "null", "none"):
            return None, None

        # Clean common timestamp noise
        clean_str = re.sub(r"\s+", " ", str_val)

        try:
            # dateutil handles ISO 8601, 'YYYY-MM-DD', 'DD/MM/YYYY', etc.
            parsed = date_parser.parse(clean_str, dayfirst=True)
            if not parsed.tzinfo:
                parsed = parsed.replace(tzinfo=timezone.utc)
            return parsed, parsed.date()
        except (ValueError, TypeError, OverflowError) as exc:
            logger.warning("Could not parse date string '%s': %s", str_val, exc)
            return None, None

    def validate_coordinates(
        self, raw_lat: Any, raw_lon: Any
    ) -> Tuple[Optional[float], Optional[float]]:
        """Validate geographic coordinates.

        Null Island (0.0, 0.0), NaN, and out-of-bounds coordinates are set to None.
        """
        if raw_lat is None or raw_lon is None:
            return None, None

        try:
            lat = float(raw_lat)
            lon = float(raw_lon)
        except (ValueError, TypeError):
            return None, None

        if math.isnan(lat) or math.isnan(lon) or math.isinf(lat) or math.isinf(lon):
            return None, None

        # Null Island detection
        if abs(lat) < 1e-6 and abs(lon) < 1e-6:
            logger.debug("Coordinate (0, 0) flagged as missing/placeholder.")
            return None, None

        # Lat/Lon bounds validation
        if not (-90.0 <= lat <= 90.0):
            logger.warning("Latitude %f out of bounds [-90, 90]", lat)
            return None, None

        if not (-180.0 <= lon <= 180.0):
            logger.warning("Longitude %f out of bounds [-180, 180]", lon)
            return None, None

        return lat, lon

    def determine_precision(
        self,
        raw_precision: Optional[Any],
        has_coords: bool,
        is_geocoded: bool,
        has_address: bool,
        has_locality: bool,
        has_police_station: bool,
        has_district: bool,
    ) -> LocationPrecision:
        """Assign honest location precision based on AGENTS.md taxonomy."""
        # 1. Check if raw precision string matches an official enum value
        if raw_precision:
            p_str = str(raw_precision).strip().upper()
            try:
                parsed_enum = LocationPrecision(p_str)
                # Verify consistency:
                if parsed_enum in (
                    LocationPrecision.EXACT_COORDINATE,
                    LocationPrecision.ADDRESS_GEOCODED,
                ):
                    if has_coords:
                        return parsed_enum
                    # If claimed exact/geocoded but coords missing, fall back to descriptive level
                    if has_address:
                        return LocationPrecision.LOCALITY if has_locality else LocationPrecision.UNKNOWN
                else:
                    return parsed_enum
            except ValueError:
                pass

        # 2. Inferred taxonomy based on real data availability
        if has_coords:
            return (
                LocationPrecision.ADDRESS_GEOCODED
                if is_geocoded
                else LocationPrecision.EXACT_COORDINATE
            )

        if has_address or has_locality:
            return LocationPrecision.LOCALITY

        if has_police_station:
            return LocationPrecision.POLICE_STATION

        if has_district:
            return LocationPrecision.DISTRICT

        return LocationPrecision.UNKNOWN

    def _parse_confidence(
        self, raw_conf: Any, precision: LocationPrecision
    ) -> Optional[float]:
        """Determine confidence score between 0.0 and 1.0."""
        if raw_conf is not None:
            try:
                c = float(raw_conf)
                if 0.0 <= c <= 1.0:
                    return c
            except (ValueError, TypeError):
                pass

        # Default confidence heuristics based on precision tier
        confidence_defaults = {
            LocationPrecision.EXACT_COORDINATE: 1.0,
            LocationPrecision.ADDRESS_GEOCODED: 0.8,
            LocationPrecision.LOCALITY: 0.5,
            LocationPrecision.POLICE_STATION: 0.3,
            LocationPrecision.DISTRICT: 0.1,
            LocationPrecision.UNKNOWN: 0.0,
        }
        return confidence_defaults.get(precision, None)

    @staticmethod
    def _clean_str(val: Any) -> Optional[str]:
        if val is None or (isinstance(val, float) and math.isnan(val)):
            return None
        s = str(val).strip()
        if not s or s.lower() in ("nan", "null", "none"):
            return None
        return s
