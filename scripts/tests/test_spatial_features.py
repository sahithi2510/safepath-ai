"""Unit tests for spatial feature extraction, metric KDE, and precision filtering."""

import pytest
import pandas as pd
import numpy as np

from scripts.feature_pipeline.spatial import SpatialFeatureExtractor
from scripts.feature_pipeline.schemas import FeaturePipelineConfig


@pytest.fixture
def spatial():
    return SpatialFeatureExtractor()


@pytest.fixture
def sample_crimes_df():
    return pd.DataFrame([
        {
            "external_id": "CR_001",
            "latitude": 28.6315,
            "longitude": 77.2167,
            "location_precision": "EXACT_COORDINATE",
            "severity": 4,
            "incident_datetime": "2026-03-15T22:00:00Z",
        },
        {
            "external_id": "CR_002",
            "latitude": 28.6320,
            "longitude": 77.2170,
            "location_precision": "ADDRESS_GEOCODED",
            "severity": 2,
            "incident_datetime": "2026-03-16T14:00:00Z",
        },
        {
            # Coarse record: MUST be excluded from point operations
            "external_id": "CR_003",
            "latitude": 28.6300,
            "longitude": 77.2150,
            "location_precision": "DISTRICT",
            "severity": 3,
            "incident_datetime": "2026-03-17T10:00:00Z",
        },
        {
            # Null Island: MUST be excluded
            "external_id": "CR_004",
            "latitude": 0.0,
            "longitude": 0.0,
            "location_precision": "EXACT_COORDINATE",
            "severity": 2,
            "incident_datetime": "2026-03-18T10:00:00Z",
        },
    ])


def test_filter_valid_crime_points_enforces_precision(spatial, sample_crimes_df):
    """Verify ONLY exact and address-geocoded records are retained for spatial density."""
    gdf, excluded = spatial.filter_valid_crime_points(sample_crimes_df)

    # Out of 4 records: CR_001 (EXACT) and CR_002 (ADDRESS_GEOCODED) pass.
    # CR_003 (DISTRICT) and CR_004 (Null Island) MUST be excluded.
    assert len(gdf) == 2
    assert excluded == 2
    assert set(gdf["external_id"]) == {"CR_001", "CR_002"}
    assert gdf.crs.to_string() == "EPSG:3857"  # In projected metric space


def test_spatial_crime_density_calculation(spatial, sample_crimes_df):
    """Verify Gaussian KDE computes positive density near crimes and zero far away."""
    gdf, _ = spatial.filter_valid_crime_points(sample_crimes_df)
    eval_time = pd.Timestamp("2026-03-25T00:00:00Z")

    # Point right at Connaught Place near CR_001 and CR_002
    near_densities = spatial.compute_crime_density_features(
        eval_point_wgs84=(28.6315, 77.2167),
        crimes_metric_gdf=gdf,
        eval_time=eval_time,
    )

    assert near_densities["crime_density_500m"] > 0.0
    assert near_densities["crime_density_1km"] >= near_densities["crime_density_500m"]
    assert near_densities["severity_weighted_density"] > 0.0

    # Point 10 km away in South Delhi (far from Connaught Place points)
    far_densities = spatial.compute_crime_density_features(
        eval_point_wgs84=(28.5400, 77.2000),
        crimes_metric_gdf=gdf,
        eval_time=eval_time,
    )

    assert far_densities["crime_density_500m"] == 0.0
    assert far_densities["crime_density_1km"] == 0.0


def test_safe_zone_proximities(spatial):
    """Verify nearest distance to police station in meters."""
    safe_zones_df = pd.DataFrame([
        {
            "id": "SZ_001",
            "zone_type": "police",
            "latitude": 28.6328,
            "longitude": 77.2195,
        },
        {
            "id": "SZ_002",
            "zone_type": "hospital",
            "latitude": 28.6340,
            "longitude": 77.2140,
        },
    ])

    prox = spatial.compute_safe_zone_proximities(
        eval_point_wgs84=(28.6315, 77.2167),
        safe_zones_df=safe_zones_df,
    )

    # Distance to police station should be within 100m - 500m
    assert 100.0 <= prox["nearest_police_m"] <= 600.0
    assert 100.0 <= prox["nearest_hospital_m"] <= 600.0
    # Transport absent -> defaults to 500m
    assert prox["nearest_transport_m"] == 500.0


def test_lighting_and_activity_proxies(spatial):
    """Verify lighting score interpretation and road-type activity weight."""
    # Lit road
    lit_score, act_primary = spatial.compute_lighting_and_activity_proxies(
        {"is_lit": True, "road_type": "primary"}, hour_of_day=14.0
    )
    assert lit_score == 1.0
    assert act_primary > 0.5

    # Unlit quiet lane at night
    unlit_score, act_night = spatial.compute_lighting_and_activity_proxies(
        {"is_lit": False, "road_type": "service"}, hour_of_day=3.0
    )
    assert unlit_score == 0.0
    assert act_night < act_primary

    # Missing lit tag -> conservative 0.5 midpoint assumption
    mid_score, _ = spatial.compute_lighting_and_activity_proxies(
        {"is_lit": None, "road_type": "residential"}, hour_of_day=12.0
    )
    assert mid_score == 0.5
