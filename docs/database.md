# SafePath AI — Database Schema

> **Status:** Living document — updated as schema evolves  
> **Last Updated:** 2026-10-08  
> **Database:** PostgreSQL 15+ with PostGIS 3+  
> **Migrations:** Flyway (see `database/migrations/`)

---

## 1. Extensions Required

```sql
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS postgis_topology;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
```

---

## 2. Tables

### 2.1 `users`

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | UUID | PK, DEFAULT uuid_generate_v4() | |
| `email` | VARCHAR(255) | UNIQUE, NOT NULL | |
| `password_hash` | VARCHAR(255) | NOT NULL | BCrypt |
| `full_name` | VARCHAR(255) | NOT NULL | |
| `phone_number` | VARCHAR(20) | NULLABLE | |
| `route_preference` | VARCHAR(20) | DEFAULT 'STANDARD' | STANDARD, SOLO_TRAVELLER, TOURIST, ELDERLY |
| `is_enabled` | BOOLEAN | DEFAULT TRUE | |
| `created_at` | TIMESTAMP WITH TIME ZONE | NOT NULL | |
| `updated_at` | TIMESTAMP WITH TIME ZONE | NOT NULL | |

### 2.2 `roles`

| Column | Type | Constraints |
|--------|------|-------------|
| `id` | SERIAL | PK |
| `name` | VARCHAR(50) | UNIQUE, NOT NULL |

### 2.3 `user_roles`

| Column | Type | Constraints |
|--------|------|-------------|
| `user_id` | UUID | FK → users.id |
| `role_id` | INTEGER | FK → roles.id |

### 2.4 `emergency_contacts`

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | UUID | PK | |
| `user_id` | UUID | FK → users.id, NOT NULL | |
| `name` | VARCHAR(255) | NOT NULL | |
| `phone_number` | VARCHAR(20) | NOT NULL | |
| `relationship` | VARCHAR(100) | NULLABLE | |
| `is_primary` | BOOLEAN | DEFAULT FALSE | |
| `created_at` | TIMESTAMP WITH TIME ZONE | NOT NULL | |

### 2.5 `crime_incidents`

> **Critical:** See `docs/data-quality.md` for location precision rules.

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | UUID | PK | |
| `crime_type` | VARCHAR(100) | NOT NULL | e.g., theft, assault |
| `crime_subtype` | VARCHAR(100) | NULLABLE | |
| `severity` | INTEGER | NOT NULL, CHECK (1–5) | 1=minor, 5=severe |
| `incident_datetime` | TIMESTAMP WITH TIME ZONE | NULLABLE | From source; may be date-only |
| `incident_date` | DATE | NULLABLE | When time unknown |
| `source` | VARCHAR(255) | NOT NULL | Dataset name/version |
| `latitude` | DOUBLE PRECISION | NULLABLE | Only when legitimately available |
| `longitude` | DOUBLE PRECISION | NULLABLE | Only when legitimately available |
| `geometry` | GEOMETRY(Point, 4326) | NULLABLE | PostGIS point; only when legitimate |
| `location_precision` | VARCHAR(30) | NOT NULL | See precision taxonomy |
| `location_confidence` | FLOAT | NULLABLE, CHECK (0–1) | Quality estimate |
| `original_address` | TEXT | NULLABLE | Preserved if geocoded |
| `district` | VARCHAR(255) | NULLABLE | |
| `locality` | VARCHAR(255) | NULLABLE | |
| `police_station_name` | VARCHAR(255) | NULLABLE | |
| `city` | VARCHAR(255) | NULLABLE | |
| `state` | VARCHAR(255) | NULLABLE | |
| `country` | VARCHAR(100) | DEFAULT 'India' | |
| `raw_source_data` | JSONB | NULLABLE | Original source record |
| `created_at` | TIMESTAMP WITH TIME ZONE | NOT NULL | |

**Location Precision ENUM:**
- `EXACT_COORDINATE` — GPS-quality from source
- `ADDRESS_GEOCODED` — geocoded from real address
- `LOCALITY` — locality known only
- `POLICE_STATION` — police station known only
- `DISTRICT` — district known only
- `UNKNOWN` — no usable geographic info

**Indexes:**
- `GIST(geometry)` — spatial index
- `BTREE(incident_datetime)` — temporal queries
- `BTREE(location_precision)` — filter by precision
- `BTREE(crime_type, severity)` — type/severity queries

### 2.6 `road_segments`

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | UUID | PK | |
| `osm_way_id` | BIGINT | NULLABLE | OpenStreetMap way ID |
| `geometry` | GEOMETRY(LineString, 4326) | NOT NULL | Road geometry |
| `start_node_id` | BIGINT | NOT NULL | OSM node |
| `end_node_id` | BIGINT | NOT NULL | OSM node |
| `start_point` | GEOMETRY(Point, 4326) | NOT NULL | |
| `end_point` | GEOMETRY(Point, 4326) | NOT NULL | |
| `length_meters` | FLOAT | NOT NULL | |
| `road_type` | VARCHAR(50) | NULLABLE | OSM highway tag |
| `road_name` | VARCHAR(255) | NULLABLE | |
| `is_lit` | BOOLEAN | NULLABLE | From OSM lit tag |
| `max_speed` | INTEGER | NULLABLE | km/h |
| `is_oneway` | BOOLEAN | DEFAULT FALSE | |
| `walkable` | BOOLEAN | DEFAULT TRUE | |
| `estimated_walk_time_s` | FLOAT | NULLABLE | seconds |
| `created_at` | TIMESTAMP WITH TIME ZONE | NOT NULL | |
| `updated_at` | TIMESTAMP WITH TIME ZONE | NOT NULL | |

**Indexes:**
- `GIST(geometry)` — spatial queries
- `GIST(start_point)`, `GIST(end_point)` — nearest node queries
- `BTREE(osm_way_id)` — OSM lookups

### 2.7 `community_reports`

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | UUID | PK | |
| `user_id` | UUID | FK → users.id, NOT NULL | Reporter identity (private) |
| `report_type` | VARCHAR(50) | NOT NULL | harassment, poor_lighting, etc. |
| `description` | TEXT | NULLABLE | Max 1000 chars, sanitized |
| `latitude` | DOUBLE PRECISION | NULLABLE | Reporter's confirmed location |
| `longitude` | DOUBLE PRECISION | NULLABLE | Reporter's confirmed location |
| `geometry` | GEOMETRY(Point, 4326) | NULLABLE | Point geometry when coordinates present |
| `location_precision` | VARCHAR(30) | DEFAULT 'UNKNOWN' | EXACT_COORDINATE, UNKNOWN |
| `road_segment_id` | UUID | FK → road_segments.id, NULLABLE | Nearest segment |
| `status` | VARCHAR(20) | DEFAULT 'PENDING' | PENDING, VERIFIED, REJECTED |
| `verified_by` | UUID | FK → users.id, NULLABLE | Moderator / Admin |
| `verified_at` | TIMESTAMP WITH TIME ZONE | NULLABLE | |
| `verification_note` | TEXT | NULLABLE | Audit note |
| `incident_datetime` | TIMESTAMP WITH TIME ZONE | NULLABLE | When it happened (@PastOrPresent) |
| `created_at` | TIMESTAMP WITH TIME ZONE | NOT NULL | Submission timestamp |

**Indexes:**
- `GIST(geometry)` — nearby reports
- `BTREE(status)` — filter by status
- `BTREE(user_id)` — user's reports
- `BTREE(report_type)` — filter by type

### 2.8 `safe_zones`

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | UUID | PK | |
| `name` | VARCHAR(255) | NOT NULL | |
| `zone_type` | VARCHAR(50) | NOT NULL | POLICE_STATION, HOSPITAL, PHARMACY, PUBLIC_TRANSPORT, OTHER |
| `latitude` | DOUBLE PRECISION | NOT NULL | |
| `longitude` | DOUBLE PRECISION | NOT NULL | |
| `geometry` | GEOMETRY(Point, 4326) | NOT NULL | |
| `address` | TEXT | NULLABLE | |
| `phone_number` | VARCHAR(20) | NULLABLE | |
| `operating_hours` | VARCHAR(255) | NULLABLE | |
| `osm_node_id` | BIGINT | NULLABLE | OpenStreetMap source |
| `is_24h` | BOOLEAN | NULLABLE | |
| `source` | VARCHAR(100) | NULLABLE | osm, manual, etc. |
| `created_at` | TIMESTAMP WITH TIME ZONE | NOT NULL | |
| `updated_at` | TIMESTAMP WITH TIME ZONE | NOT NULL | |

**Indexes:**
- `GIST(geometry)` — nearest safe zones

### 2.9 `route_requests`

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | UUID | PK | |
| `user_id` | UUID | FK → users.id, NULLABLE | Null = anonymous |
| `origin_lat` | DOUBLE PRECISION | NOT NULL | |
| `origin_lon` | DOUBLE PRECISION | NOT NULL | |
| `destination_lat` | DOUBLE PRECISION | NOT NULL | |
| `destination_lon` | DOUBLE PRECISION | NOT NULL | |
| `origin_address` | TEXT | NULLABLE | |
| `destination_address` | TEXT | NULLABLE | |
| `travel_mode` | VARCHAR(20) | DEFAULT 'WALKING' | |
| `departure_time` | TIMESTAMP WITH TIME ZONE | NULLABLE | |
| `route_preference` | VARCHAR(20) | NULLABLE | |
| `requested_at` | TIMESTAMP WITH TIME ZONE | NOT NULL | |

### 2.10 `route_results`

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | UUID | PK | |
| `request_id` | UUID | FK → route_requests.id | |
| `route_mode` | VARCHAR(20) | NOT NULL | FASTEST, BALANCED, SAFEST |
| `geometry` | GEOMETRY(LineString, 4326) | NOT NULL | |
| `distance_meters` | FLOAT | NOT NULL | |
| `duration_seconds` | FLOAT | NOT NULL | |
| `safety_score` | FLOAT | NULLABLE | 0–1 |
| `risk_level` | VARCHAR(20) | NULLABLE | |
| `explanation` | JSONB | NULLABLE | SHAP-based |
| `created_at` | TIMESTAMP WITH TIME ZONE | NOT NULL | |

### 2.11 `weather_observations`

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | UUID | PK | |
| `latitude` | DOUBLE PRECISION | NOT NULL | |
| `longitude` | DOUBLE PRECISION | NOT NULL | |
| `geometry` | GEOMETRY(Point, 4326) | NOT NULL | |
| `observed_at` | TIMESTAMP WITH TIME ZONE | NOT NULL | |
| `temperature_c` | FLOAT | NULLABLE | |
| `precipitation_mm` | FLOAT | NULLABLE | |
| `visibility_km` | FLOAT | NULLABLE | |
| `weather_code` | INTEGER | NULLABLE | WMO code |
| `is_night` | BOOLEAN | NULLABLE | |
| `source` | VARCHAR(50) | DEFAULT 'open-meteo' | |
| `fetched_at` | TIMESTAMP WITH TIME ZONE | NOT NULL | |

### 2.12 `feature_snapshots`

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | UUID | PK | |
| `road_segment_id` | UUID | FK → road_segments.id | |
| `snapshot_time` | TIMESTAMP WITH TIME ZONE | NOT NULL | When features were computed |
| `hour_of_day` | INTEGER | NULLABLE | 0–23 |
| `day_of_week` | INTEGER | NULLABLE | 0–6 |
| `crime_density_500m` | FLOAT | NULLABLE | KDE — only from precision-valid data |
| `crime_density_1km` | FLOAT | NULLABLE | |
| `severity_weighted_density` | FLOAT | NULLABLE | |
| `recent_crime_density` | FLOAT | NULLABLE | Last 30 days |
| `night_crime_density` | FLOAT | NULLABLE | |
| `lighting_score` | FLOAT | NULLABLE | Proxy 0–1 |
| `activity_proxy` | FLOAT | NULLABLE | Estimated pedestrian density |
| `nearest_police_m` | FLOAT | NULLABLE | Meters |
| `nearest_hospital_m` | FLOAT | NULLABLE | Meters |
| `nearest_transport_m` | FLOAT | NULLABLE | Meters |
| `weather_precipitation` | FLOAT | NULLABLE | |
| `weather_visibility` | FLOAT | NULLABLE | |
| `verified_report_count` | INTEGER | DEFAULT 0 | |
| `features_json` | JSONB | NULLABLE | Full feature vector |
| `precision_notes` | TEXT | NULLABLE | Data quality notes |
| `created_at` | TIMESTAMP WITH TIME ZONE | NOT NULL | |

### 2.13 `audit_logs`

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | UUID | PK | |
| `user_id` | UUID | NULLABLE | FK → users.id |
| `action` | VARCHAR(100) | NOT NULL | e.g., USER_REGISTERED |
| `entity_type` | VARCHAR(100) | NULLABLE | |
| `entity_id` | UUID | NULLABLE | |
| `ip_address` | VARCHAR(50) | NULLABLE | |
| `details` | JSONB | NULLABLE | |
| `created_at` | TIMESTAMP WITH TIME ZONE | NOT NULL | |

---

## 3. Key Spatial Queries

### Nearby Safe Zones
```sql
SELECT *, ST_Distance(geometry::geography, ST_MakePoint(:lon, :lat)::geography) AS distance_m
FROM safe_zones
WHERE ST_DWithin(geometry::geography, ST_MakePoint(:lon, :lat)::geography, :radius_meters)
ORDER BY distance_m
LIMIT :limit;
```

### Crime Density Near Segment (Exact Coordinates Only)
```sql
SELECT COUNT(*) as crime_count,
       SUM(severity) as severity_sum
FROM crime_incidents
WHERE location_precision IN ('EXACT_COORDINATE', 'ADDRESS_GEOCODED')
  AND ST_DWithin(geometry::geography, :segment_midpoint::geography, 500);
```

### Nearby Community Reports
```sql
SELECT *
FROM community_reports
WHERE status = 'VERIFIED'
  AND ST_DWithin(geometry::geography, ST_MakePoint(:lon, :lat)::geography, :radius_meters)
ORDER BY created_at DESC;
```

---

## 4. Flyway Migration Order

| Version | Description |
|---------|-------------|
| V1__create_extensions.sql | PostGIS, uuid-ossp |
| V2__create_users.sql | users, roles, user_roles |
| V3__create_emergency_contacts.sql | emergency_contacts |
| V4__create_crime_incidents.sql | crime_incidents + indexes |
| V5__create_road_segments.sql | road_segments + indexes |
| V6__create_community_reports.sql | community_reports + indexes |
| V7__create_safe_zones.sql | safe_zones + indexes |
| V8__create_route_tables.sql | route_requests, route_results |
| V9__create_weather.sql | weather_observations |
| V10__create_feature_snapshots.sql | feature_snapshots |
| V11__create_audit_logs.sql | audit_logs |
| V12__insert_roles.sql | INSERT default roles |

---

*SafePath AI — database.md — 2026-10-08*
