"""SafePath AI - Spatial & Temporal Feature Pipeline Orchestrator.

Combines spatial density, temporal context, emergency service proximity,
and infrastructure proxies into model-ready feature datasets matching FEATURE_NAMES.
"""

from __future__ import annotations

from datetime import datetime, timezone
import logging
from typing import Any, Dict, List, Optional, Tuple, Union

import geopandas as gpd
import numpy as np
import pandas as pd
from shapely.geometry import LineString, Point

from scripts.feature_pipeline.leakage_guard import TemporalBoundaryManager
from scripts.feature_pipeline.schemas import (
    FEATURE_NAMES,
    FeaturePipelineConfig,
    SegmentFeatureRecord,
)
from scripts.feature_pipeline.spatial import SpatialFeatureExtractor
from scripts.feature_pipeline.temporal import TemporalFeatureExtractor

logger = logging.getLogger(__name__)


class FeaturePipeline:
    """Orchestrates end-to-end feature extraction for road segments."""

    def __init__(
        self,
        config: Optional[FeaturePipelineConfig] = None,
        spatial_extractor: Optional[SpatialFeatureExtractor] = None,
        temporal_extractor: Optional[TemporalFeatureExtractor] = None,
    ):
        self.config = config or FeaturePipelineConfig()
        self.spatial = spatial_extractor or SpatialFeatureExtractor(self.config)
        self.temporal = temporal_extractor or TemporalFeatureExtractor(self.config)
        self.leakage_guard = TemporalBoundaryManager()

    def generate_features_for_segments(
        self,
        segments_df: pd.DataFrame,
        crimes_df: pd.DataFrame,
        safe_zones_df: Optional[pd.DataFrame] = None,
        evaluation_time: Optional[Union[datetime, str, pd.Timestamp]] = None,
        weather_context: Optional[Dict[str, float]] = None,
        community_reports_df: Optional[pd.DataFrame] = None,
    ) -> pd.DataFrame:
        """Generate complete ML feature dataset for road segments.

        Returns DataFrame containing road_segment_id, snapshot_time, coordinates,
        and all columns in FEATURE_NAMES, ready for model training or inference.
        """
        if segments_df.empty:
            logger.warning("Segments DataFrame is empty. Returning empty feature set.")
            return pd.DataFrame(columns=["road_segment_id", "snapshot_time"] + FEATURE_NAMES)

        eval_ts = (
            pd.to_datetime(evaluation_time, utc=True)
            if evaluation_time is not None
            else pd.Timestamp.now(tz=timezone.utc)
        )

        safe_zones = safe_zones_df if safe_zones_df is not None else pd.DataFrame()
        weather = weather_context or {}
        precip = float(weather.get("precipitation_mm", self.config.default_weather_precip))
        visibility = float(weather.get("visibility_km", self.config.default_weather_visibility))

        # 1. Temporal feature derivation
        temporal_feats, is_valid_time = self.temporal.extract_features(eval_ts)
        hour_of_day = temporal_feats["hour_of_day"]
        day_of_week = temporal_feats["day_of_week"]

        # 2. Anti-leakage filtering: mask out any future crimes occurring after evaluation time
        historical_crimes = self.leakage_guard.mask_future_crimes(
            crimes_df=crimes_df,
            evaluation_time=eval_ts,
            time_col="incident_datetime",
        )

        # 3. Precision filtering: ONLY exact/geocoded points enter spatial KDE
        valid_crimes_metric, excluded_coarse = self.spatial.filter_valid_crime_points(historical_crimes)

        records: List[Dict[str, Any]] = []

        for idx, row in segments_df.iterrows():
            segment_id = str(row.get("id") or row.get("segment_id") or row.get("osm_way_id") or f"seg_{idx}")

            # Extract segment point coordinates (midpoint, start point, or explicit lat/lon)
            point_wgs84 = self._extract_segment_point(row)
            if point_wgs84 is None:
                logger.debug("Segment '%s' lacks usable coordinates. Skipping.", segment_id)
                continue

            lat, lon = point_wgs84

            # 4. Spatial crime densities
            spatial_densities = self.spatial.compute_crime_density_features(
                eval_point_wgs84=(lat, lon),
                crimes_metric_gdf=valid_crimes_metric,
                eval_time=eval_ts,
            )

            # 5. Emergency service proximities
            proximities = self.spatial.compute_safe_zone_proximities(
                eval_point_wgs84=(lat, lon),
                safe_zones_df=safe_zones,
            )

            # 6. Lighting & activity proxies
            seg_attrs = {
                "is_lit": row.get("is_lit"),
                "road_type": row.get("road_type") or row.get("highway"),
            }
            lighting_score, activity_proxy = self.spatial.compute_lighting_and_activity_proxies(
                segment_attributes=seg_attrs,
                hour_of_day=hour_of_day,
            )

            # 7. Community report count (verified reports within 500m)
            verified_count = self._count_verified_reports(
                point_wgs84=(lat, lon),
                reports_df=community_reports_df,
                radius_meters=self.config.radius_500m,
            )

            # Build feature dictionary
            features = {
                "crime_density_500m": spatial_densities["crime_density_500m"],
                "crime_density_1km": spatial_densities["crime_density_1km"],
                "severity_weighted_density": spatial_densities["severity_weighted_density"],
                "recent_crime_density": spatial_densities["recent_crime_density"],
                "night_crime_density": spatial_densities["night_crime_density"],
                "hour_of_day": hour_of_day,
                "day_of_week": day_of_week,
                "lighting_score": lighting_score,
                "activity_proxy": activity_proxy,
                "nearest_police_m": proximities["nearest_police_m"],
                "nearest_hospital_m": proximities["nearest_hospital_m"],
                "nearest_transport_m": proximities["nearest_transport_m"],
                "weather_precipitation": precip,
                "weather_visibility": visibility,
                "verified_report_count": verified_count,
            }

            # Completeness fraction
            completeness = sum(1 for v in features.values() if v is not None) / len(FEATURE_NAMES)

            # Precision notes documenting exclusions per AGENTS.md
            notes = (
                f"Computed using {len(valid_crimes_metric)} point-precise crime records. "
                f"Excluded {excluded_coarse} coarse records (locality/district/police_station)."
            )

            record = {
                "road_segment_id": segment_id,
                "snapshot_time": eval_ts.isoformat(),
                "latitude": lat,
                "longitude": lon,
                "feature_completeness": completeness,
                "precision_notes": notes,
            }
            record.update(features)
            records.append(record)

        result_df = pd.DataFrame(records)
        logger.info(
            "Feature generation complete: %d segments processed at time %s",
            len(result_df),
            eval_ts.isoformat(),
        )
        return result_df

    def _extract_segment_point(self, row: pd.Series) -> Optional[Tuple[float, float]]:
        """Extract (latitude, longitude) center or evaluation point for a segment."""
        # 1. Explicit lat/lon
        lat = row.get("latitude") or row.get("lat")
        lon = row.get("longitude") or row.get("lon")
        if pd.notna(lat) and pd.notna(lon):
            return float(lat), float(lon)

        # 2. Geometry object (Shapely Point or LineString)
        geom = row.get("geometry")
        if geom is not None and not pd.isna(geom):
            if isinstance(geom, Point):
                return float(geom.y), float(geom.x)
            elif isinstance(geom, LineString):
                mid = geom.interpolate(0.5, normalized=True)
                return float(mid.y), float(mid.x)

        return None

    def _count_verified_reports(
        self,
        point_wgs84: Tuple[float, float],
        reports_df: Optional[pd.DataFrame],
        radius_meters: float = 500.0,
    ) -> int:
        """Count verified community reports within radius."""
        if reports_df is None or reports_df.empty:
            return 0

        # Filter strictly verified reports per AGENTS.md §9
        verified_mask = reports_df.get("status", "").astype(str).str.upper() == "VERIFIED"
        if not np.any(verified_mask) and "is_verified" in reports_df.columns:
            verified_mask = reports_df["is_verified"].astype(bool)

        verified_df = reports_df[verified_mask]
        if verified_df.empty:
            return 0

        # Distance calculation in metric projection
        lat, lon = point_wgs84
        pt_wgs84 = gpd.GeoSeries([Point(lon, lat)], crs=self.config.wgs84_crs)
        pt_metric = pt_wgs84.to_crs(self.config.metric_crs).iloc[0]

        valid_pts = verified_df[
            pd.to_numeric(verified_df.get("latitude"), errors="coerce").notna()
            & pd.to_numeric(verified_df.get("longitude"), errors="coerce").notna()
        ]
        if valid_pts.empty:
            return 0

        geoms = [
            Point(xy)
            for xy in zip(
                pd.to_numeric(valid_pts["longitude"]),
                pd.to_numeric(valid_pts["latitude"]),
            )
        ]
        gdf = gpd.GeoDataFrame(valid_pts, geometry=geoms, crs=self.config.wgs84_crs).to_crs(
            self.config.metric_crs
        )

        coords = np.column_stack([gdf.geometry.x, gdf.geometry.y])
        distances = np.sqrt((coords[:, 0] - pt_metric.x) ** 2 + (coords[:, 1] - pt_metric.y) ** 2)
        return int(np.sum(distances <= radius_meters))
