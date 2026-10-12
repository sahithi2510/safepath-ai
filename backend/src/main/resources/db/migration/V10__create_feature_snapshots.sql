-- ============================================================
-- V10: Feature snapshots table
-- ============================================================
-- Stores computed ML features per road segment.
-- Used as an input cache to avoid recomputing features on every request.
-- Features are NULLABLE — not all features are available for all segments.
-- Precision notes document any data quality issues for a given snapshot.

CREATE TABLE feature_snapshots (
    id                          UUID        NOT NULL DEFAULT uuid_generate_v4() PRIMARY KEY,
    road_segment_id             UUID        NOT NULL REFERENCES road_segments(id) ON DELETE CASCADE,
    snapshot_time               TIMESTAMPTZ NOT NULL,
    hour_of_day                 INTEGER     CHECK (hour_of_day BETWEEN 0 AND 23),
    day_of_week                 INTEGER     CHECK (day_of_week BETWEEN 0 AND 6),

    -- Spatial crime density features
    -- IMPORTANT: Only populated when sufficient EXACT_COORDINATE or ADDRESS_GEOCODED data exists
    crime_density_500m          FLOAT,
    crime_density_1km           FLOAT,
    severity_weighted_density   FLOAT,
    recent_crime_density        FLOAT,      -- Last 30 days
    night_crime_density         FLOAT,      -- 20:00-05:00 crimes

    -- Lighting (proxy from OSM lit tag)
    lighting_score              FLOAT       CHECK (lighting_score BETWEEN 0 AND 1),

    -- Activity proxy (estimated pedestrian density)
    activity_proxy              FLOAT       CHECK (activity_proxy BETWEEN 0 AND 1),

    -- Emergency service proximity (meters)
    nearest_police_m            FLOAT,
    nearest_hospital_m          FLOAT,
    nearest_transport_m         FLOAT,

    -- Weather context
    weather_precipitation       FLOAT,
    weather_visibility          FLOAT,

    -- Verified community reports
    verified_report_count       INTEGER     NOT NULL DEFAULT 0,

    -- Full feature vector for ML input
    features_json               JSONB,

    -- Data quality documentation
    precision_notes             TEXT,

    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_feature_snapshots_segment  ON feature_snapshots(road_segment_id);
CREATE INDEX idx_feature_snapshots_time     ON feature_snapshots(snapshot_time DESC);
