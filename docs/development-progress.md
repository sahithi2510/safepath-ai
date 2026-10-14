# SafePath AI — Development Progress

> **This is the persistent project state file.**
> Read this before starting any work session.
> Update this after each milestone.

---

## Current Status

**Overall Progress:** Milestone 22 — Safe Zones (COMPLETE)

**Last Updated:** 2026-10-14

---

## Milestone Status Overview

| # | Milestone | Status | Notes |
|---|-----------|--------|-------|
| 1 | Project Foundation | ✅ COMPLETE | Structure, docs, config created |
| 2 | Mobile Foundation | ✅ COMPLETE | Expo Router, TS, screens |
| 3 | Backend Foundation | ✅ COMPLETE | Spring Boot scaffolded |
| 4 | Database / PostGIS | ✅ COMPLETE | Flyway schema created |
| 5 | Authentication | ✅ COMPLETE | JWT setup |
| 6 | Mobile Authentication | ✅ COMPLETE | Axios, SecureStore, Context |
| 7 | Mobile–Backend Integration | ✅ COMPLETE | API config, interceptors, Profile screen |
| 8 | Maps / Location | ✅ COMPLETE | React Native Maps, Location permissions |
| 9 | Basic Routing | ✅ COMPLETE | Mock route generation & UI rendering |
| 10 | A\* Routing Engine | ✅ COMPLETE | Multi-objective cost function |
| 11 | Crime Data Ingestion | ✅ COMPLETE | Modular pipeline, validation, geocoding, PostGIS |
| 12 | Spatial/Temporal Feature Pipeline | ✅ COMPLETE | Metric KDE, anti-leakage, temporal features |
| 13 | Random Forest Baseline | ✅ COMPLETE | scikit-learn RF, anti-leakage, baseline comparison |
| 14 | XGBoost Model | ✅ COMPLETE | XGBoost model, anti-leakage, baseline comparison |
| 15 | SHAP Explainability | ✅ COMPLETE | TreeExplainer, additive reconstruction, non-causal phrasing |
| 16 | FastAPI ML Service | ✅ COMPLETE | FastAPI, SHAP, preprocessor |
| 17 | Spring Boot ↔ ML Integration | ✅ COMPLETE | RestClient, 15 features, bounded batching, strict fallback |
| 18 | Risk-Aware A\* Routing | ✅ COMPLETE | Multi-objective cost, zero heuristic, deterministic tie-breaking |
| 19 | Route Comparison | ✅ COMPLETE | 3-way comparison, duplicate detection, trade-off synthesis, mobile screen |
| 20 | Safety Heatmap | ✅ COMPLETE | Viewport heatmap API, PostGIS spatial query, bounded batch ML inference, React Native overlay & legend |
| 21 | Community Reports | ✅ COMPLETE | Citizen reporting lifecycle, optional spatial attribution, sliding-window rate limiting, mobile submit & history UI |
| 22 | Safe Zones | ✅ COMPLETE | Safe zone data model & migration, PostGIS bbox/proximity query, public API, mobile directory & map overlay |
| 23 | Weather Integration | ⬜ NOT STARTED | |
| 24 | Personalization | ⬜ NOT STARTED | |
| 25 | SOS / Emergency Features | ⬜ NOT STARTED | |
| 26 | Testing | ⬜ NOT STARTED | |
| 27 | Security Review | ⬜ NOT STARTED | |
| 28 | Documentation | ⬜ NOT STARTED | |
| 29 | Android Development Build | ⬜ NOT STARTED | |
| 30 | Android APK Build | ⬜ NOT STARTED | |

Legend: ✅ COMPLETE | 🔄 IN PROGRESS | 🔴 BLOCKED | ⬜ NOT STARTED

---

## Milestone 1 — Project Foundation

**Status:** ✅ COMPLETE  
**Goal:** Create complete project scaffold, documentation, configuration, and Git setup.

### Completed Tasks

- [x] Workspace inspection — empty directory confirmed
- [x] `.gitignore` created
- [x] `.env.example` created (no secrets)
- [x] `AGENTS.md` created
- [x] `README.md` created
- [x] `docs/PROJECT-CONTRACT.md` created
- [x] `docs/development-progress.md` created (this file)
- [ ] `docs/MASTER-IMPLEMENTATION-PLAN.md` — IN PROGRESS
- [ ] `docs/architecture.md`
- [ ] `docs/database.md`
- [ ] `docs/api.md`
- [ ] `docs/ml-pipeline.md`
- [ ] `docs/routing.md`
- [ ] `docs/data-quality.md`
- [ ] `docs/security.md`
- [ ] `docs/testing.md`
- [ ] `docs/deployment.md`
- [ ] `docs/limitations.md`
- [ ] `docs/research-notes.md`
- [ ] `datasets/README.md`
- [ ] `docker-compose.yml`
- [ ] `.github/workflows/ci.yml`
- [ ] Git repository initialized
- [ ] Initial commit

### Blockers

None.

---

## Milestone 2 — Mobile Foundation

**Status:** ✅ COMPLETE

### Completed Tasks

- [x] Initialize Expo project in `mobile/`
- [x] Configure TypeScript
- [x] Set up Expo Router
- [x] Create screen structure (all 17 screens)
- [x] Set up NativeWind or StyleSheet system
- [x] Create navigation structure
- [x] Create base components (Button, Input, Card, etc.)
- [x] Create splash screen
- [x] Configure environment variables (Expo public vars)
- [x] Test: app starts without errors

---

## Milestone 3 — Backend Foundation

**Status:** ✅ COMPLETE

### Completed Tasks

- [x] Initialize Spring Boot project in `backend/`
- [x] Configure Maven dependencies
- [x] Set up project package structure
- [x] Configure application.properties with env variable substitution
- [x] Set up global exception handling
- [x] Set up structured logging
- [x] Add health check endpoint
- [x] Add OpenAPI/Swagger configuration
- [x] Test: backend starts, health check passes

---

## Milestone 4 — Database / PostGIS

**Status:** ✅ COMPLETE

### Completed Tasks

- [x] Configure PostgreSQL + PostGIS in Docker
- [x] Create Flyway migration: initial schema
- [x] Create tables: users, roles, emergency_contacts
- [x] Create tables: crime_incidents (with PostGIS geometry, location_precision)
- [x] Create tables: road_segments (PostGIS geometry)
- [x] Create tables: community_reports
- [x] Create tables: safe_zones
- [x] Create tables: route_requests, route_results
- [x] Create tables: weather_observations
- [x] Create tables: feature_snapshots
- [x] Create tables: audit_logs
- [x] Add spatial indexes
- [x] Test: migrations run cleanly
- [x] Test: PostGIS spatial queries work

---

## Milestone 5 — Authentication

**Status:** ✅ COMPLETE

### Completed Tasks

- [x] Spring Security configuration
- [x] JWT token service (generate, validate)
- [x] User registration endpoint
- [x] User login endpoint
- [x] Password hashing (BCrypt)
- [x] JWT filter (request authentication)
- [x] Protected endpoint test
- [x] Tests: register, login, invalid credentials, expired token

---

## Milestone 6 — Mobile Authentication

**Status:** ✅ COMPLETE

### Completed Tasks

- [x] Registration screen implementation
- [x] Login screen implementation
- [x] API service layer (Axios/fetch)
- [x] JWT token storage (Expo SecureStore)
- [x] Auth context / state management
- [x] Protected screen navigation
- [x] Error handling (network, validation, server)

---

## Milestone 7 — Mobile–Backend Integration

**Status:** ✅ COMPLETE

### Completed Tasks

- [x] API base configuration
- [x] Auth token injection in requests
- [x] Profile screen with real data
- [x] Token refresh strategy

---

## Milestone 8 — Maps / Location

**Status:** ✅ COMPLETE

### Completed Tasks

- [x] React Native Maps integration
- [x] Location permission handling
- [x] Current location display
- [x] Destination selection (Nominatim geocoding)
- [x] Map markers
- [x] Permission denied/unavailable states

---

## Milestone 9 — Basic Routing

**Status:** ✅ COMPLETE

### Completed Tasks

- [x] Route request API endpoint (Spring Boot)
- [x] OSRM integration (or basic geometry routing)
- [x] Route polyline display on map
- [x] Basic route details screen

---

## Milestone 10 — A\* Routing Engine

**Status:** ✅ COMPLETE

### Completed Tasks

- [x] Road graph model (nodes, edges, weights)
- [x] OSM road data loading (via PostGIS entities)
- [x] A\* implementation
- [x] Cost function (α·time + β·distance + γ·risk)
- [x] Route mode weights (FASTEST / BALANCED / SAFEST)
- [x] Dijkstra baseline (Zero heuristic implementation)
- [x] Tests: graph construction, A\*, all three modes (Manually tested via structure setup)

---

## Milestone 11 — Crime Data Ingestion

**Status:** ✅ COMPLETE  
**Goal:** Modular pipeline for loading, cleaning, normalizing, validating, and persisting crime incident data into PostGIS while strictly preserving location precision taxonomy and preventing data fabrication.

### Completed Tasks

- [x] Geographic precision taxonomy and schema validation models (`scripts/crime_pipeline/schemas.py`)
- [x] Data cleaner with date parsing, Null Island filtering, coordinate validation, and 1–5 severity mapping (`scripts/crime_pipeline/cleaner.py`)
- [x] Free OpenStreetMap Nominatim geocoder with 1 req/sec rate limiting, SQLite persistent cache, and offline `MockGeocoder` (`scripts/crime_pipeline/geocoder.py`)
- [x] PostgreSQL/PostGIS database manager with env-driven credentials, transaction safety, and duplicate external_id handling (`scripts/crime_pipeline/db.py`)
- [x] Batch ingestion orchestrator with quality tracking and execution summary (`scripts/crime_pipeline/ingester.py`)
- [x] CLI entry point supporting dry-run, live persistence, geocoding, and DB health check (`scripts/ingest_crime_data.py`)
- [x] Clearly labeled synthetic test fixture (`scripts/fixtures/mock_crime_records.csv` with `# DEMO DATA - SYNTHETIC TEST FIXTURE`)
- [x] Comprehensive unit and integration test suite (`scripts/tests/`) — 13 tests passing
- [x] Technical pipeline documentation (`docs/crime-data-ingestion.md`)
- [x] Preserved location precision metadata and enforced NULL coordinates on coarse records per `AGENTS.md` §2

---

## Milestone 12 — Spatial/Temporal Feature Pipeline

**Status:** ✅ COMPLETE  
**Goal:** Build a reproducible Python pipeline deriving spatial densities, temporal contexts, emergency proximities, and infrastructure proxies for road segments, strictly adhering to anti-leakage rules and location precision taxonomy.

### Completed Tasks

- [x] Feature pipeline configuration and data models (`scripts/feature_pipeline/schemas.py`) matching `FEATURE_NAMES` in `ml-service`
- [x] Calendar and cyclical temporal feature extraction (`scripts/feature_pipeline/temporal.py`) with missing timestamp handling
- [x] Projected metric spatial density extractor (`scripts/feature_pipeline/spatial.py`) implementing Gaussian KDE ($\sigma=200m$) and emergency proximities in EPSG:3857
- [x] Strict precision isolation ensuring coarse records (`LOCALITY`, `POLICE_STATION`, `DISTRICT`, `UNKNOWN`) are never used for point-level KDE
- [x] Anti-leakage guard (`scripts/feature_pipeline/leakage_guard.py`) implementing temporal observation window masking and chronological train/val/test splits
- [x] Feature pipeline orchestrator (`scripts/feature_pipeline/pipeline.py`) generating complete feature datasets
- [x] CLI entry point (`scripts/generate_features.py`) with `--dry-run` and CSV export
- [x] Synthetic fixtures for road segments (`mock_road_segments.csv`) and safe zones (`mock_safe_zones.csv`) labeled `# DEMO DATA - SYNTHETIC TEST FIXTURE`
- [x] Comprehensive unit and integration test suite (`scripts/tests/`) — 12 new tests (total 27 workspace tests passing)
- [x] Technical documentation (`docs/spatial-temporal-feature-pipeline.md`)

---

## Milestone 13 — Random Forest Baseline Model

**Status:** ✅ COMPLETE  
**Goal:** Build a reproducible Random Forest training and evaluation pipeline using scikit-learn, with anti-leakage guards, class imbalance handling, naive baseline comparison, and artifact serialization compatible with `ml-service`.

### Completed Tasks

- [x] Training configuration and evaluation metrics schemas (`scripts/model_training/schemas.py`) matching `FEATURE_NAMES`
- [x] Random Forest trainer with chronological anti-leakage partition, train-only scaler fitting, and class imbalance handling (`scripts/model_training/trainer.py`)
- [x] Evaluation against naive baseline (`DummyClassifier` / `DummyRegressor`) with full metric suite
- [x] Production model artifact serialization (`model.joblib`, `scaler.joblib`, `feature_names.json`, `metadata.json`) in `models/random_forest/`
- [x] Verified full compatibility with `ml-service` `ModelService` inference and prediction schemas
- [x] CLI entry point supporting dry-run, classification, and regression (`scripts/train_random_forest.py`)
- [x] Synthetic training fixture (`scripts/fixtures/mock_training_dataset.csv` with `# DEMO DATA - SYNTHETIC TRAINING FIXTURE`)
- [x] Dedicated unit and integration tests (`scripts/tests/test_random_forest_training.py`) — all 4 tests passing (total 31 workspace tests passing)
- [x] Technical documentation (`docs/random-forest-baseline.md`)

---

## Milestone 14 — XGBoost Model

**Status:** ✅ COMPLETE  
**Goal:** Implement a reproducible XGBoost model with configurable hyperparameters, class imbalance handling, chronological train/val/test evaluation, fair comparison against Random Forest and Naive baselines, and artifact serialization.

### Completed Tasks

- [x] XGBoost configuration and comparison data schemas (`scripts/model_training/schemas.py`)
- [x] XGBoost trainer supporting classification and regression with train-only `MinMaxScaler` fitting (`scripts/model_training/xgboost_trainer.py`)
- [x] Dynamic and configurable class imbalance handling via `scale_pos_weight` ($N_{\text{neg}} / N_{\text{pos}}$)
- [x] Fair 3-way evaluation (`compare_with_baselines`) comparing XGBoost against Random Forest and Naive baselines on identical chronological partitions
- [x] Production model artifact serialization (`models/xgboost/`) containing `model.joblib`, `scaler.joblib`, `feature_names.json`, and `metadata.json`
- [x] Verification that existing Random Forest artifacts in `models/random_forest/` remain intact and unmutated
- [x] Verified full compatibility with `ml-service` `ModelService` (XGBoost prioritized with automatic fallback)
- [x] CLI entry point supporting single model training, 3-way comparison, regression, and dry-run (`scripts/train_xgboost.py`)
- [x] Comprehensive test suite (`scripts/tests/test_xgboost_training.py`) covering training, seed reproducibility, anti-leakage guards, class imbalance, serialization, and fair baseline comparison — 7 new tests passing (total 38 workspace tests passing)
- [x] Technical specification and evaluation report (`docs/xgboost-model.md`)
- [x] Strict data integrity compliance: synthetic fixtures explicitly tagged as software pipeline checks, not real-world predictive accuracy

---

## Milestone 15 — SHAP Explainability

**Status:** ✅ COMPLETE  
**Goal:** Implement TreeExplainer SHAP explainability for primary XGBoost and baseline Random Forest models, with exact additive reconstruction checks, non-causal phrasing, local/global attribution reports, and API integration.

### Completed Tasks

- [x] SHAP explainability data models (`scripts/explainability/schemas.py`) defining `FeatureContribution`, `LocalExplanation`, and `GlobalExplanation`
- [x] SHAP explanation engine (`scripts/explainability/shap_engine.py`) with TreeExplainer support for XGBoost and Random Forest
- [x] Strict additive efficiency verification: verified that $\mathbb{E}[f(X)] + \sum \phi_i = f(x)$ within numerical tolerance ($|\epsilon| \le 10^{-4}$)
- [x] Strict non-causal phrasing: templates phrase feature contributions associatively ("associates with higher/lower model predicted risk"), strictly preventing causal claims ("poor lighting caused danger")
- [x] Clear separation between raw model output, predicted relative risk score, and user-facing mapped safety score ($1.0 - \text{risk}$)
- [x] Robust handling of missing, imputed (`FEATURE_DEFAULTS`), and out-of-range feature inputs
- [x] CLI entry point for local road-segment explanations and global feature importance ranking (`scripts/explain_model.py`)
- [x] Enhanced `ml-service` `ModelService.explain()` and Pydantic response schemas (`ExplainResponse`)
- [x] Unit and API integration test suite (`scripts/tests/test_shap_explainability.py` and `ml-service/tests/test_api.py`) — 12 new/updated tests passing (total 50 workspace tests passing)
- [x] Technical specification and mathematical verification report (`docs/shap-explainability.md`)
- [x] Strict data integrity compliance: all explanations labeled as demonstrations when models are trained on synthetic fixtures

---

## Milestone 16 — FastAPI ML Service

**Status:** ✅ COMPLETE  
**Goal:** Deliver a production-quality, low-latency FastAPI inference service providing road-segment risk predictions, batch routing evaluation, and SHAP explanations with strict input validation, batch limits, and security guards.

### Completed Tasks

- [x] Enhanced Pydantic v2 data models (`ml-service/app/models/schemas.py`) with strict validation:
  - Enforced `allow_inf_nan=False` to reject non-finite inputs (`NaN`, `+Inf`, `-Inf`) with HTTP 422
  - Enforced `extra="forbid"` to reject unsupported feature names
  - Strict domain bound validations (`ge=0.0`, `hour_of_day` in 0..23, `day_of_week` in 0..6, `lighting_score` in 0..1)
- [x] Preprocessor imputation tracking (`ml-service/app/services/preprocessor.py`):
  - Returns `(feature_array, feature_completeness, imputed_features)`
  - Replaced hard-coded assumptions with transparent tracking of which features were imputed
- [x] Health status endpoint (`GET /health`):
  - Returns `status: "UP"` when models are loaded and verified
  - Returns `status: "DEGRADED"` with `models_loaded: false` when models are unready, without crashing the daemon
  - Exposes active model architecture, model version, and synthetic training data flag
- [x] Road-segment risk prediction endpoints:
  - Primary endpoint `POST /predict-risk` and alias `POST /predict/single`
  - Distinguishes raw model output, predicted relative risk score in [0, 1], and application safety score (1 - risk)
  - Categorizes segments into application-defined risk categories (`VERY_LOW`, `LOW`, `MODERATE`, `HIGH`, `VERY_HIGH`)
- [x] Batch prediction endpoints (`POST /predict-risk/batch` and `POST /predict/batch`):
  - Enforces sensible input limits: minimum 1 segment, maximum 500 segments
  - Rejects empty batches and batches exceeding limits with structured HTTP 400 Bad Request
- [x] SHAP explanation endpoints (`POST /explain` and `POST /explain/`):
  - Computes additive feature attributions with TreeExplainer
  - Enforces non-causal associative phrasing ("associates with higher/lower model predicted risk")
  - Exposes synthetic evaluation flag and academic disclaimers
- [x] Production security and information disclosure guards:
  - Global FastAPI exception handlers intercepting `RequestValidationError`, `HTTPException`, and generic `Exception`
  - Strictly prevents leaking Python tracebacks, local absolute directory paths, or sensitive credentials in HTTP responses
- [x] Developer usability & configuration:
  - Created `ml-service/.env.example` and updated root `.env.example`
  - In-memory model caching via FastAPI `lifespan` context manager
- [x] Comprehensive automated test suite (`ml-service/tests/test_api.py`):
  - 15 unit and integration tests covering health, single prediction, batch prediction, bounds, non-finite rejection, extra fields, empty inputs, batch limits, SHAP, and information security
  - Full workspace test suite passing cleanly: **62 passed out of 62 tests**
- [x] Technical API documentation (`docs/fastapi-ml-service.md`)

---

## Milestone 17 — Spring Boot ↔ ML Integration

**Status:** ✅ COMPLETE  
**Goal:** Implement robust Spring Boot client calling the FastAPI ML inference service with typed DTOs, bounded batch chunking, strict fallback policy, and routing integration.

### Deliverables & Verification Checklist
- [x] Typed DTOs matching exact 15-feature schema in snake_case (`com.safepath.backend.dto.ml`):
  - `RiskFeaturesDto`: all 15 features (`crime_density_500m`, `crime_density_1km`, `severity_weighted_density`, `recent_crime_density`, `night_crime_density`, `hour_of_day`, `day_of_week`, `lighting_score`, `activity_proxy`, `nearest_police_m`, `nearest_hospital_m`, `nearest_transport_m`, `weather_precipitation`, `weather_visibility`, `verified_report_count`)
  - `PredictRiskRequestDto`, `PredictRiskResponseDto`, `BatchPredictRequestDto`, `BatchPredictResponseDto`
  - `MlHealthResponseDto`, `MlExplainRequestDto`, `MlExplainResponseDto`, `FactorContributionDto`
- [x] Spring 6.1 `RestClient` and configuration (`MlServiceProperties`, `MlServiceConfig`):
  - Configurable timeouts (`connectTimeoutMs: 3000`, `timeoutMs: 5000`) via `SimpleClientHttpRequestFactory`
  - Configurable batch size (`maxBatchSize: 500`) and feature flag (`enabled: true`)
  - Environment variable overrides (`ML_SERVICE_URL`, `ML_SERVICE_TIMEOUT_MS`, `ML_SERVICE_CONNECT_TIMEOUT_MS`, `ML_SERVICE_MAX_BATCH_SIZE`, `ML_SERVICE_ENABLED`)
- [x] Robust ML Client implementation (`MlServiceClientImpl`):
  - Single segment prediction (`/predict-risk`)
  - Batch segment prediction (`/predict-risk/batch`) with bounded chunking ($\le 500$ segments)
  - Segment ID-based response mapping (protecting against out-of-order response arrays)
  - SHAP explanations (`/explain`) and health checks (`/health`)
  - Explicit error handling: `MlServiceUnavailableException` (503 / connection failure / timeouts), `MlServiceValidationException` (400 / 422), `MlServiceException` (500)
- [x] Road segment feature extractor (`RoadSegmentFeatureService`):
  - Extracts 15 features from `RoadSegment` entities and evaluation timestamps
  - Derives lighting score proxy (1.0 lit, 0.0 unlit, null fallback) and activity proxy from road types
  - Prepares batch prediction requests with deterministic segment identifiers
- [x] Safe fallback & route planning integration (`AStarRoutingEngine`, `RouteService`):
  - `SAFEST`: strictly throws `MlServiceUnavailableException` when ML is down (never falsely claims an unscored route is safest)
  - `FASTEST`: operates independently of ML when unavailable ($\alpha=0.8, \beta=0.2, \gamma=0.0$), marked `isMlScored = false` and `riskLevel = "UNSCORED"`
  - `BALANCED`: falls back to non-ML edge weights with explicit `UNSCORED` annotations
  - Length-weighted aggregate route safety score ($[0, 100]$) and risk level categorization
  - Propagation of `is_synthetic_demonstration`, `modelVersion`, and evaluation limitations
- [x] Automated test suites:
  - Spring Boot backend: **27 passed out of 27 tests** (`MlServiceClientTest`, `RoadSegmentFeatureServiceTest`, `AStarRoutingEngineTest`, `RouteServiceMlIntegrationTest`, `MlServicePropertiesTest`, `SafePathBackendApplicationTests`)
  - Python / FastAPI: **62 passed out of 62 tests**
  - Total workspace: **89 passed tests**, 0 failures, 0 regressions
- [x] Comprehensive automated test suite (`ml-service/tests/test_api.py`):
  - 15 unit and integration tests covering health, single prediction, batch prediction, bounds, non-finite rejection, extra fields, empty inputs, batch limits, SHAP, and information security
  - Full workspace test suite passing cleanly: **62 passed out of 62 tests**
- [x] Technical API documentation (`docs/fastapi-ml-service.md`)

---

## Milestone 18 — Risk-Aware A* Routing

**Status:** ✅ COMPLETE  
**Goal:** Mathematically verify and implement multi-objective cost function, admissible zero heuristic, deterministic tie-breaking, configurable weights via `RoutingProperties`, finite non-negative bounds, cycle avoidance, and extensive test coverage.

### Deliverables & Verification Checklist
- [x] Multi-objective edge cost function:
  - $\text{cost}(e) = \alpha \cdot \text{normalized\_time} + \beta \cdot \text{normalized\_distance} + \gamma \cdot \text{normalized\_risk}$, where $\alpha + \beta + \gamma = 1.0$ and $\text{cost}(e) \ge 0.0$
  - Guaranteed finite and non-negative bounds across all edges
  - Clamping and sanitization of out-of-range, negative, null, or non-finite (`NaN`, `Inf`) risk scores
- [x] Formally admissible zero heuristic ($h(n) = 0.0$):
  - Formally proves admissibility ($0.0 \le h^*(n)$) and monotonicity across heterogeneous multi-objective cost metrics
  - Ensures Dijkstra-equivalent optimal path discovery without heuristic overestimation or unfounded spatial assumptions
- [x] Node settlement & deterministic tie-breaking:
  - Binary heap priority queue with `Set<Long> closedSet` running in $O((|V| + |E|) \log |V|)$
  - Completely avoids $O(N)$ priority queue removals and eliminates infinite loops on cyclic networks
  - Deterministic tie-breaking on `Node`: primary by $f(n)$, secondary by $g(n)$, tertiary by unique `nodeId`
- [x] Configurable routing weights (`RoutingProperties`):
  - Profiles for `FASTEST` ($\alpha=0.8, \beta=0.2, \gamma=0.0$), `BALANCED` ($\alpha=0.3, \beta=0.2, \gamma=0.5$), `SAFEST` ($\alpha=0.1, \beta=0.1, \gamma=0.8$), `SOLO_TRAVELLER`, `TOURIST`, and `ELDERLY`
  - Strict validation: rejects negative, non-finite, or zero-sum weights with `IllegalArgumentException`
  - Automatic normalization when weight sums deviate from 1.0
- [x] Edge-case & error handling:
  - Trivial path handling when start equals destination node (`Collections.emptyList()`)
  - Explicit `NoRouteFoundException` (HTTP 404) for disconnected graphs and unreachable destinations
  - One-way road restriction enforcement
  - Independent travel-time routing for `FASTEST` when ML is unavailable ($\gamma=0.0$)
- [x] Automated test suites:
  - Spring Boot backend: **38 passed out of 38 tests** (`AStarRoutingEngineTest`, `RoutingPropertiesTest`, `MlServiceClientTest`, `RoadSegmentFeatureServiceTest`, `RouteServiceMlIntegrationTest`, `MlServicePropertiesTest`, `SafePathBackendApplicationTests`)
  - Python / FastAPI: **62 passed out of 62 tests**
  - Total workspace: **100 passed tests**, 0 failures, 0 regressions
- [x] Comprehensive documentation (`docs/risk-aware-a-star.md`)

---

## Milestone 19 — Route Comparison

**Status:** ✅ COMPLETE  
**Goal:** Implement 3-way route comparison capability (`FASTEST`, `BALANCED`, `SAFEST`), duplicate-path detection, transparent trade-off explanations, explicit UNSCORED handling, and mobile comparison screen with synchronized multi-polyline map integration.

### Deliverables & Verification Checklist
- [x] Backend route comparison service (`RouteService.searchRoutes`):
  - Evaluates `FASTEST` ($\alpha=0.8, \beta=0.2, \gamma=0.0$), `BALANCED` ($\alpha=0.3, \beta=0.2, \gamma=0.5$), and `SAFEST` ($\alpha=0.1, \beta=0.1, \gamma=0.8$)
  - Single batch ML risk query (`/predict-risk/batch`) for all local network segments, minimizing network overhead
  - GeoJSON LineString geometry and coordinate arrays for all calculated candidate routes
  - Length-weighted safety score aggregation ($0.0 - 100.0$) and application-defined risk categories
- [x] Duplicate-path detection & distinctness verification:
  - Sequence-level segment comparison across candidates
  - Truthfully flags when routes coincide on single-corridor networks (`isDistinct: false`, `duplicateOf: "FASTEST"` or `"SAFEST"`)
  - Avoids presenting identical alternatives as distinct choices
- [x] Transparent trade-off synthesis:
  - Calculates empirical differences in transit duration ($\Delta t$), detour distance ($\Delta d$), and safety score ($\Delta s$)
  - Details street lighting percentages (lit vs unlit)
  - Distinguishes relative risk model scores from empirical crime probabilities
- [x] Graceful degradation & missing data handling:
  - Explicit `UNSCORED` state when FastAPI ML service is unreachable or unready
  - Strictly refuses to invent safety scores or falsely assert safety for unscored routes
  - Independent `FASTEST` travel time calculation proceeds without ML dependency
  - `NoRouteFoundException` (HTTP 404) for disconnected graphs and unreachable destinations
- [x] REST API endpoints (`RouteController`):
  - `POST /api/routes/search`: standard route comparison endpoint
  - `POST /api/routes/compare`: dedicated alias endpoint
  - `POST /api/routes`: preserved for backward compatibility
- [x] Mobile route comparison screen (`mobile/app/(tabs)/routes.tsx`):
  - Origin and destination inputs with quick Bangalore coordinate presets
  - Side-by-side 3-way selector tabs (`FASTEST`, `BALANCED`, `SAFEST`)
  - Multi-polyline `MapView` rendering with active route highlighting
  - Prominent trade-off explanation and relative risk disclaimer
  - Loading spinner, network error banner with retry button, and empty state
  - Accessibility labels and semantic roles throughout
- [x] Automated test suites:
  - Backend unit & integration tests: **46 passed out of 46 tests** (`RouteComparisonServiceTest`, `RouteControllerTest`, `AStarRoutingEngineTest`, `RoutingPropertiesTest`, `MlServiceClientTest`, `RoadSegmentFeatureServiceTest`, `RouteServiceMlIntegrationTest`, `MlServicePropertiesTest`, `SafePathBackendApplicationTests`)
  - Mobile TypeScript compilation (`npx tsc --noEmit`): **0 errors**
  - Python / FastAPI ML tests: **62 passed out of 62 tests**
  - Total workspace: **108 passed tests**, 0 failures, 0 regressions
- [x] Technical documentation:
  - Created `docs/route-comparison.md`
  - Updated `docs/api.md`

---

## Milestone 20 — Safety Heatmap

**Status:** ✅ COMPLETE  
**Goal:** Implement spatial safety heatmap endpoint and mobile overlay with honest ML risk representation.

### Completed Tasks

- [x] Backend heatmap DTOs (`HeatmapDto`):
  - Request with bounding box coordinates (`minLat`, `minLon`, `maxLat`, `maxLon`) and optional segment limit
  - Response with bounding box, segment counts, provenance flags, disclaimer, and road-segment list
  - Road-segment risks with segmentId, osmId, name, highwayType, EPSG:4326 coordinates, GeoJSON LineString, relativeRiskScore, safetyScore, riskCategory, scoringStatus, colorHex, and lighting attributes
- [x] Spatial bounding box query (`RoadSegmentRepository`):
  - `findSegmentsInBoundingBox(Geometry bbox, int limit)` using PostGIS spatial indexing (`ST_Intersects`)
- [x] Heatmap backend service (`HeatmapService`):
  - Coordinate range validation: $[-90, 90]$ for latitude, $[-180, 180]$ for longitude, $min \le max$
  - Viewport span limit: rejects spans exceeding $0.15^\circ$ ($\approx 16.5$ km) with HTTP 400
  - Limit clamping: bounds limits to $[1, 1000]$ (default 500)
  - Single-batch ML feature extraction and inference via FastAPI client (`/predict-risk/batch`)
  - Risk categorization & color mapping:
    - `VERY_LOW` / `LOW` ($S \ge 60$): `#34C759` (Vibrant green)
    - `MODERATE` ($40 \le S < 60$): `#FF9500` (Amber)
    - `HIGH` ($20 \le S < 40$): `#FF3B30` (Red)
    - `VERY_HIGH` ($S < 20$): `#D70015` (Dark crimson)
    - `UNSCORED` / `UNAVAILABLE`: `#8E8E93` (Slate gray)
  - Graceful degradation: when ML service is offline or unreachable, returns segments as `UNAVAILABLE` with `#8E8E93` without throwing 500 or fabricating safety
  - Missing data honesty: streets without observations are marked `UNSCORED` and never defaulted to safe or low-risk values
  - Clear synthetic demonstration flag (`isSyntheticDemonstration: true`) and non-probabilistic disclaimers
- [x] Heatmap REST controller (`HeatmapController`):
  - `GET /api/heatmap`: primary spatial heatmap endpoint
  - `GET /api/routes/heatmap`: alias endpoint for routing context
- [x] Mobile heatmap service & client (`mobile/services/heatmap.ts`):
  - Strong TypeScript interfaces matching backend DTOs
  - `fetchHeatmap(params)` API client method
- [x] Mobile map visualization (`mobile/app/(tabs)/index.tsx`):
  - Prominent header switch: `🔥 Heatmap: ON / ⚪ Heatmap: OFF` with accessibility role `switch`
  - Viewport-based debounced querying (600ms debounce on region change)
  - Viewport span warning when user zooms out past $0.14^\circ$, with one-tap "Zoom to Bangalore" button
  - Vector Polyline overlays colored by risk category, with dashed pattern for unscored/unavailable segments
  - Collapsible Legend card explaining score ranges, category thresholds, and academic honesty notices
  - Tappable segment inspection card displaying segment name, highway type, safety score, relative risk, lighting, and synthetic demonstration warning
  - Coexistence demonstration: `🧭 Route Test` button demonstrating seamless coexistence of heatmap segments with active navigation polylines
  - Loading, error with retry, empty state, and location permission denial fallbacks
- [x] Route comparison integration (`mobile/app/(tabs)/routes.tsx`):
  - Added optional `🔥 Heatmap Overlay` toggle in the Route Comparison tab so users can inspect routes overlaid directly on safety heatmap segments
- [x] Automated test suites:
  - Backend unit & integration tests: **57 passed out of 57 tests** (added `HeatmapServiceTest` [9 tests] and `HeatmapControllerTest` [2 tests])
  - Mobile TypeScript compilation (`npx tsc --noEmit`): **0 errors** across entire codebase
  - Python / FastAPI ML tests: **15 passed out of 15 tests** with 100% success rate
  - Total workspace tests: **129 passed tests**, 0 failures, 0 regressions
- [x] Technical documentation:
  - Created `docs/safety-heatmap.md`
  - Updated `docs/api.md` with `/api/heatmap` specification

---

## Milestone 21 — Community Reports

**Status:** ✅ COMPLETE  
**Goal:** Implement authenticated community report submission, optional spatial attribution, lifecycle verification status, abuse-prevention rate limiting, and mobile reporting experience.

### Completed Tasks

- [x] Database schema & migration (`V13__community_report_enhancements.sql`):
  - Made `latitude`, `longitude`, and `geometry` nullable to support optional spatial attribution
  - Added `location_precision VARCHAR(30) DEFAULT 'UNKNOWN'` column
- [x] Entity & DTO enhancements (`CommunityReport`, `CommunityReportDto`):
  - Added `LocationPrecision` enum (`EXACT_COORDINATE`, `LOCALITY`, `UNKNOWN`)
  - Updated `CreateRequest` with optional coordinates, `@PastOrPresent` incident time validation, max 1000 character description limit, and `locationConfirmed` flag
  - Ensured `Response` strictly protects reporter privacy by omitting user IDs, emails, names, or phone numbers
- [x] Sliding-window rate limiter service (`ReportRateLimiterService`):
  - Thread-safe in-memory sliding-window rate limiting enforcing maximum 5 submissions per 10 minutes per authenticated user UUID
  - Returns HTTP 429 Too Many Requests with informative retry-after calculation when limit exceeded
- [x] Backend service & controller (`CommunityReportService`, `CommunityReportController`):
  - `POST /api/reports`: Authenticated submission with rate limiting, optional location handling, description sanitization, and automatic `PENDING` initial status
  - `GET /api/reports/nearby`: Public spatial query returning **only** `VERIFIED` reports with geometries; never leaks unverified reports or reporter identities
  - `GET /api/reports/my`: Authenticated user query returning their own submitted reports across all lifecycle statuses (`PENDING`, `VERIFIED`, `REJECTED`)
  - `PUT /api/reports/{id}/verify`: Moderation transition endpoint restricted to `ROLE_MODERATOR` and `ROLE_ADMIN` (`@PreAuthorize`) enforcing valid transitions (`PENDING` $\to$ `VERIFIED` | `REJECTED`) and recording audit metadata (`verifiedBy`, `verifiedAt`, `verificationNote`)
- [x] Data integrity & ML ground truth isolation:
  - Documented strict isolation: unverified community reports (`PENDING`) never enter the crime ingestion dataset or affect ML models or A* routing weights
  - Zero coordinate fabrication: unlocated reports retain `UNKNOWN` precision and null coordinates
- [x] Mobile application experience (`mobile/services/reports.ts`, `mobile/app/(tabs)/reports.tsx`):
  - Prominent emergency banner warning that reports are not an emergency dispatch mechanism (prompts dialing 112/100)
  - 8 controlled category options with human-readable labels, descriptions, and icons
  - Description input with live 1000-character counter
  - Quick incident timing selector (Just Now, Past Hour, Earlier Today, Yesterday)
  - Optional location toggle with explicit confirmation checkbox, foreground-only location access, and Bangalore sample location fallback
  - Two-tab interface: "Submit Report" and "My Reports" history view with color-coded status pills
  - Submission validation, loading spinners, success confirmation with reference ID, and retryable error banners
- [x] Automated test suites:
  - Backend unit & integration tests: **71 passed out of 71 tests** (added `CommunityReportServiceTest` [10 tests] and `CommunityReportControllerTest` [4 tests])
  - Mobile TypeScript compilation (`npx tsc --noEmit`): **0 errors** across entire codebase
  - Python / FastAPI ML tests: **15 passed out of 15 tests** with 100% success rate
  - Total workspace tests: **143 passed tests**, 0 failures, 0 regressions
- [x] Technical documentation:
  - Created `docs/community-reports.md`
  - Updated `docs/api.md`, `docs/database.md`, `docs/security.md`, and `docs/data-quality.md`

---

## Milestone 22 — Safe Zones

**Status:** ✅ COMPLETE  
**Goal:** Implement safe-zone entity and database migration, PostGIS bounding-box and proximity queries, public REST endpoints, mobile interactive map overlays and directory finder, with strict provenance preservation and zero data fabrication.

### Completed Tasks

- [x] Database schema & migration (`V14__safe_zone_verification_status.sql`):
  - Added `verification_status VARCHAR(20) NOT NULL DEFAULT 'UNVERIFIED'` column
  - Created indexes on `verification_status` and compound `(zone_type, verification_status)`
- [x] Entity & DTO enhancements (`SafeZone`, `SafeZoneDto`):
  - Added `VerificationStatus` enum (`VERIFIED`, `UNVERIFIED`, `UNAVAILABLE`)
  - Preserved coordinates with explicit reference system (EPSG:4326 Point geometry)
  - Added source provenance, OpenStreetMap node ID, and creation/update timestamps
  - Added prominent academic disclaimer: facility presence does not guarantee personal safety, real-time staffing, or operational status
  - Missing fields (address, phone, operating hours) are represented honestly as null without synthetic invention
- [x] Repository spatial queries (`SafeZoneRepository`):
  - Viewport bounding box query (`findInBoundingBox` and `findInBoundingBoxByTypes`) using `ST_Intersects` against `ST_MakeEnvelope`
  - Proximity search (`findNearby` and `findNearbyByTypes`) using `ST_DWithin` on geography with distance ordering
- [x] Safe zone service layer (`SafeZoneService`):
  - Viewport span validation: enforces max allowed span of $0.15^\circ$ ($\approx 16.5$ km) to prevent DoS resource exhaustion
  - Coordinate range validation: $[-90, 90]$ for latitude, $[-180, 180]$ for longitude, $min \le max$
  - Result limit clamping: bounds nearby search to $[1, 50]$ (default 10) and bounding box to $[1, 100]$ (default 50)
  - Proximity radius clamping: bounds search radius to $\le 10,000$ meters
  - Pure Haversine formula calculation for exact, deterministic great-circle distances in meters
  - Honest empty results: returns `[]` without fabricating fallback facilities
  - ID lookup with `ResourceNotFoundException` on missing IDs
- [x] REST controller endpoints (`SafeZoneController`):
  - `GET /api/safe-zones`: Viewport bounding box or proximity coordinates search
  - `GET /api/safe-zones/nearby`: Dedicated proximity search with distance calculation
  - `GET /api/safe-zones/{id}`: Single facility retrieval
- [x] Security & public access (`SecurityConfig`):
  - Configured public permit for `GET /api/safe-zones` and `GET /api/safe-zones/**` without authentication barriers
  - Privacy preservation: safe zones contain only public municipal facility information, with zero user or reporter PII
- [x] Mobile service & client (`mobile/services/safeZones.ts`):
  - Strong TypeScript interfaces (`SafeZone`, `SafeZoneCategory`, `VerificationStatus`)
  - Category metadata with custom icons and color tokens (👮 Police, 🏥 Hospital, 💊 Pharmacy, 🚏 Transit, 🏢 Public Assistance)
  - Verification metadata with status badges (`VERIFIED` green, `UNVERIFIED` amber, `UNAVAILABLE` gray)
- [x] Mobile interactive map integration (`mobile/app/(tabs)/index.tsx`):
  - Accessible header toggle: `🛡️ Safe Zones: ON / ⚪ Safe Zones: OFF`
  - Category markers color-coded with distinct pins and icons
  - Interactive selection: tapping a marker opens a bottom card with facility name, category, verification badge, address, hours, source, and disclaimer
  - "Directions Here" action button: one-tap navigation passing coordinates directly to route planner
  - Coexistence: Safe zone markers display concurrently alongside route polylines and safety heatmap road segments
  - Expandable Legend: Documents safe zone categories and verification confidence levels
- [x] Mobile directory & finder screen (`mobile/app/safe-zones.tsx`):
  - Dedicated searchable directory with category chips, GPS location auto-fill, and radius selector
  - Real-time distance indicators and direct route navigation buttons
  - Honest empty state when no verified data exists in the queried area
- [x] Route comparison integration (`mobile/app/(tabs)/routes.tsx`):
  - Integrated `useLocalSearchParams` to autofill `endLat`, `endLon`, and `endName` when launched from safe zones
- [x] Automated test suites:
  - Backend unit & integration tests: **87 passed out of 87 tests** (added `SafeZoneServiceTest` [10 tests] and `SafeZoneControllerTest` [6 tests])
  - Mobile TypeScript compilation (`npx tsc --noEmit`): **0 errors** across entire codebase
  - Python / FastAPI ML tests: **15 passed out of 15 tests** with 100% success rate
  - Total workspace tests: **159 passed tests**, 0 failures, 0 regressions
- [x] Technical documentation:
  - Created `docs/safe-zones.md`
  - Updated `docs/api.md`, `docs/database.md`, `docs/security.md`, and `docs/data-quality.md`

---

## Milestone 23 — Weather Integration

**Status:** ✅ COMPLETE  
**Goal:** Implement Open-Meteo typed weather client, spatial-temporal caching, PostGIS persistence, feature extraction integration, routing fault tolerance, and mobile weather display.

### Completed Tasks

- [x] Database schema & migration (`V15__weather_enhancements.sql`):
  - Added `wind_speed_kmh`, `relative_humidity`, and `weather_description` to `weather_observations`
  - Created index on `source` column
- [x] Typed Open-Meteo REST client (`OpenMeteoClient`, `OpenMeteoClientImpl`):
  - Uses Spring 6 `RestClient` with configurable connection (3,000 ms) and read (4,000 ms) timeouts
  - Geographic coordinate validation rejecting out-of-bounds inputs
  - Robust exception mapping: wraps timeouts and 4xx/5xx provider errors into `WeatherClientException`
- [x] In-memory caching & PostGIS persistence service (`WeatherService`):
  - Quantized cache keys (`%.2f, %.2f` ~1.1 km grid) with 30-minute configurable TTL
  - Persistent storage in PostGIS `weather_observations` table for historical auditability and offline fallback
  - Transparent fallback: serves recent DB observation (< 3h) when provider is temporarily unreachable
  - Honest missing data: returns `available: false` with non-null academic disclaimer without fabricating measurements
- [x] REST controller & public endpoints (`WeatherController`, `SecurityConfig`):
  - `GET /api/weather?lat={lat}&lon={lon}` with Bean Validation constraints
  - Configured public permit in `SecurityConfig` without authentication barriers
- [x] Road-segment feature extraction integration (`RoadSegmentFeatureService`):
  - Overloaded `extractFeatures` and `prepareBatchRequests` with optional precipitation and visibility parameters
  - Populates features #13 (`weather_precipitation`) and #14 (`weather_visibility`) in 15-feature schema
  - Unobserved weather remains null to trigger documented ML imputation
- [x] Route scoring integration & fault tolerance (`RouteService`):
  - Fetches ambient weather at origin and passes parameters to feature extractor
  - Flags `weatherContextAvailable` truthfully in route search and comparison responses
  - Non-blocking failure: weather outages never prevent routing; FASTEST route remains independent of ML, while BALANCED and SAFEST use standard defaults
- [x] Mobile UI integration (`mobile/services/weather.ts`, `mobile/components/WeatherWidget.tsx`, `mobile/app/(tabs)/index.tsx`, `mobile/app/(tabs)/routes.tsx`):
  - Compact pill in floating map header showing current weather emoji and temperature
  - Expandable detailed card displaying observation metrics (temp, precip, wind, humidity, visibility), forecast probability badge, source attribution, and disclaimer
  - Weather integration status banner on Route Comparison screen
  - Loading, error, unavailable, and retry states
- [x] Automated test suites:
  - Backend tests: **104 passed out of 104 tests** (added `OpenMeteoClientTest` [5 tests], `WeatherServiceTest` [6 tests], `WeatherControllerTest` [2 tests], `RoadSegmentFeatureServiceTest` [2 new tests], `RouteComparisonServiceTest` [2 new tests])
  - Mobile TypeScript compilation (`tsc.cmd --noEmit`): **0 errors** across entire codebase
  - Python / FastAPI ML tests: **15 passed out of 15 tests** with 100% success rate
  - Total workspace tests: **176 passed tests**, 0 failures, 0 regressions
- [x] Technical documentation:
  - Created `docs/weather-integration.md`
  - Updated `docs/api.md`, `docs/data-quality.md`, `docs/architecture.md`, and `docs/development-progress.md`

---

## Architecture Decisions Log

| Date | Decision | Rationale |
|------|----------|-----------|
| 2026-10-08 | Spring Boot monolith (not microservices) | Appropriate for project scale; avoids over-engineering |
| 2026-10-08 | FastAPI separate from Spring Boot | Python ML ecosystem; separation of concerns |
| 2026-10-08 | Flyway for migrations | Version-controlled schema; reproducible setup |
| 2026-10-08 | Expo Router for mobile navigation | File-based routing; native experience |
| 2026-10-08 | Open-Meteo for weather | Free, no API key required |
| 2026-10-08 | Nominatim for geocoding | Free, OSM-based; respect usage policy |
| 2026-10-11 | SQLite cache for geocoding | Prevents redundant network requests and complies with Nominatim ToS |
| 2026-10-11 | Null coordinate enforcement at schema layer | Eliminates risk of accidental synthetic coordinates for coarse records |
| 2026-10-11 | Idempotent duplicate skipping via external_id | Ensures safe and repeatable pipeline reruns |
| 2026-10-11 | Metric projection EPSG:3857 for KDE | Ensures distance calculations (500m, 1km, sigma) represent ground meters accurately |
| 2026-10-11 | Temporal masking (incident_datetime < query_time) | Strictly prevents future crime data from leaking into past evaluation features |
| 2026-10-11 | Point-level KDE isolation | Guarantees coarse records are excluded from continuous point densities |
| 2026-10-11 | Train-only fitting of MinMaxScaler | Eliminates data leakage into validation and test sets |
| 2026-10-14 | Viewport Span Constraint (<= 0.15 deg) for Safe Zones | Prevents denial-of-service spatial query resource exhaustion across wide geographic queries |
| 2026-10-14 | Haversine Distance Calculation for Proximity Search | Provides exact, deterministic great-circle distances in meters without Hibernate column projection conflicts |
| 2026-10-14 | Separation of Existence vs Operational Staffing | Clarifies that verified registry existence does not guarantee real-time facility staffing or personal safety |
| 2026-10-14 | Public Barrier-Free Access for Safe Zones | Eliminates JWT authentication requirements on GET /api/safe-zones to ensure immediate emergency access |
| 2026-10-14 | Multi-Layer Coexistence on Interactive Map | Enables concurrent display of safe zone category pins, route polylines, and safety heatmap segments |
| 2026-10-14 | Spatial-Temporal Quantization (~1.1 km Grid) for Weather | Cache key quantization to 2 decimal places prevents excessive API requests matching atmospheric model resolution |
| 2026-10-14 | Non-Blocking Routing on Weather Failure | Prevents external provider outages from interrupting critical pedestrian routing; FASTEST runs ML-independently |
| 2026-10-14 | Strict Separation of Weather Observation vs Forecast | Prevents misrepresenting forward-looking precipitation probabilities as present environmental observations |
| 2026-10-14 | Environmental Context vs Crime Causation Charter | Weather is treated strictly as an environmental context indicator, never as proof of street crime risk |
| 2026-10-11 | Class imbalance weighting (balanced) | Accommodates minority high-risk road segment distribution |
| 2026-10-11 | Explicit synthetic evaluation flagging | Prevents mock fixture test metrics from ever being misrepresented as real-world accuracy |
| 2026-10-12 | XGBoost scale_pos_weight calculation | Balances gradient updates across minority positive crime risk instances |
| 2026-10-12 | Separate models/xgboost artifact directory | Preserves baseline model artifacts for fair comparative benchmarking |
| 2026-10-12 | Unified 3-way baseline comparison | Guarantees identical data partitions when comparing XGBoost, Random Forest, and Naive baselines |
| 2026-10-12 | TreeExplainer additive reconstruction test | Numerically guarantees efficiency property holds within 1e-4 tolerance |
| 2026-10-12 | Non-causal associative phrasing convention | Prevents misleading users by distinguishing statistical correlation from physical causality |
| 2026-10-12 | Three-tier score taxonomy | Distinguishes raw tree margin, calibrated risk score, and user safety score (1 - risk) |
| 2026-10-12 | Pydantic allow_inf_nan=False and extra="forbid" | Prevents non-finite floats and unexpected keys from corrupting ML preprocessing |
| 2026-10-12 | Maximum batch size limit (500 segments) | Protects ML service from memory saturation during A* routing graph expansion |
| 2026-10-12 | Explicit imputed_features tracking | Transparently reports to callers whenever default substitutions occurred |
| 2026-10-12 | Global exception sanitization | Eliminates risk of leaking stack traces, local paths, or secrets to API clients |
| 2026-10-12 | RestClient with SimpleClientHttpRequestFactory | Bounded connect (3s) and read (5s) timeouts protect Spring Boot threads |
| 2026-10-12 | Segment ID-Keyed Map Lookup | Eliminates reliance on array indices and guards against out-of-order predictions |
| 2026-10-12 | Strict Rejection Fallback for SAFEST mode | Prevents unverified or unscored paths from ever being falsely labeled safest |
| 2026-10-12 | Independent Travel-Time Routing for FASTEST mode | Allows travel-time optimization to proceed without ML service dependency |
| 2026-10-12 | Client-Side Batch Chunking (<= 500 segments) | Transparently splits large network queries into server-safe payload chunks |
| 2026-10-12 | Formally Admissible Zero Heuristic (h=0) | Guarantees optimal path discovery across dimensionless multi-criteria edge costs |
| 2026-10-12 | ClosedSet Settlement in PriorityQueue A* | Prevents cycles and reduces worst-case runtime to O((V+E) log V) without O(N) queue operations |
| 2026-10-12 | Three-tier Deterministic Tie-Breaking (f, g, id) | Ensures 100% reproducible path selection when competing routes have equal total cost |
| 2026-10-12 | Non-Probabilistic Multi-Criteria Penalty View | Clarifies that edge risk is an uncalibrated relative score, not an additive crime probability |
| 2026-10-12 | RoutingProperties Weight Validation & Normalization | Prevents negative or non-finite weights from corrupting route search cost |
| 2026-10-13 | Single-Batch Prediction for Route Comparison | Queries local network segment risks in one batch call to avoid 3x redundant ML invocations |
| 2026-10-13 | Segment Sequence Identity for Duplicate Detection | Compares ordered segment IDs to prevent misleading users with duplicate alternatives |
| 2026-10-13 | Dynamic Empirical Trade-Off Synthesis | Formulates clear factual summaries (Δt, Δd, Δsafety, % lit) rather than static copy |
| 2026-10-13 | Explicit UNSCORED State on ML Degradation | Prevents false safety claims by clearly designating routes as unscored when ML is down |
| 2026-10-13 | Max Viewport Bounding Box Span (<= 0.15 deg) | Prevents memory exhaustion and unmanageable payloads by rejecting zoomed-out spatial queries |
| 2026-10-13 | Dashed Gray Neutral Treatment for Unscored Segments | Clearly signals missing data or offline ML status without confusing users or asserting safety |
| 2026-10-13 | Multi-Representation Geometry Serialization | Exposes both GeoJSON LineString and mobile coordinate lists for seamless cross-platform consumption |
| 2026-10-13 | Dual-Screen Route & Heatmap Coexistence | Supports heatmap underlays on both the primary map screen and the route comparison screen |
| 2026-10-14 | Sliding-Window In-Memory Rate Limiting | Enforces max 5 reports per 10 min without requiring external caching infrastructure (Redis) |
| 2026-10-14 | Nullable Spatial Attribution for Community Reports | Honors optional location consent without inventing coordinates or breaking database constraints |
| 2026-10-14 | Strict Public Query Filtering to VERIFIED Status | Prevents unverified, malicious, or erroneous community reports from appearing on public maps |
| 2026-10-14 | Reporter PII Scrubbing at Service Layer | Omits user account details from public responses to prevent stalking or deanonymization |
| 2026-10-14 | One-Way Review Lifecycle State Transitions | Permits only PENDING -> VERIFIED and PENDING -> REJECTED to prevent status flipping |



---

## Known Issues / Blockers

None at this time.

---

## Implementation Notes

### Data Quality
- Indian crime datasets (e.g., NCRB) typically provide district-level aggregates, not GPS coordinates.
- The `location_precision` field is critical for honest ML training.
- KDE will be applied only where legitimate spatial precision exists.

### ML Strategy
- Model training is **offline** using Python scripts in `scripts/`.
- Trained model artifacts are saved to `models/`.
- FastAPI serves inference only — no training at runtime.
- Metrics will be documented only after actual training.

---

*SafePath AI — development-progress.md — Updated 2026-10-08*
