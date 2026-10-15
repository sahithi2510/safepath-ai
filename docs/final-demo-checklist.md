# SafePath AI — Practical Demonstration Checklist & Execution Guide

> **Document Type:** Operational Demonstration Plan & Verifier Checklist  
> **Status:** AUDITED & VERIFIED (Milestone 26 Hybrid Architecture)  
> **Target Audience:** Evaluators, Examiners, Reviewers, and Developers  
> **Academic Integrity Notice:** Complies strictly with `AGENTS.md` and `PROJECT-CONTRACT.md`. Synthetic demonstration data and mock fixtures are explicitly labeled. No claims of live Docker, database migrations, or mobile runtime rendering are made without direct empirical verification.

---

## 1. System Overview & Component Roles

SafePath AI is an intelligent pedestrian navigation platform designed to compute walking routes balancing travel time, distance, and relative street-level risk.

```
                              +---------------------------------------+
                              |         Mobile App (React Native)     |
                              |   Web (8081) / Android (10.0.2.2)     |
                              +-------------------+-------------------+
                                                  |
                                                  | HTTP / JSON (Port 8080)
                                                  v
                              +---------------------------------------+
                              |        Spring Boot 3.3 Monolith       |
                              |   - JWT Authentication & RBAC         |
                              |   - A* Multi-Criteria Routing Engine  |
                              |   - Safe Zones & Community Reports    |
                              |   - Open-Meteo Weather Client         |
                              +---------+-------------------+---------+
                                        |                   |
            HikariCP / JDBC (Port 5432) |                   | HTTP / JSON (Port 8001)
                                        v                   v
            +-------------------------------+   +-------------------------------+
            |    PostgreSQL 15 + PostGIS    |   |       FastAPI ML Service      |
            |    Spatial Database & Flyway  |   |   - XGBoost v1.0.0 Model      |
            |    15 Schema Migrations       |   |   - TreeExplainer (SHAP)      |
            +-------------------------------+   +-------------------------------+
```

---

## 2. Service Startup Order & Environment Setup

Services must be launched in dependency order: **Database $\rightarrow$ ML Service $\rightarrow$ Backend $\rightarrow$ Mobile**.

### 2.1 Startup Order & Commands

#### Step 1: PostgreSQL 15 + PostGIS (Port 5432)
*Required for:* Persistent user accounts, road network geometries, safe zones, reports, and weather fallback.
- **Docker Compose Mode (Preferred if Docker Desktop is running):**
  ```powershell
  # Ensure Docker Desktop is launched from Windows Start Menu
  docker compose up -d postgres
  ```
- **Native Host Mode (If PostgreSQL is installed locally):**
  Ensure the PostgreSQL service is running on `127.0.0.1:5432` with PostGIS extensions enabled.
- **Status If Unavailable:** Standalone ML inference and Spring Boot unit/integration test suites operate safely without a live database.

#### Step 2: FastAPI Machine Learning Service (Port 8001)
*Required for:* Calibrated road-segment risk inference, feature imputation, and SHAP explainability.
```powershell
# Open a new PowerShell terminal
cd "C:\Users\91967\OneDrive\Desktop\Safe path AI\ml-service"

# 1. Activate the dedicated virtual environment
.\venv\Scripts\activate

# 2. Start Uvicorn ASGI server
python -m uvicorn app.main:app --host 127.0.0.1 --port 8001
```
*Expected Startup Output:*
```
INFO:     Started server process
INFO:     Waiting for application startup.
INFO:     Application startup complete.
INFO:     Uvicorn running on http://127.0.0.1:8001 (Press CTRL+C to quit)
```

#### Step 3: Spring Boot Backend Application (Port 8080)
*Required for:* Business logic, route comparison, A* search, and REST APIs.
```powershell
# Open a new PowerShell terminal
cd "C:\Users\91967\OneDrive\Desktop\Safe path AI\backend"

# 1. Provide required environment variables
$env:JWT_SECRET="safe_path_ai_academic_demo_secret_key_at_least_256_bits_length_12345678"
$env:POSTGRES_PASSWORD="your_postgres_password"
$env:ML_SERVICE_URL="http://localhost:8001"

# 2. Start application
.\mvnw.cmd spring-boot:run
```
*Expected Startup Output:*
```
2026-10-15T... INFO ... c.s.b.SafePathBackendApplication : Started SafePathBackendApplication in ... seconds
```

#### Step 4: Expo Mobile Application (Port 8081)
*Required for:* User interface, interactive map, route visualization, and reporting.
```powershell
# Open a new PowerShell terminal
cd "C:\Users\91967\OneDrive\Desktop\Safe path AI\mobile"

# Start Metro bundler
npx expo start
```
- Press `w` in the terminal to view in a desktop browser.
- Press `a` to open in a running Android Studio emulator.

---

### 2.2 Environment Variables & Client Base URLs

| Client Environment | Correct Base URL Configuration | Why This URL Is Required |
|---|---|---|
| **Web Browser (Desktop)** | `http://localhost:8080/api` | The browser runs on the same host operating system as the backend. |
| **Android Emulator** | `http://10.0.2.2:8080/api` | **CRITICAL:** The Android emulator runs in a separate virtual machine. `127.0.0.1` refers to the emulator phone itself; `10.0.2.2` is the special alias provided by Google to route to the host machine's loopback interface. |
| **Physical Phone (Expo Go)** | `http://<YOUR_LAN_IP>:8080/api`<br>*(e.g., `http://192.168.1.105:8080/api`)* | Physical mobile phones communicate over the local Wi-Fi router. The computer's local IPv4 address must be used, and port 8080 must be permitted through Windows Defender Firewall. |

---

## 3. Service Health-Check Verification

Run these health checks in PowerShell or any browser before demonstrating application features:

### 3.1 FastAPI ML Service Health Check
```powershell
Invoke-RestMethod -Uri "http://127.0.0.1:8001/health" | ConvertTo-Json
```
**Expected Response (HTTP 200):**
```json
{
  "status": "UP",
  "models_loaded": true,
  "active_model": "xgboost",
  "model_version": "1.0.0",
  "is_synthetic_demonstration": true
}
```
*Verification note:* Confirms active XGBoost model loading and explicit synthetic demonstration flagging.

### 3.2 Spring Boot Actuator Health Check
```powershell
Invoke-RestMethod -Uri "http://localhost:8080/actuator/health" | ConvertTo-Json
```
**Expected Response (HTTP 200):**
```json
{
  "status": "UP"
}
```

### 3.3 Spring Boot Public Health Endpoint
```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/health" | ConvertTo-Json
```
**Expected Response (HTTP 200):**
```json
{
  "status": "UP",
  "database": "UP",
  "mlService": "UP",
  "timestamp": "2026-10-15T..."
}
```

---

## 4. Feature-by-Feature Demonstration Guide

### 4.1 Route Comparison: FASTEST, BALANCED, and SAFEST

The A* routing engine evaluates three distinct paths using the multi-criteria cost formula:
$$\text{Cost}(e) = \alpha \cdot \text{time}_{\text{norm}} + \beta \cdot \text{distance}_{\text{norm}} + \gamma \cdot \text{risk}_{\text{norm}}$$

| Mode | Alpha ($\alpha$) | Beta ($\beta$) | Gamma ($\gamma$) | Routing Characteristic |
|---|---|---|---|---|
| **FASTEST** | `0.70` | `0.20` | `0.10` | Minimizes travel duration; operates independently of ML service availability. |
| **BALANCED** | `0.40` | `0.20` | `0.40` | Moderate compromise between travel time and risk. |
| **SAFEST** | `0.10` | `0.10` | `0.80` | Heavily penalizes high-risk edges; rejected with `HTTP 503` if ML is down. |

#### Demonstration Command:
```powershell
$routeRequest = @{
    startLat = 12.9716
    startLon = 77.5946
    endLat = 12.9800
    endLon = 77.6100
    travelMode = "WALKING"
    routePreference = "STANDARD"
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/api/routes/compare" -Method Post -Body $routeRequest -ContentType "application/json" | ConvertTo-Json -Depth 5
```

#### What to Look For:
1. `fastestRoute`: Lowest duration, may traverse streets with lower lighting or higher baseline crime density.
2. `safestRoute`: Higher percentage of lit streets (`litSegmentsPercentage`), lower relative risk, may trade +2 to +4 minutes of walking time.
3. `tradeOffExplanation`: Factual summary string explaining the empirical trade-off:
   `"SAFEST route trades +3.0 min (+250 m) for a +43.0 pt improvement in safety score (95% vs 60% lit streets)."`
4. `disclaimer`: Explicit statement that scores represent relative feature-based estimates, not guaranteed crime prevention.

---

### 4.2 Machine Learning Risk Inference & SHAP Explainability

SafePath AI uses TreeSHAP to provide transparent local feature attributions, explaining *why* a particular street received its risk score.

#### Demonstration Command (FastAPI directly):
```powershell
$explainPayload = @{
    segment_id = "demo-seg-cubbon-01"
    features = @{
        crime_density_500m = 0.35
        crime_density_1km = 0.40
        hour_of_day = 22
        day_of_week = 5
        lighting_score = 0.85
        activity_proxy = 0.60
        nearest_police_m = 250.0
        nearest_hospital_m = 900.0
        weather_precipitation = 0.0
        weather_visibility = 10.0
    }
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://127.0.0.1:8001/explain" -Method Post -Body $explainPayload -ContentType "application/json" | ConvertTo-Json -Depth 5
```

#### What to Look For:
1. `risk_score` and `safety_score`: Calibrated outputs where $\text{safety\_score} = 1.0 - \text{risk\_score}$.
2. `base_value`: The global expected value (dataset mean log-odds).
3. `top_risk_factors`: Features that increased the predicted risk (e.g., late night `hour_of_day = 22`).
4. `top_mitigating_factors`: Features that reduced predicted risk (e.g., strong lighting `lighting_score = 0.85`, close proximity to police `nearest_police_m = 250m`).
5. `reconstruction_valid: true`: Confirms the mathematical efficiency axiom of SHAP:
   $$\text{Base Value} + \sum \phi_i = \text{Model Output (Margin)}$$
6. `disclaimer`: Strict academic disclaimer that attributions reflect statistical model associations, not physical causation.

---

### 4.3 Safety Heatmap (Segment Risk Visualization)

The heatmap endpoint returns street segments within a bounded geographic viewport with color-coded safety classifications.

#### Demonstration Command:
```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/heatmap?minLat=12.970&minLon=77.590&maxLat=12.980&maxLon=77.600" | ConvertTo-Json -Depth 4
```

#### What to Look For:
1. `segments`: Array of LineString road segments with `relativeRiskScore`, `safetyScore`, and `colorHex`:
   - High Safety ($\ge 75$): Green (`#34C759`)
   - Moderate Safety ($50 - 74$): Yellow / Orange (`#FF9500`)
   - Lower Safety ($< 50$): Red / Coral (`#FF3B30`)
   - Unscored / ML Offline: Neutral Gray (`#8E8E93`)
2. Viewport constraint: Enforces $\Delta \le 0.15^\circ$ (~16 km). Submitting a larger span correctly returns `HTTP 400 Bad Request` to prevent server memory exhaustion.
3. Segments lacking observations are returned with `scoringStatus: "UNAVAILABLE"` and `riskCategory: "UNSCORED"`. They are **never** assumed to be safe.

---

### 4.4 Ambient Weather Context

Pedestrian routing incorporates ambient weather conditions from Open-Meteo without making ungrounded claims about crime causation.

#### Demonstration Command:
```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/weather?lat=12.9716&lon=77.5946" | ConvertTo-Json -Depth 4
```

#### What to Look For:
1. `observation`: Current temperature, precipitation rate, wind speed, relative humidity, visibility, and weather condition string.
2. `forecast`: Forward-looking precipitation probability.
3. `cached`: `true` on repeat calls within 30 minutes due to spatial-temporal quantization (~1.1 km grid).
4. `disclaimer`: Expresses that weather represents ambient environmental conditions and does not measure crime risk directly.
5. If Open-Meteo is unreachable, the system automatically falls back to recent database observations (< 3h old) or returns `available: false` without crashing route planning.

---

### 4.5 Verified Safe Zones (Emergency Assistance)

Publicly accessible assistance facilities (police stations, hospitals, 24/7 pharmacies, transit stations).

#### Demonstration Command:
```powershell
# Proximity search within 2000 meters
Invoke-RestMethod -Uri "http://localhost:8080/api/safe-zones?lat=12.9716&lon=77.5946&radiusMeters=2000" | ConvertTo-Json -Depth 3
```

#### What to Look For:
1. Public barrier-free access: No `Authorization: Bearer` header required (accessible during emergencies).
2. Facilities returned with exact Haversine distance in meters (`distanceMeters`).
3. Verification status: Only `VERIFIED` registry entries are returned.
4. Disclaimer: Clearly clarifies that facility existence does not guarantee real-time staffing or absolute personal safety.

---

### 4.6 Community Reports & Moderation Security Lifecycle

Demonstrates the security lifecycle from submission through rate limiting to moderator-only verification.

#### Step 1: Register and Login
```powershell
# 1. Register test user
$userBody = @{
    fullName = "Demo Walker"
    email = "walker@example.com"
    password = "SecurePassword123!"
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/api/auth/register" -Method Post -Body $userBody -ContentType "application/json"

# 2. Login to get JWT
$loginBody = @{
    email = "walker@example.com"
    password = "SecurePassword123!"
} | ConvertTo-Json

$loginResp = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" -Method Post -Body $loginBody -ContentType "application/json"
$token = $loginResp.token
$headers = @{ Authorization = "Bearer $token" }
```

#### Step 2: Submit Community Report
```powershell
$reportBody = @{
    reportType = "POOR_LIGHTING"
    description = "Streetlights completely out along this avenue."
    latitude = 12.9730
    longitude = 77.5960
    locationConfirmed = $true
} | ConvertTo-Json

$newReport = Invoke-RestMethod -Uri "http://localhost:8080/api/reports" -Method Post -Body $reportBody -Headers $headers -ContentType "application/json"
$newReport | ConvertTo-Json
```
*Expected Status:* `PENDING`.

#### Step 3: Verify Rate Limiting
Submitting 5 additional reports in rapid succession triggers `HTTP 429 Too Many Requests`, preventing denial-of-service spam.

#### Step 4: Verify Public Map Isolation
```powershell
# Query nearby reports (unauthenticated)
Invoke-RestMethod -Uri "http://localhost:8080/api/reports/nearby?lat=12.973&lon=77.596&radiusMeters=1000" | ConvertTo-Json
```
*Expected Outcome:* The newly submitted `PENDING` report does **NOT** appear on the public map. Only `VERIFIED` reports are visible.

#### Step 5: Moderator Verification (RBAC Enforcement)
```powershell
# An administrator or moderator approves the report
$verifyBody = @{
    status = "VERIFIED"
    verificationNote = "Municipal authority confirmed streetlight circuit repair in progress."
} | ConvertTo-Json

# Attempting this call with a normal user token returns HTTP 403 Forbidden
# Calling with ROLE_MODERATOR credentials transitions status to VERIFIED
```
*Privacy protection:* The public response scrubs reporter email, user ID, and phone number to prevent stalking or deanonymization.

---

## 5. Database Dependency Matrix

Understanding which features require a live PostgreSQL database and which can be demonstrated independently:

| Feature | Requires Live PostgreSQL | Can Run Standalone / With Mock Fixtures? | Technical Reason |
|---|---|---|---|
| **FastAPI ML Health (`/health`)** | **NO** | **YES** | Evaluates in-memory XGBoost model artifact (`models/xgboost/model.json`). |
| **FastAPI Risk Prediction (`/predict-risk`)** | **NO** | **YES** | Preprocesses features and computes XGBoost probability in Python runtime. |
| **FastAPI SHAP Explanation (`/explain`)** | **NO** | **YES** | Runs `shap.TreeExplainer` directly on model in memory. |
| **Weather Direct Query (`/api/weather`)** | **NO** | **YES** | Queries Open-Meteo external REST API directly; only uses DB for audit logging and fallback. |
| **Spring Boot Live ML Tests** | **NO** | **YES** | Uses Spring `RestClient` to talk directly to FastAPI port 8001 without DB. |
| **Spring Boot Automated Tests** | **NO** | **YES** | Comprehensive unit/integration suites execute against in-memory H2/H2GIS database. |
| **Mobile TypeScript Compilation** | **NO** | **YES** | Static analysis executes via Node.js compiler (`tsc --noEmit`). |
| **User Registration & Login** | **YES** | NO | Requires persistent `users` table for bcrypt credentials and JWT issuance. |
| **Live A* Route Search (`/api/routes`)** | **YES** | NO | Requires PostGIS `road_segments` table to build graph edges and query nodes. |
| **Live Safety Heatmap (`/api/heatmap`)** | **YES** | NO | Requires PostGIS spatial queries (`ST_Intersects` / `ST_MakeEnvelope`) over road network. |
| **Live Safe Zones Query (`/api/safe-zones`)** | **YES** | NO | Queries PostGIS `safe_zones` table using spatial indexes. |
| **Live Community Reports (`/api/reports`)** | **YES** | NO | Requires persistent `community_reports` table and foreign key constraints. |

---

## 6. Synthetic Demonstration Data & Scientific Limitations

SafePath AI adheres to strict academic honesty standards:

> ### ⚠️ Scientific & Demonstration Disclaimers
> 1. **Synthetic Training Data:**  
>    Due to privacy constraints and the absence of publicly available, high-precision street-level crime datasets in India (NCRB datasets provide district-level aggregates rather than GPS coordinates), the active ML models are trained on **reproducible synthetic fixtures**.
> 2. **Relative Risk vs Absolute Crime Probability:**  
>    The system outputs **relative risk scores** indicating comparative risk between road segments under given feature combinations. It does **not** predict absolute crime occurrences, probabilities, or guarantee physical security.
> 3. **Non-Causal Feature Attribution:**  
>    SHAP attributions indicate mathematical correlations within the trained model. A high feature weight for "poor lighting" means the model associates poor lighting with higher risk, not that repairing a streetlight physically prevents crime.
> 4. **Safe Zone Operational Status:**  
>    Listing a police station or hospital indicates verified registry existence; it does **not** guarantee real-time officer availability, doctor staffing, or personal protection.
> 5. **Unscored Streets are Not Safe:**  
>    Road segments lacking observation data are marked `UNSCORED` (neutral gray). The system strictly avoids defaulting unobserved segments to "safe".

---

## 7. Troubleshooting Common Errors

### Error 1: ML Service Connection Refused
- **Symptom:** Spring Boot logs `ML risk predictions unavailable: Connection refused: no further information: 127.0.0.1:8001`.
- **Cause:** FastAPI ML service daemon is not running on port 8001.
- **Resolution:** Open PowerShell, navigate to `ml-service`, activate virtualenv (`.\venv\Scripts\activate`), and start Uvicorn:
  `python -m uvicorn app.main:app --host 127.0.0.1 --port 8001`.
- **System Behavior:** Route comparison degrades gracefully; `FASTEST` route succeeds independently; `BALANCED` route is returned as `UNSCORED`; `SAFEST` is rejected with `HTTP 503`.

### Error 2: Mobile Network Request Failed
- **Symptom:** Expo app displays `Network request failed` when calling backend APIs.
- **Causes & Resolutions:**
  - **Android Emulator:** Ensure `EXPO_PUBLIC_API_URL` is set to `http://10.0.2.2:8080/api` (not `localhost`).
  - **Physical Device:** Ensure the phone is connected to the same Wi-Fi network and `EXPO_PUBLIC_API_URL` uses your computer's local LAN IP (e.g., `http://192.168.1.100:8080/api`).
  - **Windows Firewall:** Ensure Windows Firewall allows inbound connections on port 8080.

### Error 3: Docker Named Pipe Not Found
- **Symptom:** `open //./pipe/dockerDesktopLinuxEngine: The system cannot find the file specified.`
- **Cause:** Docker Desktop was started headlessly or is not running an active Windows desktop session.
- **Resolution:** Launch **Docker Desktop** from the Windows Start menu. Wait for the whale icon in the system tray to turn solid green, then execute `docker compose up -d`.

### Error 4: Community Report HTTP 429 Too Many Requests
- **Symptom:** API returns status `429 Too Many Requests` on report submission.
- **Cause:** In-memory sliding-window rate limiter triggered (maximum 5 reports per user per rolling 10-minute window).
- **Resolution:** Expected security behavior. Wait 10 minutes or authenticate with a different user account.

---

## 8. Final Empirical Verification Table

Status designations:
- **PASS:** Verified directly through runtime execution, automated test reports, or live HTTP network calls.
- **BLOCKED:** Cannot execute on current host environment without manual user system/desktop intervention.
- **NOT VERIFIED:** Static configuration passed, but physical device/hardware validation was not performed.

| Layer / Subsystem | Specific Verification Check | Empirical Evidence Source | Verification Status |
|---|---|---|---|
| **Containerization** | Docker Desktop Engine Connectivity | `docker version` / pipe inspection | **BLOCKED** (Daemon pipe stopped headlessly) |
| **Containerization** | Multi-Container Docker Compose | `docker compose up -d` | **BLOCKED** (Requires interactive Docker Desktop GUI) |
| **Database** | Live PostgreSQL / PostGIS 15 Instance | TCP socket probe (`127.0.0.1:5432`) | **BLOCKED / NOT RUN** (No host DB running) |
| **Database** | Flyway Migrations V1..V15 Schema | Migration SQL inspection (`V1` to `V15`) | **PASS** (15 migrations syntax & geography verified) |
| **ML Service** | FastAPI Health Check Endpoint | Live `GET http://127.0.0.1:8001/health` | **PASS** (HTTP 200, XGBoost model v1.0.0 loaded) |
| **ML Service** | Single-Segment Risk Prediction | Live `POST http://127.0.0.1:8001/predict-risk` | **PASS** (HTTP 200, calibrated risk/safety scoring) |
| **ML Service** | TreeSHAP Explainability & Additive Check | Live `POST http://127.0.0.1:8001/explain` | **PASS** (HTTP 200, `reconstruction_valid: true`) |
| **ML Service** | Batch Segment Risk Prediction | Live `POST http://127.0.0.1:8001/predict-risk/batch` | **PASS** (HTTP 200, multi-segment scoring verified) |
| **Cross-Service** | Spring Boot to FastAPI Live HTTP | [`LiveMlServiceConnectivityTest.java`](file:///c:/Users/91967/OneDrive/Desktop/Safe%20path%20AI/backend/src/test/java/com/safepath/backend/integration/LiveMlServiceConnectivityTest.java) | **PASS** (3/3 live integration tests passed) |
| **Backend Service** | A* Multi-Criteria Routing Engine | [`AStarRoutingEngineTest.java`](file:///c:/Users/91967/OneDrive/Desktop/Safe%20path%20AI/backend/src/test/java/com/safepath/backend/service/routing/AStarRoutingEngineTest.java) | **PASS** (11/11 tests passed; weight prioritization verified) |
| **Backend Service** | Defensive ML Degradation & Fallbacks | [`RouteComparisonServiceTest.java`](file:///c:/Users/91967/OneDrive/Desktop/Safe%20path%20AI/backend/src/test/java/com/safepath/backend/service/RouteComparisonServiceTest.java) | **PASS** (FASTEST succeeds, BALANCED unscores, SAFEST 503s) |
| **Backend Service** | Ambient Weather Client & Cache | [`WeatherServiceTest.java`](file:///c:/Users/91967/OneDrive/Desktop/Safe%20path%20AI/backend/src/test/java/com/safepath/backend/service/WeatherServiceTest.java) | **PASS** (Quantized cache & DB fallback verified) |
| **Backend Service** | Safe Zones Haversine Search | [`SafeZoneServiceTest.java`](file:///c:/Users/91967/OneDrive/Desktop/Safe%20path%20AI/backend/src/test/java/com/safepath/backend/service/SafeZoneServiceTest.java) | **PASS** (10/10 tests passed; barrier-free access verified) |
| **Backend Service** | Community Report Rate Limiting & RBAC | [`CommunityReportServiceTest.java`](file:///c:/Users/91967/OneDrive/Desktop/Safe%20path%20AI/backend/src/test/java/com/safepath/backend/service/CommunityReportServiceTest.java) | **PASS** (Max 5/10min, moderator verification, PII scrubbed) |
| **Backend Total** | Maven Surefire Test Suite | `backend/target/surefire-reports` | **PASS** (128 tests run, 0 failures, 0 errors, 0 skipped) |
| **Python Offline** | Ingestion, KDE, Leakage, Model Training | Pytest on `scripts/tests` | **PASS** (47 tests passed, 0 failures) |
| **Python ML Total** | Pytest Test Suites Combined | `pytest ml-service/tests scripts/tests` | **PASS** (63 tests passed, 0 failures) |
| **Mobile Client** | TypeScript Static Type Compilation | `.\node_modules\.bin\tsc.cmd --noEmit` | **PASS** (0 errors across entire mobile codebase) |
| **Mobile Client** | Android Emulator / Physical Device UI | Hardware / ADB verification | **NOT VERIFIED** (No emulator or phone connected) |

---

*SafePath AI — final-demo-checklist.md — Created 2026-10-15*
