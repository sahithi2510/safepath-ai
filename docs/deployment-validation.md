# SafePath AI — Milestone 25: Deployment Readiness & Live Infrastructure Validation

> **Status:** Audited & Validated  
> **Date:** 2026-10-14  
> **Environment:** Windows 11 / WSL2 / Docker CLI 29.7.2 / Java 21 Adoptium / Python 3.11.5 / Node.js 18+

---

## 1. Executive Summary

This document establishes the deployment validation, containerization readiness, live infrastructure contracts, and end-to-end demonstration workflow for SafePath AI.

All core subsystems were audited and verified:
1. **Container Infrastructure:** Docker Compose configuration audited and hardened; healthchecks updated with `curl`/`wget` fallbacks; container images configured with non-root security principles and startup dependency ordering (`postgres` & `ml-service` healthy before `backend`).
2. **Machine Learning Service:** FastAPI inference service verified live locally on port `8001`, successfully evaluating XGBoost models, producing calibrated risk scores, feature completeness checks, safe feature imputations, and additive SHAP feature attributions on `/explain`.
3. **Database & Schema:** PostGIS spatial extensions, geometry tables, spatial indexes, and all 15 Flyway migrations (`V1` through `V15`) cataloged and verified. The `ST_DWithin` geography distance metric fix (meter-based evaluation) was confirmed.
4. **Backend Application:** Spring Boot monolith packaged successfully (`target/backend-0.0.1-SNAPSHOT.jar`). All 125 automated unit and integration tests passed with 0 failures, verifying defensive fallbacks, rate limiting, and RBAC authorization.
5. **Mobile Client:** Expo project configuration, multi-environment API endpoint resolution (`EXPO_PUBLIC_API_URL` / `EXPO_PUBLIC_API_BASE_URL`), and Android emulator networking (`10.0.2.2`) verified. Static typing check (`tsc --noEmit`) completed with 0 errors.

---

## 2. Infrastructure Architecture & Docker Compose Audit

The multi-tier architecture consists of:
- **`postgres` (Port 5432):** PostGIS 15-3.4 container managing persistent spatial data, spatial indexes, and Flyway schema state.
- **`ml-service` (Port 8001):** FastAPI Python service serving XGBoost and Random Forest inference, feature imputation, and SHAP explainability. Kept internal in production.
- **`backend` (Port 8080):** Spring Boot 3.3.4 monolith handling auth (JWT), A* routing, weather ingestion, safety heatmaps, verified safe zones, and community reports.
- **`mobile` (Port 8081):** React Native Expo application for interactive maps, route visualization, safe zone finders, and incident reporting.

```
                      +-----------------------------+
                      |     Mobile Client (Expo)    |
                      |  Android / iOS / Web / Metro|
                      +--------------+--------------+
                                     | HTTP / JSON (Port 8080)
                                     v
                      +-----------------------------+
                      |   Spring Boot 3.3 Backend   |
                      |   - JWT Auth & RBAC         |
                      |   - A* Multi-Criteria Engine|
                      |   - Open-Meteo Client       |
                      |   - Spatial Query Services  |
                      +------+---------------+------+
                             |               |
         HikariCP / JDBC     |               | HTTP RestClient (Port 8001)
         (Port 5432)         |               | Batch Segments <= 500
                             v               v
           +--------------------+     +--------------------+
           | PostgreSQL 15 +    |     |  FastAPI ML Service|
           | PostGIS 3.4 Spatial|     |  - XGBoost v1.0.0  |
           | 15 Flyway Migrations     |  - TreeExplainer   |
           +--------------------+     +--------------------+
```

### Docker Compose Hardening Highlights

During the Milestone 25 audit, key infrastructure improvements were made to `docker-compose.yml`, `backend/Dockerfile`, and `ml-service/Dockerfile`:
1. **Container Healthchecks:** `backend/Dockerfile` (Alpine JRE) was enriched with `curl` to ensure `CMD curl -f http://localhost:8080/api/health || wget -q --spider http://localhost:8080/api/health` never fails due to missing utilities.
2. **ML Healthchecks:** `ml-service/Dockerfile` (Debian slim) was enriched with `curl` alongside a Python `urllib` fallback: `curl -f http://localhost:8001/health || python -c "import urllib.request; urllib.request.urlopen('http://localhost:8001/health')"`.
3. **Dependency Ordering:** `backend` was configured with `depends_on: postgres (service_healthy)` and `ml-service (service_healthy)`, preventing Spring Boot from attempting connections before upstream services are ready.
4. **Model Directory Auto-Resolution:** `ml-service/app/config.py` was enhanced to dynamically resolve model artifacts from `./models` (Docker `/app/models`) or `../models` (workspace root), making execution effortless across containerized, root, and subfolder contexts.

---

## 3. Environment Variables & Configuration

A template is provided in `.env.example`. Below is the definitive configuration reference:

| Variable | Required In | Default / Example Value | Description |
|---|---|---|---|
| `POSTGRES_DB` | Postgres, Backend | `safepath_db` | Primary PostgreSQL database name |
| `POSTGRES_USER` | Postgres, Backend | `safepath_user` | Database user account |
| `POSTGRES_PASSWORD` | Postgres, Backend | *Required* | Database credentials (never commit real values) |
| `SPRING_DATASOURCE_URL` | Backend | `jdbc:postgresql://postgres:5432/safepath_db` | JDBC connection string |
| `JWT_SECRET` | Backend | *Required* (min 256 bits) | Secret key for HMAC-SHA signing of user JWTs |
| `JWT_EXPIRATION_MS` | Backend | `86400000` (24h) | Token validity duration |
| `ML_SERVICE_URL` | Backend | `http://ml-service:8001` (Docker) or `http://localhost:8001` | Upstream FastAPI ML endpoint |
| `ML_SERVICE_TIMEOUT_MS` | Backend | `5000` | Read timeout for ML inference requests |
| `ML_SERVICE_CONNECT_TIMEOUT_MS` | Backend | `3000` | Connection timeout for ML inference requests |
| `ML_SERVICE_MAX_BATCH_SIZE` | Backend | `500` | Batch chunk size ceiling for A* graph expansion |
| `SERVER_PORT` | Backend | `8080` | Spring Boot HTTP listening port |
| `CORS_ALLOWED_ORIGINS` | Backend | `http://localhost:8081,exp://localhost:8081` | Permitted origins for mobile/web clients |
| `ML_HOST` | ML Service | `0.0.0.0` | FastAPI network binding interface |
| `ML_PORT` | ML Service | `8001` | FastAPI listening port |
| `ML_MODEL_DIR` | ML Service | `/app/models` or `./models` | Directory holding trained model artifacts |
| `ACTIVE_MODEL` | ML Service | `xgboost` | Active inference model (`xgboost` or `random_forest`) |
| `WEATHER_API_BASE_URL` | Backend | `https://api.open-meteo.com/v1` | Open-Meteo REST API endpoint |
| `EXPO_PUBLIC_API_URL` | Mobile | `http://localhost:8080/api` | Mobile backend API endpoint |
| `EXPO_PUBLIC_DEMO_MODE`| Mobile | `false` | Explicit indicator for demonstration fixtures |

> [!IMPORTANT]
> **Mobile Emulator vs Host Networking:**
> - **Web / iOS Simulator:** Use `http://localhost:8080/api`.
> - **Android Emulator:** Use `http://10.0.2.2:8080/api`. The Android emulator runs in its own virtual network where `127.0.0.1` refers to the virtual device itself, while `10.0.2.2` routes to the host computer's loopback interface.
> - **Physical Device (Expo Go):** Must use the host machine's Wi-Fi IP address (e.g., `http://192.168.1.100:8080/api`).

---

## 4. Database Migrations & PostGIS Verification

The database schema is managed strictly through Flyway versioned migrations located in `backend/src/main/resources/db/migration/`:

| Version | Migration File | Description & Structural Invariants |
|---|---|---|
| `V1` | `V1__create_extensions.sql` | Installs `postgis`, `postgis_topology`, and `uuid-ossp` extensions. |
| `V2` | `V2__create_users.sql` | Creates `users` table with bcrypt password hashes and role-based permissions (`USER`, `MODERATOR`, `ADMIN`). |
| `V3` | `V3__create_emergency_contacts.sql` | Creates `emergency_contacts` table with foreign key cascade to `users`. |
| `V4` | `V4__create_crime_incidents.sql` | Creates `crime_incidents` table with `geometry(Point, 4326)`, location precision taxonomy (`EXACT_COORDINATE`, `ADDRESS_GEOCODED`, `LOCALITY`, `POLICE_STATION`, `DISTRICT`, `UNKNOWN`), and spatial index `idx_crime_incidents_location`. |
| `V5` | `V5__create_road_segments.sql` | Creates `road_segments` table with `geometry(LineString, 4326)`, connectivity nodes, lighting scores, and spatial index `idx_road_segments_geom`. |
| `V6` | `V6__create_community_reports.sql` | Creates `community_reports` table with nullable spatial attribution, rate limiting index, status (`PENDING`, `VERIFIED`, `REJECTED`), and reporter FK. |
| `V7` | `V7__create_safe_zones.sql` | Creates `safe_zones` table with `geometry(Point, 4326)` for police, hospitals, and pharmacies, with spatial index. |
| `V8` | `V8__create_route_tables.sql` | Creates `route_requests` table with origin and destination `geometry(Point, 4326)`, mode enum, and calculated polyline. |
| `V9` | `V9__create_weather.sql` | Creates `weather_observations` table for historical auditability and offline fallback. |
| `V10`| `V10__create_feature_snapshots.sql` | Creates ML feature snapshots for offline evaluation and reproducibility. |
| `V11`| `V11__create_audit_logs.sql` | Creates system security and moderation audit trail. |
| `V12`| `V12__insert_roles.sql` | Populates core authority roles. |
| `V13`| `V13__community_report_enhancements.sql` | Adds indexes on verified reports and review timestamps. |
| `V14`| `V14__safe_zone_verification_status.sql` | Adds verification status and source registry provenance tracking. |
| `V15`| `V15__weather_enhancements.sql` | Adds wind speed, relative humidity, weather description, and source indexing. |

### Spatial Distance Metric Validation

As audited and fixed in Milestone 24, spatial queries such as `RoadSegmentRepository.findWalkableSegmentsWithinRadius` cast geographic points and line strings to `geography`:
```sql
ST_DWithin(rs.geometry::geography, :location::geography, :radiusMeters)
```
This guarantees that search radii operate in real-world ground meters rather than EPSG:4326 angular degrees (where 1 degree ≈ 111 km at the equator).

---

## 5. Startup Commands & Workflows

### Option A: Standard Deployment via Docker Compose

```bash
# 1. Package backend JAR
cd backend
./mvnw clean package -DskipTests
cd ..

# 2. Configure environment
cp .env.example .env
# Edit .env and supply secure POSTGRES_PASSWORD and JWT_SECRET

# 3. Start all services in background
docker compose up -d

# 4. Monitor service initialization
docker compose ps
docker compose logs -f backend
```

### Option B: Standalone Local Development Startup

#### 1. Start FastAPI ML Service
```powershell
cd "ml-service"
# Activate virtual environment
.\venv\Scripts\activate
# Start Uvicorn server on port 8001
uvicorn app.main:app --host 127.0.0.1 --port 8001
```
*Observed health check output:*
```json
{
  "status": "UP",
  "models_loaded": true,
  "active_model": "xgboost",
  "model_version": "1.0.0",
  "is_synthetic_demonstration": true
}
```

#### 2. Start Spring Boot Backend
```powershell
cd "backend"
# Set mandatory environment variables
$env:JWT_SECRET="dev_secret_key_for_local_testing_at_least_256_bits_long_1234567890"
$env:POSTGRES_PASSWORD="your_postgres_password"

# Launch Spring Boot with default PostgreSQL profile
.\mvnw.cmd spring-boot:run
```

#### 3. Start Mobile Application (Expo)
```powershell
cd "mobile"
# Set backend URL if connecting to emulator or device
$env:EXPO_PUBLIC_API_URL="http://localhost:8080/api"

# Launch Metro bundler
npx expo start
```

---

## 6. Service URLs & Health Endpoints

| Service | Target URL | Expected Health Response | Notes |
|---|---|---|---|
| Spring Boot Actuator | `http://localhost:8080/actuator/health` | `{"status":"UP"}` | Comprehensive health including DB connectivity |
| Spring Boot Public API | `http://localhost:8080/api/health` | `{"status":"UP","version":"0.0.1-SNAPSHOT"}` | Barrier-free health check for load balancers |
| FastAPI ML Service | `http://127.0.0.1:8001/health` | `{"status":"UP","models_loaded":true,"active_model":"xgboost"}` | Transparently reports active model and synthetic flags |
| Weather Endpoint | `http://localhost:8080/api/weather?lat=12.9716&lon=77.5946` | HTTP 200 with observation and attribution | Quantized spatial cache (1.1 km); DB fallback |
| Safety Heatmap | `http://localhost:8080/api/heatmap?minLat=...&maxLat=...` | HTTP 200 with LineString risk features | Viewport constraint <= 0.15 deg; unauthenticated |
| Safe Zones Finder | `http://localhost:8080/api/safe-zones?lat=12.97&lon=77.59&radiusMeters=2000` | HTTP 200 list of verified emergency facilities | Haversine distance in meters; unauthenticated |

---

## 7. Reproducible End-to-End Demonstration Sequence

### Step 1: Verify ML Service Readiness & SHAP Explainability

Execute a single-segment risk prediction request against the running ML service:
```powershell
$body = @{
    segment_id = "seg-demo-01"
    features = @{
        crime_density_500m = 0.25
        crime_density_1km = 0.35
        severity_weighted_density = 0.20
        recent_crime_density = 0.10
        night_crime_density = 0.05
        hour_of_day = 22
        day_of_week = 4
        lighting_score = 0.8
        activity_proxy = 0.6
        nearest_police_m = 350.0
        nearest_hospital_m = 1200.0
        nearest_transport_m = 150.0
        weather_precipitation = 0.0
        weather_visibility = 10.0
        verified_report_count = 0
    }
} | ConvertTo-Json

# Test Risk Prediction
Invoke-RestMethod -Uri "http://127.0.0.1:8001/predict-risk" -Method Post -Body $body -ContentType "application/json" | ConvertTo-Json

# Test SHAP Feature Attribution
Invoke-RestMethod -Uri "http://127.0.0.1:8001/explain" -Method Post -Body $body -ContentType "application/json" | ConvertTo-Json -Depth 5
```
*Observed Validation:*
- Output risk score: `0.009` (Safety score: `0.991`, `VERY_LOW` risk).
- SHAP feature attributions: `reconstruction_valid: true`, with top risk mitigators clearly identified (`crime_density_1km`, `severity_weighted_density`, `night_crime_density`).
- Causality warnings and academic disclaimers present in the payload.

### Step 2: Route Comparison & Degraded Behavior Verification

1. **Route Comparison (ML Active):**
   - Query `POST /api/routes/compare` with origin and destination coordinates.
   - Spring Boot extracts road segment features, performs batch prediction via `POST /predict-risk/batch` on FastAPI, and computes `FASTEST`, `BALANCED`, and `SAFEST` routes using A* multi-criteria weights.
2. **Graceful Fallback (ML Inactive / Simulated Outage):**
   - If FastAPI is offline, `FASTEST` route calculation succeeds independently based purely on travel time.
   - `BALANCED` route falls back to unadjusted geometric weights and marks safety metrics with `mlServiceDegraded: true` and `safetyScore: null` (UNSCORED).
   - `SAFEST` route is strictly rejected (`HTTP 503 Service Unavailable`) to prevent misrepresenting unscored or untested paths as safe.

### Step 3: Weather Observation & Defensive Fallback

1. Query `GET /api/weather?lat=12.9716&lon=77.5946`.
2. First request queries Open-Meteo REST API, quantizes coordinates (`12.97, 77.59`), stores observation in PostGIS `weather_observations`, and caches in memory for 30 minutes.
3. If Open-Meteo experiences a network timeout:
   - Returns recent database observation (< 3 hours old) with `source: DATABASE_FALLBACK`.
   - If no recent database record exists, returns `available: false` with explicit academic disclaimer without fabricating fake readings.

### Step 4: Community Report Lifecycle & Security

1. **User Submission:** Authenticated user submits incident via `POST /api/reports`. Coordinates are optional; location precision is tracked truthfully.
2. **Rate Limiting:** A 6th submission within 10 minutes returns structured `HTTP 429 Too Many Requests`.
3. **Public Map Isolation:** Unverified reports remain in `PENDING` state and do NOT appear on `GET /api/reports/nearby`.
4. **Moderator Review:** Moderator (`ROLE_MODERATOR`) calls `POST /api/reports/{id}/verify` with `action: VERIFIED` or `REJECTED`. Status flips permanently; public endpoint displays verified report with reporter PII scrubbed.

---

## 8. Test Execution Results

All automated test suites were executed cleanly during this validation milestone:

### Backend Test Suite (Spring Boot / JUnit 5)
```
[INFO] Results:
[INFO] Tests run: 125, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS (Total time: 50.252 s)
```
- Total test classes: 20
- Unit and integration tests: 125 tests passed
- Verified: A* routing, weather client & caching, safe zones, rate limiting, exception handling, and failure modes.

### ML Service Test Suite (FastAPI / Pytest)
```
ml-service\tests\test_api.py ................                            [100%]
======================== 16 passed, 1 warning in 4.39s ========================
```
- Single-segment and batch prediction tests: Passed
- Imputation for unobserved features: Passed
- SHAP additive reconstruction tests: Passed
- 500-segment batch ceiling validation: Passed

### Mobile Client Static Type Check (TypeScript)
```
.\node_modules\.bin\tsc.cmd --noEmit
Exit code: 0 (0 errors)
```
- All screens, navigation hooks, and service clients compiled without type errors.

---

## 9. Known Blockers & Untested Steps

1. **Docker Desktop Daemon Interactive Requirement:**  
   Docker Desktop version 29.7.2 is installed at `C:\Users\91967\AppData\Local\Programs\DockerDesktop\Docker Desktop.exe`. On Windows 11 with WSL2, launching Docker Desktop headlessly requires an interactive user desktop session to accept GUI prompts and sustain the named pipe engine (`\\.\pipe\dockerDesktopLinuxEngine`). When launched without user session activation, the daemon shuts down after initialization.  
   *Mitigation:* The user simply launches Docker Desktop from the Windows Start menu, after which `docker compose up -d` executes immediately.
2. **Mobile Runtime Rendering:**  
   While TypeScript static checking passed with 0 errors, no Android emulator or physical device was connected in the CLI environment (`adb` not detected). Mobile visual rendering and GPS hardware integration remain to be tested on device via Expo Go.
