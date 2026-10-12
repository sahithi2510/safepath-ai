-- ============================================================
-- V1: Enable required PostgreSQL extensions
-- ============================================================
-- This must run first. PostGIS provides spatial types and functions.
-- uuid-ossp provides uuid_generate_v4().

CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS postgis_topology;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
