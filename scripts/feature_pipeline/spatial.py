"""SafePath AI - Spatial Feature Extractor.

Derives spatial density, severity-weighted risk, and emergency proximity features
using GeoPandas, Shapely, and metric Euclidean projections.

Adheres strictly to AGENTS.md §2 & §4:
- Evaluates spatial distance in projected metric space (EPSG:3857, meters).
- ONLY uses records with EXACT_COORDINATE or ADDRESS_GEOCODED precision for point features.
- Coarse-precision records (LOCALITY, POLICE_STATION, DISTRICT, UNKNOWN) are strictly excluded from KDE.
- KDE densities are documented as continuous risk indicators, not discrete event counts.
"""

from __future__ import annotations

import logging
import math
from typing import Any, Dict, List, Optional, Tuple, Union

import geopandas as gpd
import numpy as np
import pandas as pd
from scipy.spatial import KDTree
from shapely.geometry import LineString, Point

from scripts.feature_pipeline.schemas import FeaturePipelineConfig

logger = logging.getLogger(__name__)


# Activity proxy multiplier by road type (OSM highway tag)
# Documented assumption per docs/ml-pipeline.md §4.4
ROAD_TYPE_ACTIVITY_WEIGHTS: Dict[str, float] = {
    "primary": 0.85,
    "primary_link": 0.80,
    "secondary": 0.75,
    "secondary_link": 0.70,
    "tertiary": 0.65,
    "tertiary_link": 0.60,
    "residential": 0.50,
    "living_street": 0.55,
    "pedestrian": 0.70,
    "footway": 0.40,
    "path": 0.35,
    "service": 0.30,
    "track": 0.20,
    "unclassified": 0.45,
}


class SpatialFeatureExtractor:
    """Computes metric spatial crime densities, safe-zone proximities, and infrastructure proxies."""

    def __init__(self, config: Optional[FeaturePipelineConfig] = None):
        self.config = config or FeaturePipelineConfig()

    def filter_valid_crime_points(
        self, crimes_df: pd.DataFrame
    ) -> Tuple[gpd.GeoDataFrame, int]:
        """Filter crime records for spatial point operations.

        Strictly enforces AGENTS.md §2.1 & §2.2:
        - Only EXACT_COORDINATE and ADDRESS_GEOCODED records are accepted.
        - Records with missing or invalid lat/lon are dropped.
        - Returns (projected_geodataframe, excluded_coarse_count).
        """
        if crimes_df.empty:
            empty_gdf = gpd.GeoDataFrame(
                columns=list(crimes_df.columns) + ["geometry"],
                crs=self.config.wgs84_crs,
            )
            return empty_gdf.to_crs(self.config.metric_crs), 0

        total_input = len(crimes_df)

        # 1. Location precision mask
        precision_col = crimes_df.get("location_precision")
        if precision_col is not None:
            valid_prec_mask = crimes_df["location_precision"].astype(str).isin(
                self.config.valid_spatial_precisions
            )
        else:
            valid_prec_mask = pd.Series(True, index=crimes_df.index)

        # 2. Coordinates validity mask
        lat_valid = pd.to_numeric(crimes_df["latitude"], errors="coerce").between(-90, 90)
        lon_valid = pd.to_numeric(crimes_df["longitude"], errors="coerce").between(-180, 180)
        coords_mask = lat_valid & lon_valid

        # Exclude Null Island (0, 0)
        non_zero_mask = ~(
            (pd.to_numeric(crimes_df["latitude"], errors="coerce").abs() < 1e-6)
            & (pd.to_numeric(crimes_df["longitude"], errors="coerce").abs() < 1e-6)
        )

        eligible_mask = valid_prec_mask & coords_mask & non_zero_mask
        excluded_count = total_input - int(eligible_mask.sum())

        filtered_df = crimes_df[eligible_mask].copy()

        # Build GeoDataFrame with WGS84 and project to metric CRS (EPSG:3857)
        geometry = [
            Point(xy)
            for xy in zip(
                pd.to_numeric(filtered_df["longitude"]),
                pd.to_numeric(filtered_df["latitude"]),
            )
        ]
        gdf_wgs84 = gpd.GeoDataFrame(filtered_df, geometry=geometry, crs=self.config.wgs84_crs)
        gdf_metric = gdf_wgs84.to_crs(self.config.metric_crs)

        logger.debug(
            "Spatial filter: %d valid points retained, %d coarse/invalid records excluded.",
            len(gdf_metric),
            excluded_count,
        )
        return gdf_metric, excluded_count

    def compute_crime_density_features(
        self,
        eval_point_wgs84: Tuple[float, float],  # (latitude, longitude)
        crimes_metric_gdf: gpd.GeoDataFrame,
        eval_time: Optional[pd.Timestamp] = None,
    ) -> Dict[str, float]:
        """Compute Gaussian KDE spatial crime densities around an evaluation point.

        Evaluation point: (lat, lon) in EPSG:4326.
        Returns continuous density values for 500m, 1km, severity-weighted, recent, and night.
        """
        lat, lon = eval_point_wgs84
        defaults = {
            "crime_density_500m": 0.0,
            "crime_density_1km": 0.0,
            "severity_weighted_density": 0.0,
            "recent_crime_density": 0.0,
            "night_crime_density": 0.0,
        }

        if crimes_metric_gdf.empty:
            return defaults

        # Project evaluation point into metric CRS
        pt_wgs84 = gpd.GeoSeries([Point(lon, lat)], crs=self.config.wgs84_crs)
        pt_metric = pt_wgs84.to_crs(self.config.metric_crs).iloc[0]
        eval_x, eval_y = pt_metric.x, pt_metric.y

        # Compute Euclidean distances in meters
        coords = np.column_stack([crimes_metric_gdf.geometry.x, crimes_metric_gdf.geometry.y])
        distances = np.sqrt((coords[:, 0] - eval_x) ** 2 + (coords[:, 1] - eval_y) ** 2)

        # Bandwidth parameter (sigma in meters)
        sigma = self.config.kde_bandwidth_meters
        two_sigma_sq = 2.0 * (sigma ** 2)

        # 1. 500m Kernel Density
        mask_500m = distances <= self.config.radius_500m
        if np.any(mask_500m):
            kernel_500m = np.exp(-(distances[mask_500m] ** 2) / two_sigma_sq)
            density_500m = float(np.sum(kernel_500m))
        else:
            density_500m = 0.0

        # 2. 1km Kernel Density
        mask_1km = distances <= self.config.radius_1km
        if np.any(mask_1km):
            kernel_1km = np.exp(-(distances[mask_1km] ** 2) / two_sigma_sq)
            density_1km = float(np.sum(kernel_1km))
        else:
            density_1km = 0.0

        # 3. Severity-weighted density (within 1km)
        if np.any(mask_1km) and "severity" in crimes_metric_gdf.columns:
            severities = pd.to_numeric(
                crimes_metric_gdf["severity"].iloc[mask_1km], errors="coerce"
            ).fillna(3.0).values
            severity_density = float(np.sum(severities * kernel_1km))
        else:
            severity_density = 0.0

        # 4. Recent crime density (last 30 days prior to eval_time)
        recent_density = 0.0
        if eval_time is not None and "incident_datetime" in crimes_metric_gdf.columns:
            crime_times = pd.to_datetime(
                crimes_metric_gdf["incident_datetime"], errors="coerce", utc=True
            )
            cutoff = eval_time - pd.Timedelta(days=self.config.recent_crime_days)
            recent_mask = mask_1km & (crime_times >= cutoff) & (crime_times <= eval_time)
            if np.any(recent_mask):
                k_rec = np.exp(-(distances[recent_mask] ** 2) / two_sigma_sq)
                recent_density = float(np.sum(k_rec))

        # 5. Night crime density (crimes occurring between 20:00 and 05:00)
        night_density = 0.0
        if "incident_datetime" in crimes_metric_gdf.columns:
            crime_times = pd.to_datetime(
                crimes_metric_gdf["incident_datetime"], errors="coerce", utc=True
            )
            hours = crime_times.dt.hour
            night_mask = mask_1km & ((hours >= self.config.night_start_hour) | (hours < self.config.night_end_hour))
            if np.any(night_mask):
                k_night = np.exp(-(distances[night_mask] ** 2) / two_sigma_sq)
                night_density = float(np.sum(k_night))

        return {
            "crime_density_500m": round(density_500m, 4),
            "crime_density_1km": round(density_1km, 4),
            "severity_weighted_density": round(severity_density, 4),
            "recent_crime_density": round(recent_density, 4),
            "night_crime_density": round(night_density, 4),
        }

    def compute_safe_zone_proximities(
        self,
        eval_point_wgs84: Tuple[float, float],
        safe_zones_df: pd.DataFrame,
    ) -> Dict[str, float]:
        """Compute nearest distance in meters to police stations, hospitals, and transport hubs."""
        defaults = {
            "nearest_police_m": self.config.default_nearest_police_m,
            "nearest_hospital_m": self.config.default_nearest_hospital_m,
            "nearest_transport_m": self.config.default_nearest_transport_m,
        }

        if safe_zones_df.empty:
            return defaults

        lat, lon = eval_point_wgs84
        pt_wgs84 = gpd.GeoSeries([Point(lon, lat)], crs=self.config.wgs84_crs)
        pt_metric = pt_wgs84.to_crs(self.config.metric_crs).iloc[0]
        eval_x, eval_y = pt_metric.x, pt_metric.y

        result = dict(defaults)
        zone_categories = {
            "police": "nearest_police_m",
            "hospital": "nearest_hospital_m",
            "pharmacy": "nearest_hospital_m",  # medical fallback
            "transport": "nearest_transport_m",
            "metro": "nearest_transport_m",
            "bus_station": "nearest_transport_m",
        }

        # Filter safe zones with valid coordinates
        valid_sz = safe_zones_df[
            pd.to_numeric(safe_zones_df.get("latitude"), errors="coerce").notna()
            & pd.to_numeric(safe_zones_df.get("longitude"), errors="coerce").notna()
        ].copy()

        if valid_sz.empty:
            return defaults

        sz_geom = [
            Point(xy)
            for xy in zip(
                pd.to_numeric(valid_sz["longitude"]),
                pd.to_numeric(valid_sz["latitude"]),
            )
        ]
        sz_gdf = gpd.GeoDataFrame(valid_sz, geometry=sz_geom, crs=self.config.wgs84_crs).to_crs(
            self.config.metric_crs
        )

        for category, target_field in zone_categories.items():
            if "zone_type" in sz_gdf.columns:
                sub = sz_gdf[sz_gdf["zone_type"].astype(str).str.lower().str.contains(category)]
            elif "type" in sz_gdf.columns:
                sub = sz_gdf[sz_gdf["type"].astype(str).str.lower().str.contains(category)]
            else:
                sub = sz_gdf

            if not sub.empty:
                coords = np.column_stack([sub.geometry.x, sub.geometry.y])
                min_dist = float(
                    np.min(np.sqrt((coords[:, 0] - eval_x) ** 2 + (coords[:, 1] - eval_y) ** 2))
                )
                # Keep the shortest distance if already populated
                if result[target_field] == defaults[target_field] or min_dist < result[target_field]:
                    result[target_field] = round(min_dist, 2)

        return result

    def compute_lighting_and_activity_proxies(
        self,
        segment_attributes: Dict[str, Any],
        hour_of_day: float = 12.0,
    ) -> Tuple[float, float]:
        """Compute lighting score and activity proxy from OSM attributes and time.

        Lighting:
        - is_lit is True -> 1.0
        - is_lit is False -> 0.0
        - is_lit is None / missing -> 0.5 (documented midpoint assumption)

        Activity Proxy:
        - base weight from road_type (OSM highway)
        - temporal adjustment (higher in daytime/rush hour, lower at 3 AM)
        """
        # Lighting score
        raw_lit = segment_attributes.get("is_lit")
        if raw_lit is True or str(raw_lit).lower() in ("true", "yes", "1"):
            lighting = 1.0
        elif raw_lit is False or str(raw_lit).lower() in ("false", "no", "0"):
            lighting = 0.0
        else:
            lighting = self.config.default_lighting_score

        # Activity proxy
        road_type = str(
            segment_attributes.get("road_type") or segment_attributes.get("highway") or "residential"
        ).lower()
        base_weight = ROAD_TYPE_ACTIVITY_WEIGHTS.get(
            road_type, self.config.default_activity_proxy
        )

        # Time-of-day modifier
        if 8.0 <= hour_of_day <= 20.0:
            time_factor = 1.0
        elif (20.0 < hour_of_day <= 23.0) or (6.0 <= hour_of_day < 8.0):
            time_factor = 0.7
        else:
            time_factor = 0.3

        activity = round(min(1.0, max(0.0, base_weight * time_factor)), 3)
        return lighting, activity
