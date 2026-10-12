-- ============================================================
-- V6: Community reports table
-- ============================================================

CREATE TABLE community_reports (
    id                 UUID         NOT NULL DEFAULT uuid_generate_v4() PRIMARY KEY,
    user_id            UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    report_type        VARCHAR(50)  NOT NULL,
    description        TEXT,
    latitude           DOUBLE PRECISION NOT NULL,
    longitude          DOUBLE PRECISION NOT NULL,
    geometry           GEOMETRY(Point, 4326) NOT NULL,
    road_segment_id    UUID         REFERENCES road_segments(id) ON DELETE SET NULL,
    status             VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    verified_by        UUID         REFERENCES users(id) ON DELETE SET NULL,
    verified_at        TIMESTAMPTZ,
    verification_note  TEXT,
    incident_datetime  TIMESTAMPTZ,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_community_reports_geometry  ON community_reports USING GIST(geometry);
CREATE INDEX idx_community_reports_status    ON community_reports(status);
CREATE INDEX idx_community_reports_user_id   ON community_reports(user_id);
CREATE INDEX idx_community_reports_type      ON community_reports(report_type);
CREATE INDEX idx_community_reports_created   ON community_reports(created_at DESC);
