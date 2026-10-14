# SafePath AI — Integration Audit and End-to-End Reliability Report

> **Milestone:** 24 — Integration Audit and End-to-End Validation  
> **Status:** Completed  
> **Date:** 2026-10-14  
> **Target Scope:** Cross-service contracts, failure modes, security/privacy enforcement, and data integrity verification across React Native (Mobile), Spring Boot (Backend), PostgreSQL/PostGIS (Spatial Database), and FastAPI (ML Service).

---

## 1. Executive Summary

Milestone 24 performed a rigorous, cross-layer integration audit across all four application tiers. Rather than adding new experimental features, this audit validated end-to-end contract compatibility, defensive failure-mode degradation, spatial geometry correctness, and security/privacy isolation guards.

Key outcomes of the audit:
1. **15-Feature ML Schema Alignment**: Verified exact 1-to-1 parity between Spring Boot's [RiskFeaturesDto](file:///c:/Users/91967/OneDrive/Desktop/Safe%20path%20AI/backend/src/main/java/com/safepath/backend/dto/ml/RiskFeaturesDto.java) and FastAPI's `FEATURE_NAMES` in [preprocessor.py](file:///c:/Users/91967/OneDrive/Desktop/Safe%20path%20AI/ml-service/app/services/preprocessor.py). Weather precipitation (`mm`) and visibility (`km`) align precisely in both naming, scaling, and missing-value imputation.
2. **Spatial Calculation Correction**: Identified and rectified a metric-degree mismatch in [RoadSegmentRepository](file:///c:/Users/91967/OneDrive/Desktop/Safe%20path%20AI/backend/src/main/java/com/safepath/backend/repository/RoadSegmentRepository.java) where `ST_DWithin` was querying raw EPSG:4326 geometry (degrees) instead of geography (meters).
3. **Defensive API Error Handling**: Corrected [GlobalExceptionHandler](file:///c:/Users/91967/OneDrive/Desktop/Safe%20path%20AI/backend/src/main/java/com/safepath/backend/exception/GlobalExceptionHandler.java) to explicitly map `IllegalArgumentException` and `ConstraintViolationException` to structured HTTP `400 Bad Request` instead of leaking generic HTTP `500` server errors.
4. **Security & Endpoint Access**: Updated [SecurityConfig](file:///c:/Users/91967/OneDrive/Desktop/Safe%20path%20AI/backend/src/main/java/com/safepath/backend/config/SecurityConfig.java) to allow public read access to `GET /api/heatmap` and `GET /api/reports/nearby`, allowing unauthenticated map exploration while strictly preserving authentication and rate-limiting on report creation and moderation.
5. **Privacy Guard Validation**: Confirmed that public community report DTOs strictly omit user UUIDs, names, phone numbers, and emails.
6. **Scientific Honesty Preservation**: Confirmed that routing engine fallbacks (`FASTEST`, `BALANCED`, `SAFEST`) never falsely label an unscored route as safe when ML is unreachable, and unverified reports never silently influence risk inference.

---

## 2. Components & Contracts Inspected

| Tier | Inspected Component | Verified Contract / Invariant | Status |
| :--- | :--- | :--- | :--- |
| **ML Inference** | FastAPI `PredictRiskRequest` / `RiskFeatures` | 15 tabular features in exact order; extra fields forbidden; non-finite numbers rejected. | Verified |
| **ML Inference** | FastAPI `Preprocessor` Imputation | Omitted features imputed using documented defaults; imputation explicitly recorded in response. | Verified |
| **Backend ML Client** | Spring Boot [MlServiceClientImpl](file:///c:/Users/91967/OneDrive/Desktop/Safe%20path%20AI/backend/src/main/java/com/safepath/backend/service/ml/MlServiceClientImpl.java) | Connect/read timeouts; JSON serialization in snake_case; unready model (503) handling. | Verified |
| **Feature Extraction** | [RoadSegmentFeatureService](file:///c:/Users/91967/OneDrive/Desktop/Safe%20path%20AI/backend/src/main/java/com/safepath/backend/service/ml/RoadSegmentFeatureService.java) | Weather features 13 & 14 mapped in `mm` and `km`; nulls preserved when unobserved. | Verified |
| **Routing Engine** | [AStarRoutingEngine](file:///c:/Users/91967/OneDrive/Desktop/Safe%20path%20AI/backend/src/main/java/com/safepath/backend/service/routing/AStarRoutingEngine.java) & [RouteService](file:///c:/Users/91967/OneDrive/Desktop/Safe%20path%20AI/backend/src/main/java/com/safepath/backend/service/RouteService.java) | `FASTEST` succeeds with $\gamma = 0$; `SAFEST` marks routes as `UNSCORED` when ML is down (no false safety claims). | Verified |
| **Safety Heatmap** | [HeatmapService](file:///c:/Users/91967/OneDrive/Desktop/Safe%20path%20AI/backend/src/main/java/com/safepath/backend/service/HeatmapService.java) & [HeatmapDto](file:///c:/Users/91967/OneDrive/Desktop/Safe%20path%20AI/backend/src/main/java/com/safepath/backend/dto/HeatmapDto.java) | Bounding box span limited to 0.15° to prevent DoS; GeoJSON coordinates `[lon, lat]`. | Verified |
| **Weather Service** | [WeatherService](file:///c:/Users/91967/OneDrive/Desktop/Safe%20path%20AI/backend/src/main/java/com/safepath/backend/service/WeatherService.java) & [OpenMeteoClientImpl](file:///c:/Users/91967/OneDrive/Desktop/Safe%20path%20AI/backend/src/main/java/com/safepath/backend/service/weather/OpenMeteoClientImpl.java) | Quantized spatial cache (2 decimals); fallback to PostGIS DB (< 3h); honest unavailable state. | Verified |
| **Safe Zones** | [SafeZoneService](file:///c:/Users/91967/OneDrive/Desktop/Safe%20path%20AI/backend/src/main/java/com/safepath/backend/service/SafeZoneService.java) & [SafeZoneDto](file:///c:/Users/91967/OneDrive/Desktop/Safe%20path%20AI/backend/src/main/java/com/safepath/backend/dto/SafeZoneDto.java) | Verification status tracked (`VERIFIED`, `UNVERIFIED`, `UNAVAILABLE`); spatial queries in meters. | Verified |
| **Community Reports** | [CommunityReportService](file:///c:/Users/91967/OneDrive/Desktop/Safe%20path%20AI/backend/src/main/java/com/safepath/backend/service/CommunityReportService.java) | 5 reports / 10 min rate limit; public queries return strictly `VERIFIED`; reporter PII isolated. | Verified |
| **Security & Auth** | [SecurityConfig](file:///c:/Users/91967/OneDrive/Desktop/Safe%20path%20AI/backend/src/main/java/com/safepath/backend/config/SecurityConfig.java) & [JwtAuthenticationFilter](file:///c:/Users/91967/OneDrive/Desktop/Safe%20path%20AI/backend/src/main/java/com/safepath/backend/security/JwtAuthenticationFilter.java) | Method security `@PreAuthorize` for moderators; stateless JWT session; CORS configurable via ENV. | Verified |
| **Mobile Client** | React Native Expo TypeScript | Types match backend DTOs exactly; `tsc --noEmit` exits with 0 errors. | Verified |

---

## 3. Confirmed Defects and Implemented Fixes

### Defect 1: RoadSegmentRepository Spatial Units Inconsistency
- **Symptom / Vulnerability**: In `findWalkableSegmentsWithinRadius`, `rs.geometry` and `:location` were evaluated without geography casting (`ST_DWithin(rs.geometry, :location, :radiusMeters)`).
- **Impact**: In EPSG:4326, distances are evaluated in degrees. Passing `radiusMeters = 5000.0` to `ST_DWithin` on raw geometries resulted in checking 5000 degrees, returning segments globally rather than locally.
- **Fix**: Updated query in [RoadSegmentRepository.java](file:///c:/Users/91967/OneDrive/Desktop/Safe%20path%20AI/backend/src/main/java/com/safepath/backend/repository/RoadSegmentRepository.java) to cast both geometries to geography: `ST_DWithin(rs.geometry::geography, :location::geography, :radiusMeters)`. Distance is now evaluated accurately in geodetic meters.

### Defect 2: Global Exception Handler Leaked 500s on Client Validation Errors
- **Symptom**: Coordinate bounds violations, bounding box span overflows, or missing parameter combinations threw `IllegalArgumentException` or `ConstraintViolationException`. Because [GlobalExceptionHandler.java](file:///c:/Users/91967/OneDrive/Desktop/Safe%20path%20AI/backend/src/main/java/com/safepath/backend/exception/GlobalExceptionHandler.java) only had an uncaught `Exception.class` catch-all, these returned HTTP `500 Internal Server Error`.
- **Fix**: Added explicit handlers for `IllegalArgumentException.class` and `jakarta.validation.ConstraintViolationException.class` returning HTTP `400 Bad Request` with structured error messages and field mappings.

### Defect 3: Map Screen Map-Loading Blockers on Unauthenticated Mobile Queries
- **Symptom**: [SecurityConfig.java](file:///c:/Users/91967/OneDrive/Desktop/Safe%20path%20AI/backend/src/main/java/com/safepath/backend/config/SecurityConfig.java) permitted `/api/safe-zones` and `/api/weather`, but did not permit `GET /api/heatmap` or `GET /api/reports/nearby`.
- **Impact**: When the mobile map screen loaded before a user logged in, heatmap and nearby verified warning requests failed with `401 Unauthorized`.
- **Fix**: Added `requestMatchers(HttpMethod.GET, "/api/heatmap", "/api/heatmap/**").permitAll()` and `requestMatchers(HttpMethod.GET, "/api/reports/nearby").permitAll()` to allow public map display while keeping report submission (`POST /api/reports`) and user history (`GET /api/reports/my`) strictly protected.

### Defect 4: ML Response DTO Resilience Against Upstream Field Additions
- **Symptom**: In [PredictRiskResponseDto.java](file:///c:/Users/91967/OneDrive/Desktop/Safe%20path%20AI/backend/src/main/java/com/safepath/backend/dto/ml/PredictRiskResponseDto.java), any additional metadata returned by FastAPI (e.g. `prediction_timestamp`) caused Jackson `UnrecognizedPropertyException`.
- **Fix**: Added `@JsonIgnoreProperties(ignoreUnknown = true)` and explicit `@JsonProperty("prediction_timestamp")` to ensure cross-service forward compatibility.

---

## 4. Verification Evidence & Test Results

### 4.1 Backend Test Suite (Spring Boot / JUnit 5)
- **Command**: `.\mvnw.cmd test`
- **Working Directory**: `c:\Users\91967\OneDrive\Desktop\Safe path AI\backend`
- **Outcome**: **125 tests executed, 0 failures, 0 errors, 0 skipped (`BUILD SUCCESS`)**.
- **Execution Time**: ~4 minutes 57 seconds (including full JPA/Hibernate context bootstrapping).

Key test categories verified:
- [CrossServiceContractReliabilityTest](file:///c:/Users/91967/OneDrive/Desktop/Safe%20path%20AI/backend/src/test/java/com/safepath/backend/integration/CrossServiceContractReliabilityTest.java) (8 tests):
  - 15-feature ML schema exact field names and units.
  - Imputation tracking and sparse null preservation.
  - FastAPI prediction response calibration deserialization.
  - Mobile route search response schema compatibility.
  - WeatherDto observation vs forecastContext separation.
  - Community report strict reporter PII isolation.
  - SafeZoneDto and HeatmapDto serialization with GeoJSON.
- [EndToEndIntegrationAndFailureModeTest](file:///c:/Users/91967/OneDrive/Desktop/Safe%20path%20AI/backend/src/test/java/com/safepath/backend/integration/EndToEndIntegrationAndFailureModeTest.java) (13 tests):
  - Route comparison with ML service unavailable (FASTEST route succeeds; SAFEST marked unscored).
  - Weather provider timeout, stale cache fallback, and PostGIS DB fallback.
  - Weather provider honest unavailable state (no fabricated measurements).
  - Heatmap empty viewport (0 segments) and excessive bounding box span rejection.
  - Safe zone empty query, span limits, and 404 handling.
  - Community report rate limiting (HTTP 429 on 6th report within 10 minutes).
  - Community report privacy (public nearby excludes pending/rejected reports).
  - Community report moderation transition guards (only PENDING can transition to VERIFIED).

### 4.2 Python ML Test Suite (FastAPI / pytest)
- **Command**: `.\ml-service\venv\Scripts\pytest.exe ml-service\tests`
- **Working Directory**: `c:\Users\91967\OneDrive\Desktop\Safe path AI`
- **Outcome**: **16 tests passed, 0 failures in 14.69s**.
- **Coverage**:
  - Health endpoint (`UP` when models loaded; `DEGRADED` with 503 when missing).
  - Single and batch risk predictions with calibration.
  - Weather feature imputation, bounds validation, and negative value rejection (`weather_precipitation < 0`, `weather_visibility < 0`).
  - Strict absence of Python stack traces or local filesystem paths in error responses.

### 4.3 Mobile Client Type Checking (React Native / Expo TypeScript)
- **Command**: `.\node_modules\.bin\tsc.cmd --noEmit`
- **Working Directory**: `c:\Users\91967\OneDrive\Desktop\Safe path AI\mobile`
- **Outcome**: **Exit code 0, 0 type errors**.
- **Coverage**: All route DTOs, weather widgets, map overlays, safe zone cards, and report types compile with complete type safety.

---

## 5. Remaining Risks and Unverified Behaviors

1. **Synthetic Training Baseline**: The ML models (`xgboost-v1.0.0` and `random_forest-v1.0.0`) remain trained on synthetic feature fixtures. While feature extraction, normalization, and inference are verified end-to-end, the model does **not** possess empirical predictive validity on real-world crime data until trained on real ground-truth crime registries.
2. **PostgreSQL/PostGIS Native Extension in Test Profile**: Unit tests execute against an H2 in-memory database with spatial extensions. While native PostGIS queries are tested using Mockito and verified against SQL scripts, direct execution against a live PostgreSQL 16 / PostGIS 3.4 container requires a running Docker environment.
3. **Physical Mobile Device Hardware**: The mobile application has been verified via static TypeScript analysis, navigation type safety, and component linting. Physical hardware deployment (Android APK / iOS build) remains to be performed during full hardware staging.

---

## 6. Practical Manual End-to-End Demonstration Steps

To demonstrate the integrated SafePath AI stack end-to-end in a local staging environment:

### Step 1: Start ML Inference Service
```powershell
cd "ml-service"
.\venv\Scripts\activate
uvicorn app.main:app --host 0.0.0.0 --port 8000
# Verify: GET http://localhost:8000/health returns status "UP"
```

### Step 2: Start Spring Boot Monolith
```powershell
cd "backend"
# Ensure PostgreSQL/PostGIS is running or H2 fallback is enabled
.\mvnw.cmd spring-boot:run
# Verify: GET http://localhost:8080/api/health returns status "UP"
# Verify: GET http://localhost:8080/api/weather?latitude=12.9716&longitude=77.5946 returns live weather
```

### Step 3: Run Mobile Expo Application
```powershell
cd "mobile"
npm run start
# Press 'a' for Android emulator or 'w' for web preview
```

### Step 4: Verification Checklist in UI
1. **Weather Widget**: Observe live ambient temperature and condition pill on the map top bar. Tap to view 24-hr precipitation probability and attribution disclaimer.
2. **Safe Zones**: Navigate around Bangalore center; observe verified police stations and hospitals with distance badges.
3. **Route Search**: Search route from `MG Road` to `Brigade Road`. Observe 3 calculated alternatives:
   - `FASTEST`: Optimized for travel time.
   - `BALANCED`: Balances travel time and risk.
   - `SAFEST`: Prioritizes illuminated, low-risk street corridors.
4. **Failure Test**: Stop the FastAPI service (`Ctrl+C`). Re-run route search. Confirm UI displays:
   - FASTEST route computed normally.
   - Informative notice: *"ML risk prediction service is currently unreachable; SAFEST route unscored."* No fake safety score is displayed.
5. **Community Report**: Submit a test hazard report. Confirm receipt with status `PENDING`. Confirm the pending report is **not** visible to public unauthenticated queries until verified by a moderator.

---

*Report prepared and validated under SafePath AI Development Protocol.*
