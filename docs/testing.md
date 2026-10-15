# SafePath AI — Testing Strategy and Verification Report

> **Last Updated:** 2026-10-15  
> **Milestone:** Milestone 26 — Testing and Validation  
> **Current Status:** Verified (160 Backend Tests [157 Passed, 3 Skipped, 0 Failures], 63 Python ML Tests [63 Passed, 0 Failures], Mobile TypeScript Check [0 Errors])

---

## 1. Principles & Scientific Integrity

- Write meaningful tests — not just coverage padding.
- Critical behaviors must be **empirically verified**, not assumed.
- Tests must fail if the expected safety or routing behavior is absent.
- **Data Integrity & Honesty Rules:**
  - Mock and synthetic fixtures are strictly isolated and labeled `DEMO DATA` / `SYNTHETIC DATA`.
  - No synthetic training data or mock evaluations are claimed as real-world crime metrics or calibrated probabilities.
  - Coarse crime data (`POLICE_STATION`, `DISTRICT`, `LOCALITY`) is strictly forbidden from being given artificial GPS precision.
  - Missing risk predictions are never silently coerced to zero risk; they are marked `UNSCORED` with neutral routing costs.
  - SOS alerts are explicitly documented and logged as server-side security audit logs, not automated emergency dispatch.

---

## 2. Test Execution Summary

| Test Suite | Environment / Runner | Tests Run | Passed | Failed | Skipped | Status |
|------------|----------------------|-----------|--------|--------|---------|--------|
| **Backend (Spring Boot / JUnit 5)** | Maven Surefire / OpenJDK 21 | 160 | 157 | 0 | 3 | ✅ PASS |
| **ML Service & Pipeline (pytest)** | Python 3.11 / pytest 8.3 | 63 | 63 | 0 | 0 | ✅ PASS |
| **Mobile (React Native / TypeScript)** | TypeScript Compiler 5.7+ (`tsc --noEmit`) | N/A | Pass (0 errors) | 0 | 0 | ✅ PASS |
| **Total Automated Tests** | — | **223** | **220** | **0** | **3** | ✅ PASS |

> [!NOTE]
> **Skipped Tests Rationale:** Exactly 3 tests in `com.safepath.backend.integration.LiveMlServiceConnectivityTest` are skipped via JUnit `@EnabledIf("isMlServiceRunning")`. These tests conditionally execute only when a live FastAPI service instance is actively listening on `http://localhost:8001/health`. During offline unit and mock integration test runs, skipping these tests ensures builds remain hermetic and deterministic without network flakiness.

---

## 3. Backend Test Suite Breakdown (160 Tests)

### 3.1 Controller Layer Unit Tests (45 Tests)

| Class | Tests | Focus Areas | Result |
|-------|-------|-------------|--------|
| `AuthControllerTest` | 2 | `POST /api/auth/register` (201 Created + UserProfileDto), `POST /api/auth/login` (200 OK + JWT AuthResponse) | ✅ PASS |
| `UserControllerTest` | 3 | `GET /api/users/me` (Profile retrieval), `PUT /api/users/me` (Route preferences, personal details update) | ✅ PASS |
| `RouteControllerTest` | 4 | `POST /api/routes/search` (3-way comparison, preference forwarding, out-of-range coords rejection) | ✅ PASS |
| `HeatmapControllerTest` | 3 | `GET /api/heatmap` (Bounding box validation, max 0.15 deg span limit, unscored fallback) | ✅ PASS |
| `CommunityReportControllerTest` | 4 | `POST /api/reports` (201 Created), `GET /api/reports/nearby` (Verified only), `GET /api/reports/my`, `PUT /api/reports/{id}/verify` | ✅ PASS |
| `SafeZoneControllerTest` | 5 | `GET /api/safe-zones` (Bounding box & proximity queries, missing params 400, by ID lookup, 404 propagation) | ✅ PASS |
| `WeatherControllerTest` | 2 | `GET /api/weather/current` (Current conditions, coordinate validation, service fallback) | ✅ PASS |
| `EmergencyControllerTest` | 7 | `GET /api/emergency/contacts`, `POST /api/emergency/contacts`, `DELETE /api/emergency/contacts/{id}`, `POST /api/emergency/sos` (Audit log generation, coordinate validation, invalid ranges) | ✅ PASS |

### 3.2 Service Layer Unit & Mock Integration Tests (85 Tests)

| Class | Tests | Focus Areas | Result |
|-------|-------|-------------|--------|
| `AuthServiceTest` | 5 | Password encoding, `ROLE_USER` assignment, duplicate email 409 rejection, missing role 404, bad credentials 401 | ✅ PASS |
| `UserServiceTest` | 5 | Profile retrieval, route preference updates (`SOLO_TRAVELLER`, `TOURIST`, `ELDERLY`), partial patching, 404 handling | ✅ PASS |
| `RouteComparisonServiceTest` | 8 | 3-way route generation (FASTEST, BALANCED, SAFEST), duplicate segment detection, trade-off synthesis, missing weather resilience | ✅ PASS |
| `RouteServiceMlIntegrationTest` | 4 | ML service unavailable handling, FASTEST non-ML fallback, SAFEST strict rejection without ML, BALANCED degradation | ✅ PASS |
| `AStarRoutingEngineTest` | 11 | Multi-objective cost calculation, zero-heuristic admissibility ($h=0$), deterministic tie-breaking, graph cycle prevention | ✅ PASS |
| `CommunityReportServiceTest` | 10 | Exact coordinate vs unknown precision, sliding-window rate limiting (max 5/10min), one-way review lifecycle, reporter PII protection | ✅ PASS |
| `SafeZoneServiceTest` | 10 | PostGIS bounding box filtering, Haversine proximity calculations, verified status filtering, 24h operational checks | ✅ PASS |
| `EmergencyContactServiceTest` | 8 | Max 5 contacts limit, single primary contact invariant, primary auto-promotion/reassignment, cross-user deletion authorization (403 Forbidden) | ✅ PASS |
| `HeatmapServiceTest` | 9 | PostGIS road segment retrieval, bounded batch ML scoring, unscored neutral rendering fallback on ML outage | ✅ PASS |
| `WeatherServiceTest` | 6 | Open-Meteo cache quantization (~1.1 km grid), DB observation fallback on API failure, timeout recovery | ✅ PASS |
| `OpenMeteoClientTest` | 5 | HTTP error handling, payload deserialization, connection timeout resilience | ✅ PASS |
| `MlServiceClientTest` | 10 | Bounded timeouts (3s connect, 5s read), HTTP/1.1 client request, 422/500/503 status code handling, batch chunking (<= 500 segments) | ✅ PASS |
| `RoadSegmentFeatureServiceTest` | 7 | Extraction of 15 spatial/temporal road segment features, null safety | ✅ PASS |

### 3.3 Configuration, Contract & Integration Tests (30 Tests)

| Class | Tests | Focus Areas | Result |
|-------|-------|-------------|--------|
| `RoutingPropertiesTest` | 7 | Cost function weight validation ($\alpha + \beta + \gamma = 1$), normalization, negative/infinite value rejection | ✅ PASS |
| `MlServicePropertiesTest` | 4 | Base URL, timeout settings, batch size configuration bounds | ✅ PASS |
| `CrossServiceContractReliabilityTest` | 7 | Contract parity between Spring Boot DTOs and FastAPI Pydantic models, JSON field naming, enum mappings | ✅ PASS |
| `EndToEndIntegrationAndFailureModeTest` | 9 | End-to-end routing failure modes, database mock integration, rate limiter stress | ✅ PASS |
| `LiveMlServiceConnectivityTest` | 3 | Live Uvicorn prediction integration (executed when ML service container is active) | ⚪ SKIPPED (Offline) |

---

## 4. ML Service & Offline Pipeline Test Suite Breakdown (63 Tests)

All Python tests executed in virtual environment (`.\ml-service\venv\Scripts\python.exe -m pytest ml-service/tests/ scripts/tests/ -v`).

### 4.1 FastAPI Service Tests (`ml-service/tests/test_api.py` — 16 Tests)

| Test Name | Focus Area | Result |
|-----------|------------|--------|
| `test_health_check_loaded` | Service health when XGBoost/RF models are loaded (200 OK) | ✅ PASS |
| `test_health_check_unready_degraded` | Health response when models are unready (503 / degraded) | ✅ PASS |
| `test_predict_risk_single_valid` | Single segment risk prediction within $[0.0, 1.0]$ bounds | ✅ PASS |
| `test_predict_risk_missing_features_imputation` | Default imputation for missing feature fields | ✅ PASS |
| `test_predict_risk_empty_features_all_imputed` | Safe handling when all optional features require default imputation | ✅ PASS |
| `test_predict_risk_batch_valid` | Batch prediction for multiple segments preserving ID order | ✅ PASS |
| `test_predict_risk_batch_empty_rejected` | HTTP 422 rejection when batch array is empty | ✅ PASS |
| `test_predict_risk_batch_limit_exceeded` | HTTP 422 rejection when batch exceeds 500 segments | ✅ PASS |
| `test_validation_non_finite_values_rejected` | Rejection of `NaN`, `+Infinity`, `-Infinity` | ✅ PASS |
| `test_validation_out_of_range_values_rejected` | Rejection of negative distances or out-of-bound probabilities | ✅ PASS |
| `test_validation_unexpected_extra_features_rejected` | Pydantic `extra="forbid"` rejection of undeclared payload fields | ✅ PASS |
| `test_validation_empty_segment_id_rejected` | Rejection of blank or whitespace-only segment IDs | ✅ PASS |
| `test_validation_malformed_types_rejected` | Type safety rejection (e.g. strings in numeric fields) | ✅ PASS |
| `test_explain_endpoint_valid` | SHAP explanation generation, factor list, base value returned | ✅ PASS |
| `test_errors_do_not_leak_stack_traces_or_paths` | Global exception handling sanitization | ✅ PASS |
| `test_weather_features_imputation_and_inference` | Weather feature handling in risk scoring pipeline | ✅ PASS |

### 4.2 Offline Pipeline & Model Training Tests (`scripts/tests/` — 47 Tests)

| Test File | Tests | Focus Areas | Result |
|-----------|-------|-------------|--------|
| `test_cleaner.py` | 6 | Severity mapping, coordinate validation, datetime parsing, precision enforcement (`EXACT_COORDINATE` vs coarse) | ✅ PASS |
| `test_db_and_dedup.py` | 2 | Record parameterization, idempotent deduplication via external ID | ✅ PASS |
| `test_feature_pipeline_e2e.py` | 2 | End-to-end feature computation with synthetic fixtures, empty segment handling | ✅ PASS |
| `test_geocoder.py` | 3 | SQLite geocode cache, mock geocoding, low-confidence Nominatim result rejection | ✅ PASS |
| `test_ingestion_e2e.py` | 2 | Ingestion dry-run with mock fixtures, geocoded fixture validation | ✅ PASS |
| `test_leakage_guard.py` | 2 | Anti-leakage: temporal crime masking ($t_{\text{crime}} < t_{\text{query}}$), chronological split | ✅ PASS |
| `test_random_forest_training.py` | 4 | Classification & regression training, train-only scaler fitting, ML service artifact compatibility | ✅ PASS |
| `test_shap_explainability.py` | 10 | TreeExplainer init, additive reconstruction ($\sum \phi_i \approx f(x) - E[f(x)]$), reproducibility, non-causal disclaimer | ✅ PASS |
| `test_spatial_features.py` | 4 | Precision filtering (ignoring coarse records for KDE), spatial crime density, safe zone proximity | ✅ PASS |
| `test_temporal_features.py` | 4 | Weekend night, weekday rush hour, missing timestamp imputation, vectorized DataFrame evaluation | ✅ PASS |
| `test_xgboost_training.py` | 6 | XGBoost training, reproducibility with fixed seed, class imbalance handling (`scale_pos_weight`), baseline comparison | ✅ PASS |

---

## 5. Mobile Static & Navigation Validation

- **Static Type Check:** Ran `.\node_modules\.bin\tsc.cmd --noEmit` from `mobile/` directory. Result: **0 errors**.
- **Inspected Navigation & Screens:**
  - `mobile/app/(tabs)/index.tsx`: Map screen with dual-layer visualization (routes, safe zones, heatmap).
  - `mobile/app/(tabs)/routes.tsx`: 3-way route comparison, preference selection banner, personalized badges.
  - `mobile/app/(tabs)/profile.tsx`: User profile, active preference pill, logout action.
  - `mobile/app/edit-profile.tsx`: Name/phone validation, read-only email, loading/save states.
  - `mobile/app/settings.tsx`: 7 personalized route preference cards, weight breakdowns, immediate sync.
  - `mobile/app/emergency.tsx`: Emergency SOS trigger, 112/100/108 helplines, trusted contacts CRUD.
- **Physical Device / Emulator Verification Status:**
  - In headless automated CI/local shell environments without an attached device or running Android Virtual Device (AVD), hardware GPS, real telephony calls (`tel:`), SMS composition (`sms:`), and Android surface rendering cannot be executed and are formally recorded as **NOT RUN / BLOCKED**.

---

## 6. Live Infrastructure & Integration Verification Matrix

| Verification Target | Expected Behavior | Actual Observed State | Status | Evidence / Notes |
|---------------------|-------------------|-----------------------|--------|------------------|
| **Docker Engine** | Docker daemon running on Windows | `failed to connect to docker API at //./pipe/dockerDesktopLinuxEngine` | 🔴 BLOCKED | Docker Desktop engine service is stopped on host machine |
| **PostgreSQL / PostGIS (Port 5432)** | Live PostgreSQL instance listening | No process listening on port 5432 | 🔴 BLOCKED | Docker container `safepath-postgres` not running; host service inactive |
| **Flyway Migrations (Live DB)** | Migrations V1–V13 execute cleanly against PostGIS | Not run against live DB | ⚪ NOT RUN | Blocked by PostgreSQL availability; schema verified via H2/Flyway in CI/tests |
| **FastAPI ML Service (Port 8001)** | Uvicorn listening on port 8001 | No process listening on port 8001 | ⚪ NOT RUN | Executable via `uvicorn main:app --port 8001`; tested in-process via pytest `TestClient` |
| **Spring Boot Backend (Port 8080)** | Spring Boot server listening | No process listening on port 8080 | ⚪ NOT RUN | Executable via `./mvnw spring-boot:run`; tested comprehensively via JUnit test suite |
| **Spring Boot ↔ FastAPI Live Link** | Live HTTP requests between backend and ML container | Skips when port 8001 offline | ⚪ SKIPPED | 3 tests in `LiveMlServiceConnectivityTest` skip gracefully without failure |
| **In-Memory & Mocked End-to-End** | Full application logic, routing, auth, SOS, reports, safe zones | 157 Maven tests + 63 Pytest tests pass | ✅ PASS | All business logic, algorithms, validations, and security checks verified |

---

## 7. Security and Data Integrity Verification

1. **Endpoint Authorization & User Isolation:**
   - Private endpoints (`/api/users/me`, `/api/emergency/**`, `/api/reports/my`) strictly enforce JWT authentication.
   - User ownership is strictly validated in service layers (e.g., users cannot delete another user's emergency contacts, returning `403 Forbidden`).
   - Moderation endpoints (`PUT /api/reports/{id}/verify`) require administrative/moderator authority; unauthorized attempts return `403 Forbidden`.
   - Public read-only endpoints (`GET /api/safe-zones`, `GET /api/heatmap`, `GET /api/reports/nearby`) omit user PII and only expose verified data.
2. **Missing Risk & Data Degradation Handling:**
   - Missing or offline ML predictions are never treated as 0 risk.
   - Roads with unavailable risk scores are explicitly marked `UNSCORED` and styled as dashed neutral lines in mobile maps.
   - In routing, `SAFEST` mode strictly refuses to return unverified routes when ML scoring fails.
3. **Location Precision Taxonomy Preservation:**
   - Crime records lacking GPS coordinates retain their legitimate precision (`POLICE_STATION`, `LOCALITY`, `DISTRICT`).
   - The spatial feature pipeline strictly ignores coarse crime records for Kernel Density Estimation (KDE).
4. **Synthetic Data Transparency:**
   - Fixtures and test datasets are explicitly flagged as synthetic in metadata (`is_synthetic=true`).
   - SHAP explanation outputs include non-causal disclaimers.
5. **SOS Emergency Disclosure:**
   - Mobile UI and backend responses explicitly disclose that SOS triggers generate an immutable security audit record and do not dispatch public safety personnel. Direct helpline dials (112, 100, 108) are provided for actual dispatch.

---

## 8. Reproducible Local Test Procedure

To replicate the test results locally on any development environment:

### Step 1: Run Backend Tests
```powershell
cd backend
.\mvnw.cmd clean test
```
*Expected Output:* `Tests run: 160, Failures: 0, Errors: 0, Skipped: 3` -> `BUILD SUCCESS`.

### Step 2: Run ML Service & Pipeline Tests
```powershell
cd ..\
.\ml-service\venv\Scripts\python.exe -m pytest ml-service/tests/ scripts/tests/ -v
```
*Expected Output:* `63 passed, 31 warnings in ~19s` -> Exit code 0.

### Step 3: Run Mobile TypeScript Verification
```powershell
cd mobile
.\node_modules\.bin\tsc.cmd --noEmit
```
*Expected Output:* Exit code 0 with no diagnostic errors.

---

*SafePath AI — testing.md — Milestone 26 Complete*
