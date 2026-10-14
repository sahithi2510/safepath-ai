# SafePath AI — System Architecture

> **Status:** Living document — updated as architecture evolves  
> **Last Updated:** 2026-10-08

---

## 1. High-Level Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Mobile Application                       │
│              React Native + Expo (TypeScript)               │
│                                                             │
│  Screens: Splash, Login, Register, Home, Map, Routes,       │
│           Reports, Emergency, SOS, Safe Zones, Profile       │
│  Maps: React Native Maps (OpenStreetMap tiles)              │
│  Auth: Expo SecureStore (JWT)                               │
└─────────────────────┬───────────────────────────────────────┘
                      │ HTTPS REST API
                      │
┌─────────────────────▼───────────────────────────────────────┐
│                 Spring Boot Backend                         │
│                  Java 21, Maven                             │
│                                                             │
│  ┌─────────────┐  ┌──────────────┐  ┌────────────────────┐ │
│  │ Auth Module │  │ Route Module │  │  Community Module  │ │
│  │ JWT, BCrypt │  │ A* Engine    │  │  Reports, Verify   │ │
│  └─────────────┘  └──────────────┘  └────────────────────┘ │
│  ┌─────────────┐  ┌──────────────┐  ┌────────────────────┐ │
│  │ Safe Zones  │  │  Emergency   │  │  ML Client         │ │
│  │ PostGIS     │  │  SOS         │  │  (HTTP to FastAPI) │ │
│  └─────────────┘  └──────────────┘  └────────────────────┘ │
└──────────┬──────────────────────────────────┬───────────────┘
           │                                  │
           │ JDBC/JPA                         │ HTTP (internal)
           │                                  │
┌──────────▼──────────┐             ┌─────────▼───────────────┐
│  PostgreSQL +        │             │   FastAPI ML Service    │
│  PostGIS             │             │   Python 3.11+          │
│                      │             │                         │
│  Tables:             │             │  Endpoints:             │
│  - users             │             │  GET  /health           │
│  - crime_incidents   │             │  POST /predict-risk     │
│  - road_segments     │             │  POST /predict-risk/    │
│  - community_reports │             │        batch            │
│  - safe_zones        │             │  POST /explain          │
│  - route_requests    │             │                         │
│  - weather_obs       │             │  Models:                │
│  - feature_snapshots │             │  - Random Forest        │
│  - audit_logs        │             │  - XGBoost              │
│                      │             │  - SHAP Explainer       │
│  Indexes:            │             │                         │
│  - Spatial (GIST)    │             │  [Models loaded from    │
│  - Temporal (BTREE)  │             │   models/ directory]    │
└──────────────────────┘             └─────────────────────────┘
```

---

## 2. Component Responsibilities

### 2.1 Mobile Application

**MUST:**
- Handle all user interface and interaction
- Display maps with OSM tiles
- Handle location permissions (request, denied, unavailable states)
- Communicate with Spring Boot via HTTPS REST
- Store JWT token securely (Expo SecureStore)
- Visualize routes (polylines) and safety (colors)
- Display safe zones and community reports on map
- Handle loading, error, and empty states gracefully

**MUST NOT:**
- Contain routing logic (A\*, Dijkstra)
- Contain ML inference
- Access database directly
- Store sensitive data in AsyncStorage without encryption

### 2.2 Spring Boot Backend

**MUST:**
- Handle user authentication (JWT, BCrypt)
- Orchestrate route requests
- Execute A\* routing with road graph
- Query spatial data (PostGIS)
- Manage community reports (CRUD + verification)
- Manage safe zones
- Communicate with FastAPI for ML inference
- Handle emergency contacts and SOS events
- Validate all inputs (Bean Validation)
- Return structured, consistent API responses

**MUST NOT:**
- Contain Python ML training code
- Call third-party APIs directly for ML inference
- Expose database entities directly in API responses

### 2.3 PostgreSQL + PostGIS

**MUST:**
- Store all persistent application data
- Use PostGIS for spatial data types and queries
- Use Flyway for version-controlled migrations
- Provide spatial indexes (GIST) for performance

### 2.4 FastAPI ML Service

**MUST:**
- Load trained models from `models/` directory at startup
- Serve ML inference requests
- Preprocess features identically to training
- Return SHAP explanations when requested
- Handle model unavailable state
- Return model version in responses

**MUST NOT:**
- Train models at runtime
- Access the database directly
- Contain business routing logic

### 2.5 Python Scripts (Offline)

**MUST:**
- Process raw crime datasets
- Engineer spatial/temporal features
- Train and evaluate models
- Save model artifacts to `models/`
- Document all assumptions

---

## 3. Request Flow: Route Search

```
Mobile                Spring Boot              PostGIS         FastAPI
  │                       │                       │               │
  │ POST /api/routes/search│                       │               │
  │──────────────────────>│                       │               │
  │                       │ Load road graph        │               │
  │                       │──────────────────────>│               │
  │                       │<──────────────────────│               │
  │                       │                       │               │
  │                       │ Load features per edge │               │
  │                       │──────────────────────>│               │
  │                       │<──────────────────────│               │
  │                       │                       │               │
  │                       │ POST /predict-risk/batch               │
  │                       │───────────────────────────────────────>│
  │                       │<───────────────────────────────────────│
  │                       │                       │               │
  │                       │ Run A* (FASTEST)      │               │
  │                       │ Run A* (BALANCED)     │               │
  │                       │ Run A* (SAFEST)       │               │
  │                       │                       │               │
  │ 200 OK (3 routes)     │                       │               │
  │<──────────────────────│                       │               │
```

---

## 4. Data Flow: ML Prediction

```
Road Segment Edge
        │
        ▼
Feature Snapshot (PostGIS DB)
        │
        │  [crime density KDE, temporal features,
        │   lighting proxy, weather, emergency proximity]
        ▼
FastAPI /predict-risk/batch
        │
        │  [preprocessing, normalization]
        ▼
XGBoost Model
        │
        ▼
Risk Score (0.0 – 1.0)
        │
        ▼
Safety Score = 1 - risk_score (or derived formula, documented)
        │
        ▼
A* Edge Cost: α*time + β*distance + γ*risk
```

---

## 5. Authentication Flow

```
Mobile                 Spring Boot
  │                        │
  │ POST /api/auth/register│
  │ {name, email, password}│
  │───────────────────────>│
  │                        │ BCrypt hash password
  │                        │ Save user
  │ 201 Created            │
  │<───────────────────────│
  │                        │
  │ POST /api/auth/login   │
  │ {email, password}      │
  │───────────────────────>│
  │                        │ Verify BCrypt
  │                        │ Generate JWT
  │ 200 OK {token, ...}    │
  │<───────────────────────│
  │                        │
  │ GET /api/users/me      │
  │ Authorization: Bearer  │
  │───────────────────────>│
  │                        │ Validate JWT
  │                        │ Load user
  │ 200 OK {user}          │
  │<───────────────────────│
```

---

## 6. Package Structure

### Spring Boot
```
com.safepath.backend
├── controller/       REST endpoints
├── service/          Business logic
├── repository/       Spring Data JPA repositories
├── entity/           JPA entities
├── dto/              Request/Response DTOs
├── mapper/           Entity ↔ DTO conversion
├── security/         JWT, Spring Security config
├── routing/          A*, Dijkstra, graph model
├── ml/               ML service client
├── service/weather/  Weather integration (Open-Meteo client, spatial caching)
├── exception/        Custom exceptions, global handler
├── config/           Beans, CORS, Swagger
└── util/             Utilities
```

### FastAPI
```
ml-service/
├── app/
│   ├── main.py           FastAPI app entry
│   ├── routers/          Endpoint definitions
│   ├── models/           Pydantic schemas
│   ├── services/         ML inference logic
│   ├── preprocessing/    Feature preprocessing
│   └── config.py         Settings
├── models/               Loaded model artifacts
└── tests/
```

### Mobile
```
mobile/app/
├── (auth)/           Login, Register screens
├── (main)/           Protected screens
│   ├── home/
│   ├── map/
│   ├── routes/
│   ├── reports/
│   ├── emergency/
│   ├── safe-zones/
│   └── profile/
├── _layout.tsx       Root layout
└── index.tsx         Entry point
```

---

## 7. Technology Decisions

| Decision | Choice | Rationale | Category |
|----------|--------|-----------|----------|
| Mobile framework | React Native + Expo | Cross-platform; large ecosystem | IMPLEMENTATION CHOICE |
| Backend language | Java 21 + Spring Boot | Mature; strong security; JPA | IMPLEMENTATION CHOICE |
| ML framework | XGBoost + scikit-learn | Standard for tabular ML; interpretable | IMPLEMENTATION CHOICE |
| Routing algorithm | A\* | Optimal for weighted graph search with heuristic | FACT (algorithmically justified) |
| Database | PostgreSQL + PostGIS | Best open-source spatial DB | IMPLEMENTATION CHOICE |
| Migration | Flyway | Version-controlled schema | IMPLEMENTATION CHOICE |
| Auth | JWT + BCrypt | Standard, stateless auth | IMPLEMENTATION CHOICE |
| Geocoding | Nominatim | Free, OSM-based | IMPLEMENTATION CHOICE |
| Weather | Open-Meteo | Free, no API key | IMPLEMENTATION CHOICE |

---

*SafePath AI — architecture.md — 2026-10-08*
