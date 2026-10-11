"""SafePath AI - Feature Pipeline Schemas & Configuration.

Defines feature definitions, spatial/temporal parameters, and record models
strictly compatible with ml-service FEATURE_NAMES and AGENTS.md integrity rules.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime
from typing import Any, Dict, List, Optional, Set, Tuple

# Must match ml-service FEATURE_NAMES exactly for seamless model consumption
FEATURE_NAMES: List[str] = [
    "crime_density_500m",
    "crime_density_1km",
    "severity_weighted_density",
    "recent_crime_density",
    "night_crime_density",
    "hour_of_day",
    "day_of_week",
    "lighting_score",
    "activity_proxy",
    "nearest_police_m",
    "nearest_hospital_m",
    "nearest_transport_m",
    "weather_precipitation",
    "weather_visibility",
    "verified_report_count",
]


@dataclass
class FeaturePipelineConfig:
    """Configuration for spatial and temporal feature derivation."""

    # Coordinate systems
    wgs84_crs: str = "EPSG:4326"
    metric_crs: str = "EPSG:3857"  # Web Mercator projection where coordinates are in meters

    # Spatial parameters
    radius_500m: float = 500.0
    radius_1km: float = 1000.0
    kde_bandwidth_meters: float = 200.0  # Gaussian kernel standard deviation (meters)

    # Temporal parameters
    recent_crime_days: int = 30
    night_start_hour: int = 20
    night_end_hour: int = 5
    morning_rush_start: int = 7
    morning_rush_end: int = 9
    evening_rush_start: int = 17
    evening_rush_end: int = 20

    # Fallback / Imputation defaults (documented assumptions per AGENTS.md §11)
    default_nearest_police_m: float = 2000.0
    default_nearest_hospital_m: float = 2000.0
    default_nearest_transport_m: float = 500.0
    default_lighting_score: float = 0.5
    default_activity_proxy: float = 0.5
    default_weather_precip: float = 0.0
    default_weather_visibility: float = 10.0

    # Non-negotiable precision filter for point-based spatial operations
    # Coarse records (LOCALITY, POLICE_STATION, DISTRICT, UNKNOWN) are strictly excluded from KDE
    valid_spatial_precisions: Set[str] = field(
        default_factory=lambda: {"EXACT_COORDINATE", "ADDRESS_GEOCODED"}
    )


@dataclass
class SegmentFeatureRecord:
    """Feature snapshot for a single road segment at an evaluation timestamp."""

    road_segment_id: str
    snapshot_time: datetime
    features: Dict[str, float]
    feature_completeness: float
    precision_notes: str = ""
    latitude: Optional[float] = None
    longitude: Optional[float] = None

    def to_dict(self) -> Dict[str, Any]:
        result = {
            "road_segment_id": self.road_segment_id,
            "snapshot_time": self.snapshot_time.isoformat(),
            "feature_completeness": self.feature_completeness,
            "precision_notes": self.precision_notes,
            "latitude": self.latitude,
            "longitude": self.longitude,
        }
        result.update(self.features)
        return result
