-- ============================================================
-- V16: Align route_requests and route_results with entity models
-- ============================================================

-- Add entity-mapped columns to route_requests
ALTER TABLE route_requests ADD COLUMN IF NOT EXISTS start_point GEOMETRY(Point, 4326);
ALTER TABLE route_requests ADD COLUMN IF NOT EXISTS end_point GEOMETRY(Point, 4326);
ALTER TABLE route_requests ADD COLUMN IF NOT EXISTS preference VARCHAR(50);
ALTER TABLE route_requests ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

-- Relax NOT NULL constraints for unmapped columns in route_requests
ALTER TABLE route_requests ALTER COLUMN origin_lat DROP NOT NULL;
ALTER TABLE route_requests ALTER COLUMN origin_lon DROP NOT NULL;
ALTER TABLE route_requests ALTER COLUMN destination_lat DROP NOT NULL;
ALTER TABLE route_requests ALTER COLUMN destination_lon DROP NOT NULL;
ALTER TABLE route_requests ALTER COLUMN travel_mode DROP NOT NULL;

-- Add entity-mapped columns to route_results
ALTER TABLE route_results ADD COLUMN IF NOT EXISTS route_line GEOMETRY(LineString, 4326);
ALTER TABLE route_results ADD COLUMN IF NOT EXISTS total_distance_meters FLOAT;
ALTER TABLE route_results ADD COLUMN IF NOT EXISTS estimated_duration_seconds FLOAT;

-- Relax NOT NULL constraints for unmapped columns in route_results
ALTER TABLE route_results ALTER COLUMN route_mode DROP NOT NULL;
ALTER TABLE route_results ALTER COLUMN geometry DROP NOT NULL;
ALTER TABLE route_results ALTER COLUMN distance_meters DROP NOT NULL;
ALTER TABLE route_results ALTER COLUMN duration_seconds DROP NOT NULL;
