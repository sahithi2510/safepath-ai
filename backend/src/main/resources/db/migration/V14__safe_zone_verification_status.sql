-- ============================================================
-- V14: Safe zone verification status and spatial/type indexes
-- ============================================================

ALTER TABLE safe_zones ADD COLUMN IF NOT EXISTS verification_status VARCHAR(20) NOT NULL DEFAULT 'UNVERIFIED';

CREATE INDEX IF NOT EXISTS idx_safe_zones_verification ON safe_zones(verification_status);
CREATE INDEX IF NOT EXISTS idx_safe_zones_type_verification ON safe_zones(zone_type, verification_status);
