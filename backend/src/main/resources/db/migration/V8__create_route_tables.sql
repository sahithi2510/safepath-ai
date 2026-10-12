-- ============================================================
-- V8: Route requests and results tables
-- ============================================================

CREATE TABLE route_requests (
    id                   UUID         NOT NULL DEFAULT uuid_generate_v4() PRIMARY KEY,
    user_id              UUID         REFERENCES users(id) ON DELETE SET NULL,
    origin_lat           DOUBLE PRECISION NOT NULL,
    origin_lon           DOUBLE PRECISION NOT NULL,
    destination_lat      DOUBLE PRECISION NOT NULL,
    destination_lon      DOUBLE PRECISION NOT NULL,
    origin_address       TEXT,
    destination_address  TEXT,
    travel_mode          VARCHAR(20)  NOT NULL DEFAULT 'WALKING',
    departure_time       TIMESTAMPTZ,
    route_preference     VARCHAR(20),
    requested_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE route_results (
    id               UUID        NOT NULL DEFAULT uuid_generate_v4() PRIMARY KEY,
    request_id       UUID        NOT NULL REFERENCES route_requests(id) ON DELETE CASCADE,
    route_mode       VARCHAR(20) NOT NULL,   -- FASTEST, BALANCED, SAFEST
    geometry         GEOMETRY(LineString, 4326) NOT NULL,
    distance_meters  FLOAT       NOT NULL,
    duration_seconds FLOAT       NOT NULL,
    safety_score     FLOAT       CHECK (safety_score BETWEEN 0 AND 1),
    risk_level       VARCHAR(20),
    explanation      JSONB,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_route_requests_user_id  ON route_requests(user_id);
CREATE INDEX idx_route_requests_time     ON route_requests(requested_at DESC);
CREATE INDEX idx_route_results_request   ON route_results(request_id);
