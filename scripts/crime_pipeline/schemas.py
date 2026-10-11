"""SafePath AI - Crime Data Ingestion Schemas.

Defines the geographic precision taxonomy, data models, validation constraints,
and severity mapping rules adhering to AGENTS.md.
"""

from __future__ import annotations

from datetime import date, datetime
from enum import Enum
from typing import Any, Dict, List, Optional
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator


class LocationPrecision(str, Enum):
    """Geographic precision taxonomy required by SafePath AI AGENTS.md §2.3.

    Non-negotiable integrity rule:
    Only EXACT_COORDINATE and ADDRESS_GEOCODED may carry valid point coordinates.
    All coarser precision levels MUST NOT carry arbitrary coordinates.
    """
    EXACT_COORDINATE = "EXACT_COORDINATE"
    ADDRESS_GEOCODED = "ADDRESS_GEOCODED"
    LOCALITY = "LOCALITY"
    POLICE_STATION = "POLICE_STATION"
    DISTRICT = "DISTRICT"
    UNKNOWN = "UNKNOWN"


# IMPLEMENTATION CHOICE: Crime category to 1-5 severity mapping.
# Documented per AGENTS.md §4 & §11 guidelines.
# Scale:
# 5 = Critical / Extreme Danger (sexual assault, homicide, armed violence)
# 4 = High Danger (aggravated assault, stalking with violence, robbery, molestation)
# 3 = Moderate Danger / Harassment (street harassment, eve teasing, intimidation, snatching)
# 2 = Property / Low-to-Moderate (theft, burglary, pickpocketing, vandalism)
# 1 = Minor / Nuisance (public disturbance, non-violent nuisance, minor dispute)
SEVERITY_MAPPINGS: Dict[str, int] = {
    # Severity 5 - Extreme danger / heinous
    "sexual assault": 5,
    "rape": 5,
    "homicide": 5,
    "murder": 5,
    "attempted murder": 5,
    "kidnapping": 5,
    "gang rape": 5,
    "armed robbery": 5,

    # Severity 4 - High danger / violent
    "assault": 4,
    "physical assault": 4,
    "aggravated assault": 4,
    "stalking": 4,
    "stalking with threats": 4,
    "molestation": 4,
    "robbery": 4,
    "extortion": 4,
    "battery": 4,

    # Severity 3 - Harassment / street crimes affecting women's safety
    "harassment": 3,
    "street harassment": 3,
    "eve teasing": 3,
    "eve-teasing": 3,
    "snatching": 3,
    "chain snatching": 3,
    "intimidation": 3,
    "verbal abuse": 3,
    "threat": 3,
    "indecent exposure": 3,

    # Severity 2 - Property / low threat
    "theft": 2,
    "burglary": 2,
    "pickpocketing": 2,
    "trespassing": 2,
    "vandalism": 2,
    "vehicle theft": 2,
    "fraud": 2,

    # Severity 1 - Minor nuisance
    "public nuisance": 1,
    "disorderly conduct": 1,
    "loitering": 1,
    "minor dispute": 1,
    "noise violation": 1,
}

DEFAULT_SEVERITY = 3


class RawCrimeRecord(BaseModel):
    """Raw crime record before normalization."""
    external_id: Optional[str] = None
    crime_type: Optional[str] = None
    crime_subtype: Optional[str] = None
    severity: Optional[int] = None
    incident_datetime: Optional[str] = None
    incident_date: Optional[str] = None
    latitude: Optional[float] = None
    longitude: Optional[float] = None
    location_precision: Optional[str] = None
    original_address: Optional[str] = None
    district: Optional[str] = None
    locality: Optional[str] = None
    police_station_name: Optional[str] = None
    city: Optional[str] = None
    state: Optional[str] = None
    country: Optional[str] = "India"
    source: Optional[str] = None
    raw_fields: Dict[str, Any] = Field(default_factory=dict)

    model_config = ConfigDict(extra="allow")


class NormalizedCrimeRecord(BaseModel):
    """Normalized, validated crime record ready for PostGIS insertion.

    Strictly satisfies AGENTS.md non-negotiable data integrity rules:
    - Never fabricates coordinates.
    - If location_precision is coarse (LOCALITY, POLICE_STATION, DISTRICT, UNKNOWN),
      latitude, longitude, and geometry MUST remain None.
    - Preserves source provenance and audit metadata.
    """
    id: Optional[UUID] = None
    external_id: Optional[str] = None
    crime_type: str
    crime_subtype: Optional[str] = None
    severity: int = Field(ge=1, le=5)
    incident_datetime: Optional[datetime] = None
    incident_date: Optional[date] = None
    source: str

    latitude: Optional[float] = None
    longitude: Optional[float] = None
    location_precision: LocationPrecision
    location_confidence: Optional[float] = Field(default=None, ge=0.0, le=1.0)

    original_address: Optional[str] = None
    district: Optional[str] = None
    locality: Optional[str] = None
    police_station_name: Optional[str] = None
    city: Optional[str] = None
    state: Optional[str] = None
    country: str = "India"

    raw_source_data: Dict[str, Any] = Field(default_factory=dict)
    created_at: Optional[datetime] = None

    model_config = ConfigDict(use_enum_values=True)

    @field_validator("latitude")
    @classmethod
    def validate_latitude(cls, v: Optional[float]) -> Optional[float]:
        if v is not None:
            if not (-90.0 <= v <= 90.0):
                raise ValueError(f"Latitude must be between -90 and 90, got {v}")
        return v

    @field_validator("longitude")
    @classmethod
    def validate_longitude(cls, v: Optional[float]) -> Optional[float]:
        if v is not None:
            if not (-180.0 <= v <= 180.0):
                raise ValueError(f"Longitude must be between -180 and 180, got {v}")
        return v

    @model_validator(mode="after")
    def enforce_precision_coordinate_invariance(self) -> NormalizedCrimeRecord:
        """Enforce AGENTS.md §2.1 & §2.2:

        Coarse location records MUST NOT have coordinates.
        Coordinates are only permitted for EXACT_COORDINATE or ADDRESS_GEOCODED.
        """
        coarse_precisions = {
            LocationPrecision.LOCALITY.value,
            LocationPrecision.POLICE_STATION.value,
            LocationPrecision.DISTRICT.value,
            LocationPrecision.UNKNOWN.value,
        }

        if self.location_precision in coarse_precisions:
            if self.latitude is not None or self.longitude is not None:
                raise ValueError(
                    f"Integrity Violation: Records with precision '{self.location_precision}' "
                    f"must NOT have coordinates (lat={self.latitude}, lon={self.longitude}). "
                    f"Coarse locations cannot be silently converted to points."
                )

        if self.location_precision in {
            LocationPrecision.EXACT_COORDINATE.value,
            LocationPrecision.ADDRESS_GEOCODED.value,
        }:
            if (self.latitude is None and self.longitude is not None) or (
                self.latitude is not None and self.longitude is None
            ):
                raise ValueError("Both latitude and longitude must be provided together.")

        return self


class IngestionSummary(BaseModel):
    """Execution summary and data quality audit for crime ingestion run."""
    source_dataset: str
    total_records: int = 0
    valid_records: int = 0
    inserted_records: int = 0
    updated_records: int = 0
    duplicate_skipped: int = 0
    validation_errors: int = 0
    geocoded_records: int = 0
    geocoding_failures: int = 0
    precision_breakdown: Dict[str, int] = Field(default_factory=dict)
    duration_seconds: float = 0.0
    error_messages: List[str] = Field(default_factory=list)
    dry_run: bool = False
