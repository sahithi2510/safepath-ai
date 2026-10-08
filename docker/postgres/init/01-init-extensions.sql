-- PostgreSQL initialization script for Docker
-- Runs once when the container is first created

-- Enable required extensions
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS postgis_topology;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Grant schema permissions
GRANT ALL PRIVILEGES ON DATABASE safepath_db TO safepath_user;
