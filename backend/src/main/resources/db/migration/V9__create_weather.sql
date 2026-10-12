-- ============================================================
-- V9: Weather observations table
-- ============================================================

CREATE TABLE weather_observations (
    id                UUID        NOT NULL DEFAULT uuid_generate_v4() PRIMARY KEY,
    latitude          DOUBLE PRECISION NOT NULL,
    longitude         DOUBLE PRECISION NOT NULL,
    geometry          GEOMETRY(Point, 4326) NOT NULL,
    observed_at       TIMESTAMPTZ NOT NULL,
    temperature_c     FLOAT,
    precipitation_mm  FLOAT,
    visibility_km     FLOAT,
    weather_code      INTEGER,        -- WMO weather interpretation code
    is_night          BOOLEAN,
    source            VARCHAR(50)  NOT NULL DEFAULT 'open-meteo',
    fetched_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_weather_geometry    ON weather_observations USING GIST(geometry);
CREATE INDEX idx_weather_observed_at ON weather_observations(observed_at DESC);
