-- ============================================================
-- V4: Crime incidents table
-- ============================================================
-- IMPORTANT: location_precision is mandatory for every record.
-- Never insert a crime record without an explicit location_precision.
-- See docs/data-quality.md for the full precision taxonomy.
--
-- Valid precision values:
--   EXACT_COORDINATE  — GPS-quality coordinates from the source dataset
--   ADDRESS_GEOCODED  — Geocoded from a real address; original_address preserved
--   LOCALITY          — Only locality/neighbourhood known
--   POLICE_STATION    — Only associated police station known
--   DISTRICT          — Only district known
--   UNKNOWN           — No usable geographic information
--
-- Columns latitude, longitude, and geometry are NULLABLE.
-- They must only be populated when legitimately available.
-- ============================================================

CREATE TYPE location_precision_enum AS ENUM (
    'EXACT_COORDINATE',
    'ADDRESS_GEOCODED',
    'LOCALITY',
    'POLICE_STATION',
    'DISTRICT',
    'UNKNOWN'
);

CREATE TABLE crime_incidents (
    id                   UUID                    NOT NULL DEFAULT uuid_generate_v4() PRIMARY KEY,
    crime_type           VARCHAR(100)            NOT NULL,
    crime_subtype        VARCHAR(100),
    severity             INTEGER                 NOT NULL CHECK (severity BETWEEN 1 AND 5),
    incident_datetime    TIMESTAMPTZ,
    incident_date        DATE,
    source               VARCHAR(255)            NOT NULL,

    -- Location — NULLABLE; only populated when legitimately available
    latitude             DOUBLE PRECISION,
    longitude            DOUBLE PRECISION,
    geometry             GEOMETRY(Point, 4326),  -- PostGIS point; SRID 4326

    -- Precision tracking — MANDATORY
    location_precision   location_precision_enum NOT NULL,
    location_confidence  FLOAT                   CHECK (location_confidence BETWEEN 0 AND 1),

    -- Original text fields preserved
    original_address     TEXT,
    district             VARCHAR(255),
    locality             VARCHAR(255),
    police_station_name  VARCHAR(255),
    city                 VARCHAR(255),
    state                VARCHAR(255),
    country              VARCHAR(100)            DEFAULT 'India',

    -- Raw source data for audit trail
    raw_source_data      JSONB,

    created_at           TIMESTAMPTZ             NOT NULL DEFAULT NOW()
);

-- Spatial index — only useful when geometry is populated (EXACT_COORDINATE, ADDRESS_GEOCODED)
CREATE INDEX idx_crime_incidents_geometry    ON crime_incidents USING GIST(geometry);
-- Temporal index for time-based queries
CREATE INDEX idx_crime_incidents_datetime    ON crime_incidents(incident_datetime);
-- Precision index — critical for filtering by quality before spatial operations
CREATE INDEX idx_crime_incidents_precision   ON crime_incidents(location_precision);
-- Type/severity for feature queries
CREATE INDEX idx_crime_incidents_type_sev    ON crime_incidents(crime_type, severity);
