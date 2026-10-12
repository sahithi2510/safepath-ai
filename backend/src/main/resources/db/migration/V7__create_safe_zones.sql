-- ============================================================
-- V7: Safe zones table
-- ============================================================

CREATE TABLE safe_zones (
    id               UUID        NOT NULL DEFAULT uuid_generate_v4() PRIMARY KEY,
    name             VARCHAR(255) NOT NULL,
    zone_type        VARCHAR(50)  NOT NULL,  -- POLICE_STATION, HOSPITAL, PHARMACY, etc.
    latitude         DOUBLE PRECISION NOT NULL,
    longitude        DOUBLE PRECISION NOT NULL,
    geometry         GEOMETRY(Point, 4326) NOT NULL,
    address          TEXT,
    phone_number     VARCHAR(20),
    operating_hours  VARCHAR(255),
    osm_node_id      BIGINT,
    is_24h           BOOLEAN,
    source           VARCHAR(100) DEFAULT 'osm',
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_safe_zones_geometry  ON safe_zones USING GIST(geometry);
CREATE INDEX idx_safe_zones_type      ON safe_zones(zone_type);
CREATE INDEX idx_safe_zones_osm       ON safe_zones(osm_node_id);
