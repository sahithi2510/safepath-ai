-- ============================================================
-- V5: Road segments table
-- ============================================================
-- Represents the road network graph edges.
-- Loaded from OpenStreetMap data.

CREATE TABLE road_segments (
    id                      UUID            NOT NULL DEFAULT uuid_generate_v4() PRIMARY KEY,
    osm_way_id              BIGINT,
    geometry                GEOMETRY(LineString, 4326) NOT NULL,
    start_node_id           BIGINT          NOT NULL,
    end_node_id             BIGINT          NOT NULL,
    start_point             GEOMETRY(Point, 4326)      NOT NULL,
    end_point               GEOMETRY(Point, 4326)      NOT NULL,
    length_meters           FLOAT           NOT NULL,
    road_type               VARCHAR(50),        -- OSM highway tag (residential, primary, etc.)
    road_name               VARCHAR(255),
    is_lit                  BOOLEAN,            -- From OSM lit tag; NULL = unknown
    max_speed               INTEGER,            -- km/h; NULL = unknown
    is_oneway               BOOLEAN             NOT NULL DEFAULT FALSE,
    walkable                BOOLEAN             NOT NULL DEFAULT TRUE,
    estimated_walk_time_s   FLOAT,              -- Estimated walking time in seconds
    created_at              TIMESTAMPTZ         NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ         NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_road_segments_geometry     ON road_segments USING GIST(geometry);
CREATE INDEX idx_road_segments_start_point  ON road_segments USING GIST(start_point);
CREATE INDEX idx_road_segments_end_point    ON road_segments USING GIST(end_point);
CREATE INDEX idx_road_segments_osm_way      ON road_segments(osm_way_id);
CREATE INDEX idx_road_segments_walkable     ON road_segments(walkable);
