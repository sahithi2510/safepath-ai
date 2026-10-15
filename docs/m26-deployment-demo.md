# SafePath AI — Milestone 26: Deployment Blocker Resolution & Reproducible Demonstration

> **Document Type:** Live Infrastructure Audit & Reproducible Demonstration Report  
> **Status:** AUDITED & VERIFIED (Standalone & Live Hybrid Validation)  
> **Date:** 2026-10-15  
> **Execution Environment:** Windows 11 Home (Build 26100) / WSL2 Linux 6.6.36.6 / Java Adoptium 21.0.12.1 / Python 3.11.5 / Node.js 18+ / Docker CLI 29.7.2  

---

## 1. Executive Summary

Milestone 26 establishes the definitive status of deployment readiness for SafePath AI. Building upon the Milestone 25 audit, this milestone rigorously diagnoses the Docker Desktop environment on the host machine, establishes standalone live service execution, verifies real HTTP/JSON cross-tier contracts between Spring Boot and FastAPI, audits and reconciles historical test counts against actual automated test reports, and details reproducible steps for a live system demonstration.

In compliance with `AGENTS.md` and `PROJECT-CONTRACT.md`:
- No synthetic test numbers or fabricated test counts are presented.
- Live database queries and Docker Compose orchestration are honestly reported as **BLOCKED / NOT RUN** on the host environment rather than falsely marked as passing.
- Real cross-service network calls (HTTP/JSON over loopback socket `127.0.0.1:8001`) are verified live with an automated integration test suite.
- Mobile runtime UI rendering is explicitly labeled **NOT VERIFIED** due to the absence of a connected Android emulator or physical device.

---

## 2. Environment Prerequisites

The following software versions were verified on the local host:

| Prerequisite | Installed Version / Path | Status | Verification Command |
|---|---|---|---|
| **Java Development Kit** | OpenJDK 21.0.12.1 (Adoptium build 21.0.12.1+1) | Verified | `.\mvnw.cmd -version` |
| **Python Virtual Env** | Python 3.11.5 (64-bit) in `ml-service/venv` | Verified | `.\ml-service\venv\Scripts\python.exe --version` |
| **Node.js & npm** | Node.js v18+ / npm via Expo CLI 51.0.38 | Verified | `npx expo --version` |
| **Docker CLI** | Docker CLI 29.7.2 (build a7dcaa6) | Verified | `docker --version` |
| **Docker Engine Context** | `desktop-linux` (`npipe:////./pipe/dockerDesktopLinuxEngine`) | Verified | `docker context inspect` |
| **WSL2 Subsystem** | WSL 2.4.13.0 (Kernel 6.6.36.6) | Verified | `wsl --status` |

---

## 3. Diagnostic Audit of Docker Desktop Blocker

### 3.1 Docker CLI and Context Inspection
```powershell
PS C:\Users\91967\OneDrive\Desktop\Safe path AI> docker version
Client:
 Version:           29.7.2
 API version:       1.53
 Go version:        go1.25.7
 Git commit:        a7dcaa6
 Built:             Wed Mar 11 08:34:04 2026
 OS/Arch:           windows/amd64
 Context:           desktop-linux

error during connect: Get "http://%2F%2F.%2Fpipe%2FdockerDesktopLinuxEngine/v1.53/version": open //./pipe/dockerDesktopLinuxEngine: The system cannot find the file specified.
```

### 3.2 WSL Subsystem Status
```powershell
PS C:\Users\91967\OneDrive\Desktop\Safe path AI> wsl --list --verbose
  NAME                   STATE           VERSION
* Ubuntu                 Stopped         2
  docker-desktop         Stopped         2
```

### 3.3 Daemon Root Cause Diagnosis
1. **Named Pipe Inaccessibility:** Docker CLI is configured to communicate with the named pipe `\\.\pipe\dockerDesktopLinuxEngine`.
2. **Headless Execution Constraint:** When `Docker Desktop.exe` is invoked headlessly or from a non-interactive shell background without active desktop GUI authorization, the process terminates:
   - Application binary: `C:\Users\91967\AppData\Local\Programs\DockerDesktop\Docker Desktop.exe`
   - WSL distro `docker-desktop` remains in state `Stopped` until the GUI bootstrap process completes.
3. **Safety Constraint Compliance:** In accordance with the project contract and system safety guidelines, no system settings, Windows features, WSL kernel configurations, or administrator privileges were modified without user consent.
4. **Impact:** Multi-container orchestration via `docker compose up -d` is **BLOCKED** on this machine in headless CLI mode.

---

## 4. Test Suite Audit & Reconciled Count Derivation

### 4.1 Investigation of the "197 Tests" Claim
The Milestone 24 and Milestone 25 documentation recorded a claim of "197 passed tests" (125 Java + 16 Python ML + Mobile 0 errors). However, 125 + 16 = 141. The previous documentation did not explicitly explain how the number 197 was calculated.

An exhaustive audit of the actual test files and test reports across the repository was conducted:

| Test Layer | Test Execution Framework | Report Source | Number of Tests | Status |
|---|---|---|---|---|
| **Backend Unit & Integration Tests** | JUnit 5 / Maven Surefire 3.2.5 | `backend/target/surefire-reports/*.xml` | **128 tests** (21 classes) | 128 PASSED (0 failures, 0 errors, 0 skipped) |
| **Python Offline Data & ML Scripts** | Pytest 8.3.3 | `scripts/tests/` | **47 tests** (11 files) | 47 PASSED |
| **FastAPI ML Service Endpoints** | Pytest 8.3.3 | `ml-service/tests/test_api.py` | **16 tests** (1 file) | 16 PASSED |
| **Mobile TypeScript Compilation** | TypeScript Compiler (`tsc --noEmit`) | `mobile/` | **0 errors** | 0 type errors |
| **Mobile Automated Unit Tests** | Jest / React Native Testing Library | None configured in `mobile/package.json` | **0 tests** | N/A (Not configured) |

### 4.2 Definitive Automated Test Count
- Total backend automated tests (Surefire reports): **128**
- Total Python automated tests (Pytest reports): **63** (47 in `scripts/tests` + 16 in `ml-service/tests`)
- **Total automated test cases across SafePath AI:** **191 tests**
- **Passing rate:** **100% (191 passed, 0 failed, 0 errored, 0 skipped)**
- Derivation method: Parsed and validated directly from XML test result reports in `backend/target/surefire-reports/TEST-*.xml` and pytest test execution logs.

---

## 5. Live Service Validation Matrix

Every check below was evaluated using live network commands, test executions, or service queries. No check is marked PASS based solely on code reading or configuration inspection.

| Verification Check | Target / Endpoint | Execution Method | Status | Observed Outcome / Evidence |
|---|---|---|---|---|
| **Docker Desktop Daemon** | `//./pipe/dockerDesktopLinuxEngine` | `docker info` | **BLOCKED** | Named pipe not found; WSL distribution `docker-desktop` stopped |
| **Docker Compose Services** | `docker compose up -d` | Docker Compose CLI | **BLOCKED** | Dependent on Docker Desktop daemon |
| **Live PostgreSQL / PostGIS** | `localhost:5432` | TCP socket probe (`127.0.0.1:5432`) | **BLOCKED / NOT RUN** | Port 5432 closed; no native PostgreSQL service running on host |
| **FastAPI ML Service Health** | `http://127.0.0.1:8001/health` | HTTP GET via `Invoke-RestMethod` | **PASS** | HTTP 200: `{"status":"UP","models_loaded":true,"active_model":"xgboost","model_version":"1.0.0","is_synthetic_demonstration":true}` |
| **FastAPI Single-Segment Prediction** | `http://127.0.0.1:8001/predict-risk` | HTTP POST with feature payload | **PASS** | HTTP 200: `risk_score: 0.007`, `safety_score: 0.993`, `risk_level: "VERY_LOW"`, `imputed_features: ["crime_density_1km", ...]` |
| **FastAPI SHAP Explainability** | `http://127.0.0.1:8001/explain` | HTTP POST with feature payload | **PASS** | HTTP 200: `reconstruction_valid: true`, `reconstruction_error: 0.0`, additive attributions verified |
| **FastAPI Batch Prediction** | `http://127.0.0.1:8001/predict-risk/batch` | HTTP POST with 2 segments | **PASS** | HTTP 200: Both segments scored; feature completeness = 0.20 |
| **Spring Boot -> FastAPI Live Connectivity** | `http://127.0.0.1:8001` via `MlServiceClientImpl` | `LiveMlServiceConnectivityTest.java` (3 live integration tests) | **PASS** | 3/3 tests passed: `testLiveFastApiHealthCheck`, `testLiveFastApiPredictRisk`, `testLiveFastApiPredictBatch` |
| **Backend Unit & Defensive Fallback Tests** | Spring Boot test context | `.\mvnw.cmd test` | **PASS** | 128 tests passed (including A*, weather fallback, rate limiter, RBAC) |
| **Python Offline Data Pipeline Tests** | Pytest on `scripts/tests` | `pytest scripts/tests` | **PASS** | 47 tests passed (KDE projection, leakage guard, spatial features, temporal features, XGBoost training) |
| **Mobile TypeScript Verification** | Mobile project root | `.\node_modules\.bin\tsc.cmd --noEmit` | **PASS** | 0 errors across all screens, navigation, and service definitions |
| **Mobile Runtime UI / Device Execution** | Expo Go / Android Emulator | `adb devices` | **NOT VERIFIED** | No Android emulator or physical device attached to host machine |

---

## 6. Detailed Findings: Cross-Tier Integration & Fixes

### 6.1 Spring Boot HTTP/2 Cleartext Negotiation with Uvicorn
- **Symptom:** During standalone live integration testing, Spring 6.1 `RestClient` utilizing Java 21's default `JdkClientHttpRequestFactory` initiated an HTTP/2 cleartext upgrade (`Upgrade: h2c`) to the Uvicorn ASGI server.
- **Root Cause:** Uvicorn logs reported `WARNING: Unsupported upgrade request` and `WARNING: Invalid HTTP request received`, which caused Uvicorn to discard the request body and return `422 Unprocessable Entity (field: body missing)`.
- **Resolution:**
  - Configured `SimpleClientHttpRequestFactory` in the live connectivity integration harness, matching the existing `MlServiceConfig.java` production configuration.
  - Hardened [MlServiceClientImpl.java](file:///c:/Users/91967/OneDrive/Desktop/Safe%20path%20AI/backend/src/main/java/com/safepath/backend/service/ml/MlServiceClientImpl.java) `handleRestException` to log and expose `e.getResponseBodyAsString()` on 400 and 422 errors, ensuring upstream validation details are transparently diagnosed rather than obscured.

### 6.2 Defensive Routing Fallback Verification
The backend test suite ([RouteComparisonServiceTest.java](file:///c:/Users/91967/OneDrive/Desktop/Safe%20path%20AI/backend/src/test/java/com/safepath/backend/service/RouteComparisonServiceTest.java) and [RouteServiceMlIntegrationTest.java](file:///c:/Users/91967/OneDrive/Desktop/Safe%20path%20AI/backend/src/test/java/com/safepath/backend/service/RouteServiceMlIntegrationTest.java)) verifies non-blocking and defensive routing under all service conditions:
1. **ML Available:** `FASTEST`, `BALANCED`, and `SAFEST` routes are computed with calibrated segment risk scores and empirical trade-off summaries.
2. **ML Connection Refused / Outage:**
   - `FASTEST` mode completes independently based purely on travel time without degradation.
   - `BALANCED` mode falls back to geometric edge costs, setting `mlServiceDegraded: true` and designating safety scores as `null` (UNSCORED).
   - `SAFEST` mode is rejected with `HTTP 503 Service Unavailable`, strictly preventing unscored routes from being misrepresented as safe.

---

## 7. Step-by-Step Reproducible Demonstration Guide

Follow these exact steps to demonstrate SafePath AI on the local host.

### Step 1: Start the FastAPI ML Service
Open PowerShell in the project directory:
```powershell
cd "ml-service"
# Activate the existing virtual environment
.\venv\Scripts\activate

# Start Uvicorn daemon on port 8001
python -m uvicorn app.main:app --host 127.0.0.1 --port 8001
```
*Expected log output:*
```
INFO:     Started server process
INFO:     Waiting for application startup.
INFO:     Application startup complete.
INFO:     Uvicorn running on http://127.0.0.1:8001 (Press CTRL+C to quit)
```

### Step 2: Validate ML Endpoints via REST Calls
Open a separate PowerShell terminal:
```powershell
# 1. Health check
Invoke-RestMethod -Uri "http://127.0.0.1:8001/health" | ConvertTo-Json

# 2. Risk Prediction
$payload = @{
    segment_id = "demo-seg-101"
    features = @{
        hour_of_day = 23
        lighting_score = 0.2
        crime_density_500m = 0.45
    }
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://127.0.0.1:8001/predict-risk" -Method Post -Body $payload -ContentType "application/json" | ConvertTo-Json

# 3. SHAP Explainability
Invoke-RestMethod -Uri "http://127.0.0.1:8001/explain" -Method Post -Body $payload -ContentType "application/json" | ConvertTo-Json -Depth 4
```

### Step 3: Run Backend Live Integration & Contract Verification
With the FastAPI service running in Step 1:
```powershell
cd "backend"
# Run the live network test against the active FastAPI daemon
.\mvnw.cmd test -Dtest=LiveMlServiceConnectivityTest
```
*Expected outcome:*
```
[INFO] Running com.safepath.backend.integration.LiveMlServiceConnectivityTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

### Step 4: Run the Complete Offline Python & Backend Regression Suites
```powershell
# Run all 63 Python tests
.\ml-service\venv\Scripts\python.exe -m pytest ml-service/tests scripts/tests

# Run all 128 Backend tests
cd "backend"
.\mvnw.cmd test
```
*Expected outcome:*
- Python: `63 passed in ~25s`
- Java: `Tests run: 128, Failures: 0, Errors: 0, Skipped: 0`

### Step 5: Mobile Application Demonstration (Metro & Web Preview)
```powershell
cd "mobile"

# 1. Verify TypeScript types
.\node_modules\.bin\tsc.cmd --noEmit

# 2. Start Expo Metro bundler
npx expo start
```
- Press `w` in the terminal to launch the web preview in your browser (`http://localhost:8081`).
- To test on Android Emulator: Ensure Android Studio emulator is running, set `EXPO_PUBLIC_API_URL=http://10.0.2.2:8080/api`, and press `a`.
- To test on Physical Device: Install Expo Go from Google Play, ensure your phone is on the same local Wi-Fi, set `EXPO_PUBLIC_API_URL=http://<YOUR_LAN_IP>:8080/api`, and scan the terminal QR code.

---

## 8. Safe Cleanup Instructions

To shut down any running processes cleanly without data loss:

1. **Stop FastAPI Uvicorn Daemon:**
   - In the Uvicorn terminal window, press `Ctrl + C`.
   - Or kill via PowerShell:
     ```powershell
     Get-Process python -ErrorAction SilentlyContinue | Where-Object { $_.Path -like "*Safe path AI*" } | Stop-Process
     ```
2. **Stop Expo Metro Bundler:**
   - In the Expo terminal window, press `Ctrl + C`.
3. **Database and Docker Cleanup:**
   - No Docker containers or persistent volumes were started or altered. No cleanup commands or volume purges are needed.

---

## 9. Summary of Limitations

1. **Host-Level Docker Desktop Requirement:**  
   Docker Desktop requires an interactive desktop session on Windows 11 to launch the GUI engine and open the named pipe `\\.\pipe\dockerDesktopLinuxEngine`. Running Docker Compose in headless background agents is blocked until Docker Desktop is started interactively from the Start menu.
2. **PostgreSQL/PostGIS Standalone Requirement:**  
   Because Docker Desktop was blocked and no standalone PostgreSQL 15 instance was installed on port 5432, live PostgreSQL tests against a persistent database are documented as **BLOCKED / NOT RUN**. All repository spatial contracts are verified via Spring Boot mock integration tests and Flyway migration SQL inspections.
3. **Mobile Runtime Rendering:**  
   The mobile codebase passes full static type checking (`0 errors`), but physical rendering and touch navigation remain **NOT VERIFIED** until demonstrated on an active emulator or device.
