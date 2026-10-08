# SafePath AI — Master Implementation Plan

> **Version:** 1.0.0  
> **Date:** 2026-10-08  
> **Source:** Project specification (Section 45 onwards)

This document is the authoritative implementation plan. It captures the complete project specification in structured form.

---

## Part 1: Project Objective

Build a complete mobile application that recommends safer walking routes instead of simply the shortest or fastest route.

The system combines, where real data is available:

- Historical crime information and severity
- Spatial crime patterns and temporal patterns
- Time of day and day of week
- Street lighting information
- Pedestrian/activity proxies
- Weather conditions
- Public transport proximity
- Proximity to police stations, hospitals, pharmacies
- Road network information
- Verified community reports

### Route Outputs

The system must provide three route options:
1. **FASTEST** — minimizes travel time
2. **BALANCED** — balances time and safety
3. **SAFEST** — minimizes risk

---

## Part 2: Non-Negotiable Rules

See [`PROJECT-CONTRACT.md`](PROJECT-CONTRACT.md) and [`../AGENTS.md`](../AGENTS.md) for the full binding ruleset.

Key principles:
- Never fabricate data, coordinates, metrics, or API responses
- Preserve geographic precision (EXACT_COORDINATE → DISTRICT → UNKNOWN)
- Clearly label all demo/mock data
- Report ML metrics only after actual training and evaluation
- Document all assumptions and implementation choices

---

## Part 3: Architecture

### Component Responsibilities

**Mobile (React Native + Expo)**
- UI, map display, user interaction
- Location permission handling
- Route visualization
- API communication with Spring Boot

**Spring Boot Backend**
- Authentication (JWT)
- User management
- Community reports
- Safe zones
- Route requests and orchestration
- A\* routing engine
- Database access (via JPA)
- Communication with ML service

**PostgreSQL + PostGIS**
- All persistent data
- Spatial data and queries
- Road segments, crime data, reports, safe zones

**FastAPI ML Service**
- ML inference
- Feature preprocessing
- Model loading and serving
- SHAP explainability

**Python Scripts (offline)**
- Data processing
- Feature engineering
- Model training
- Model evaluation

---

## Part 4: Technology Stack

### Mobile
- React Native
- Expo + Expo Router
- TypeScript
- NativeWind (or StyleSheet-based)
- React Native Maps

### Backend
- Java 21
- Spring Boot 3.x
- Maven
- Spring Web, Security, Data JPA
- Bean Validation
- JWT (jjwt)
- OpenAPI/Swagger

### Database
- PostgreSQL 15+
- PostGIS 3+
- Flyway (migrations)

### ML Service
- Python 3.11+
- FastAPI
- Pandas, NumPy
- scikit-learn
- XGBoost
- SHAP
- GeoPandas, Shapely, PyProj
- NetworkX

### Maps/Geospatial
- OpenStreetMap
- Nominatim (geocoding)
- Overpass API (OSM data)
- OSRM (optional routing baseline)

### Weather
- Open-Meteo (free, no API key)

### DevOps
- Git + GitHub
- Docker + Docker Compose
- GitHub Actions (CI)

---

## Part 5: Database Schema

### Core Tables

| Table | Purpose |
|-------|---------|
| `users` | User accounts |
| `roles` | User roles (USER, ADMIN, MODERATOR) |
| `emergency_contacts` | User emergency contacts |
| `crime_incidents` | Crime records with location precision |
| `road_segments` | Road network graph edges |
| `community_reports` | User safety reports |
| `safe_zones` | Police stations, hospitals, etc. |
| `route_requests` | Route search history |
| `route_results` | Computed route details |
| `weather_observations` | Cached weather data |
| `feature_snapshots` | ML feature cache per segment |
| `audit_logs` | System audit trail |

### Crime Incident Fields (key)
- `id`, `crime_type`, `severity`
- `incident_datetime`
- `source` (dataset name)
- `latitude`, `longitude` (nullable — only when legitimately available)
- `geometry` (PostGIS Point — only when legitimately available)
- `location_precision` (ENUM: EXACT_COORDINATE, ADDRESS_GEOCODED, LOCALITY, POLICE_STATION, DISTRICT, UNKNOWN)
- `location_confidence` (FLOAT 0–1)
- `original_address` (preserved if geocoded)
- `district`, `locality`, `police_station_name`
- `created_at`

---

## Part 6: API Endpoints

### Authentication
- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/refresh`

### Users
- `GET /api/users/me`
- `PUT /api/users/me`
- `PUT /api/users/me/preferences`

### Routes
- `POST /api/routes/search` — returns fastest/balanced/safest routes
- `GET /api/routes/history`

### Community Reports
- `POST /api/reports`
- `GET /api/reports/nearby`
- `GET /api/reports/my`
- `PUT /api/reports/{id}/verify` (admin)

### Safe Zones
- `GET /api/safe-zones/nearby`
- `GET /api/safe-zones/{id}`

### Emergency
- `GET /api/emergency/contacts`
- `POST /api/emergency/contacts`
- `DELETE /api/emergency/contacts/{id}`
- `POST /api/emergency/sos` (log SOS event)

### ML Service (internal)
- `GET /health`
- `POST /predict-risk`
- `POST /predict-risk/batch`
- `POST /explain`

---

## Part 7: ML Pipeline

### Objective
Predict **relative road-segment risk** (not absolute crime probability).

### Features
- Historical crime frequency near segment
- Crime severity (weighted)
- Temporal patterns (hour, day of week)
- Spatial crime intensity (KDE — only where valid precision data exists)
- Recent crime activity
- Estimated lighting quality
- Population/activity proxy
- Weather conditions
- Public transport proximity
- Emergency service proximity
- Road characteristics
- Verified community reports

### Models
1. **Random Forest** — baseline
2. **XGBoost** — primary model

### Evaluation
> **IMPORTANT:** Metrics will only be reported after actual training.

Classification metrics (if framed as classification):
- Precision, Recall, F1
- ROC-AUC, PR-AUC

Regression metrics (if framed as regression):
- MAE, RMSE, R²

### Methodology
- Proper train/validation/test split
- No temporal leakage (respect chronological order)
- Spatial cross-validation where possible

---

## Part 8: Routing Algorithm

### Primary: A\*

Cost function:
```
cost(edge) = α * normalized_time + β * normalized_distance + γ * normalized_risk
```
where `α + β + γ = 1`

### Route Mode Weights

> **IMPLEMENTATION CHOICE:** Default weights are implementation choices, not empirically optimized values.

| Mode | α (time) | β (distance) | γ (risk) |
|------|----------|-------------|---------|
| FASTEST | 0.7 | 0.2 | 0.1 |
| BALANCED | 0.3 | 0.2 | 0.5 |
| SAFEST | 0.1 | 0.1 | 0.8 |

### Fallback: Dijkstra

Implemented as a baseline and fallback where A\* heuristic is unavailable.

---

## Part 9: Safety Score

- ML raw output → normalized risk score (0–1)
- User-facing safety score = `1 - risk_score` (or similar derivation, documented)
- Safety level categories (application-defined):
  - 0.0–0.2 → Very Unsafe
  - 0.2–0.4 → Unsafe
  - 0.4–0.6 → Moderate
  - 0.6–0.8 → Safe
  - 0.8–1.0 → Very Safe

> **IMPORTANT:** These thresholds are IMPLEMENTATION CHOICES. They are not universal scientific standards.

---

## Part 10: Mobile Screens

1. Splash
2. Login
3. Register
4. Home (Map overview)
5. Map (full interactive map)
6. Destination Search
7. Route Comparison (3 routes side by side)
8. Route Details
9. Navigation (turn-by-turn)
10. Safety Explanation
11. Community Report (submit)
12. Report History
13. Emergency / SOS
14. Emergency Contacts
15. Nearby Safe Zones
16. Profile
17. Settings

---

## Part 11: Implementation Milestones (Ordered)

| # | Milestone |
|---|-----------|
| 1 | Project Foundation |
| 2 | Mobile Foundation |
| 3 | Backend Foundation |
| 4 | Database / PostGIS |
| 5 | Authentication |
| 6 | Mobile Authentication |
| 7 | Mobile–Backend Integration |
| 8 | Maps / Location |
| 9 | Basic Routing |
| 10 | A\* Routing Engine |
| 11 | Crime Data Ingestion |
| 12 | Spatial/Temporal Feature Pipeline |
| 13 | Random Forest Baseline |
| 14 | XGBoost Model |
| 15 | SHAP Explainability |
| 16 | FastAPI ML Service |
| 17 | Spring Boot ↔ ML Integration |
| 18 | Risk-Aware A\* Routing |
| 19 | Route Comparison |
| 20 | Safety Heatmap |
| 21 | Community Reports |
| 22 | Safe Zones |
| 23 | Weather Integration |
| 24 | Personalization |
| 25 | SOS / Emergency Features |
| 26 | Testing |
| 27 | Security Review |
| 28 | Documentation |
| 29 | Android Development Build |
| 30 | Android APK Build |

---

## Part 12: Academic Differentiators

1. Road-segment risk prediction (not just area heatmap)
2. ML-based risk estimation (RF + XGBoost)
3. Spatial/temporal feature engineering (KDE, STKDE)
4. Safety-aware A\* route optimization
5. Three-mode route comparison
6. Explainable AI (SHAP)
7. Dynamic contextual risk
8. Community reporting with verification
9. Safe zone integration (PostGIS)
10. Personalization profiles
11. PostGIS spatial backend
12. Geographic uncertainty handling (location_precision)

---

## Part 13: Testing Requirements

### Backend
- Authentication (register, login, invalid, expired token)
- Bean Validation
- Route service
- Route ranking (mode weights)
- Community reports
- Authorization
- Error handling

### Routing
- Graph construction
- A\* correctness
- FASTEST: travel time prioritized
- BALANCED: compromise verified
- SAFEST: risk prioritized
- Weight behavior (changing weights changes results)

### ML
- Preprocessing pipeline
- Feature generation
- Inference endpoint
- Missing feature handling
- Model loading
- SHAP explanation

### Database
- Flyway migrations
- PostGIS spatial queries

### Mobile
- Key screens
- API error states
- Permission denied states

---

## Part 14: Security Requirements

- JWT authentication (RS256 or HS256 with strong secret)
- BCrypt password hashing
- Bean Validation on all inputs
- Global exception handler (no stack traces in responses)
- CORS explicit configuration
- Environment variables for all secrets
- No secrets in Git
- Expo SecureStore for mobile tokens
- Rate limiting strategy documented

---

## Part 15: Docker Services

| Service | Image | Port |
|---------|-------|------|
| postgres | postgis/postgis:15-3.4 | 5432 |
| backend | safepath-backend (custom) | 8080 |
| ml-service | safepath-ml (custom) | 8001 |

---

*SafePath AI — MASTER-IMPLEMENTATION-PLAN.md v1.0.0 — 2026-10-08*
