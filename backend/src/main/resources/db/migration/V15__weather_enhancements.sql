-- ============================================================
-- V15: Weather observations enhancements
-- Adds wind speed, relative humidity, and weather description
-- ============================================================

ALTER TABLE weather_observations ADD COLUMN IF NOT EXISTS wind_speed_kmh FLOAT;
ALTER TABLE weather_observations ADD COLUMN IF NOT EXISTS relative_humidity FLOAT;
ALTER TABLE weather_observations ADD COLUMN IF NOT EXISTS weather_description VARCHAR(100);

CREATE INDEX IF NOT EXISTS idx_weather_source ON weather_observations(source);
