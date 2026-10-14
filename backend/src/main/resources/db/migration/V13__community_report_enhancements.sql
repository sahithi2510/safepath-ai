-- ============================================================
-- V13: Make location optional and add location_precision for community reports
-- ============================================================

ALTER TABLE community_reports ALTER COLUMN latitude DROP NOT NULL;
ALTER TABLE community_reports ALTER COLUMN longitude DROP NOT NULL;
ALTER TABLE community_reports ALTER COLUMN geometry DROP NOT NULL;
ALTER TABLE community_reports ADD COLUMN IF NOT EXISTS location_precision VARCHAR(30) DEFAULT 'UNKNOWN';
