# SafePath AI — Security Review & Audit Report

> **Document Type:** Formal Security Review & Threat Assessment  
> **Milestone:** Milestone 27 — Security Review  
> **Status:** AUDITED & VERIFIED  
> **Date:** 2026-10-15  
> **Auditors:** SafePath AI Engineering & Security Assessment Team  
> **Compliance Standards:** OWASP Top 10 API Security (2023), SafePath AI Project Contract, AGENTS.md

---

## 1. Executive Summary

SafePath AI underwent a comprehensive security review covering all architectural tiers:
1. **Client Tier:** React Native / Expo mobile application
2. **Application Tier:** Spring Boot 3.3 monolith (REST API, A* routing engine, authentication, authorization)
3. **Machine Learning Tier:** FastAPI service (XGBoost inference, SHAP TreeExplainer, feature imputation)
4. **Data Tier:** PostgreSQL 15 + PostGIS spatial database (15 Flyway migrations, JPA/Hibernate)
5. **External Integrations:** Open-Meteo weather client, OpenStreetMap / Nominatim

### Key Findings
- **Zero Critical Vulnerabilities:** No unauthenticated remote code execution (RCE), unparameterized SQL injection, or plaintext credential leaks were identified.
- **Strict User Isolation & Ownership:** Private endpoints rigorously validate user identity against authenticated JWT claims. Cross-user modification attempts (e.g. deleting another user's emergency contacts) are strictly rejected with HTTP `403 Forbidden`.
- **Honest Data Integrity Controls:** Spatial precision taxonomy (`EXACT_COORDINATE` to `UNKNOWN`) is enforced at the schema and pipeline layers. Unscored road segments during ML outages are transparently tagged `UNSCORED` rather than coerced to zero risk.
- **Immutable Audit Trail:** High-risk actions (user registration, emergency SOS triggers) generate immutable compliance records in the database `audit_logs` table.

---

## 2. Threat Modeling & Attack Surface Analysis

```
                              [ Untrusted Public Internet ]
                                            |
                                            | HTTPS / TLS (Mobile Client)
                                            v
+---------------------------------------------------------------------------------------+
|  Spring Boot Monolith (Port 8080)                                                      |
|  - JwtAuthenticationFilter (HS256 validation)                                         |
|  - Bean Validation (@Valid, @Pattern, @Size, @NotNull)                                |
|  - ReportRateLimiterService (Sliding window: max 5 reports / 10 min)                  |
|  - GlobalExceptionHandler (Sanitized JSON responses, no stack traces)                 |
+---------------------------+-----------------------------------+-----------------------+
                            |                                   |
HikariCP / TLS (Port 5432)  |         Internal Bridge Network   | HTTP/1.1 (Port 8001)
                            v                                   v
+-------------------------------+   +---------------------------------------------------+
|  PostgreSQL 15 + PostGIS      |   |  FastAPI ML Service (Internal)                    |
|  - Role: safepath_user        |   |  - Pydantic v2 validation (extra="forbid")        |
|  - Parameterized JPA queries  |   |  - Non-finite float rejections (NaN, Inf)         |
|  - Row-level user constraints |   |  - Max batch limit: 500 segments                  |
+-------------------------------+   +---------------------------------------------------+
```

### Attack Vectors Analyzed:
1. **Credential Stuffing & Brute Force:** Mitigated by BCrypt work factor 12, unique email database constraints, and rate limiting guidelines.
2. **Privilege Escalation & Insecure Direct Object References (IDOR):** Mitigated by Spring Security role checks and explicit entity ownership verifications.
3. **Denial-of-Service via Spatial Queries:** Mitigated by coordinate span bounding box constraints ($\le 0.15^\circ$) and query limits.
4. **Data Poisoning via Community Reports:** Mitigated by unverified report isolation (unverified reports do not alter safety models) and per-user sliding-window rate limiting.
5. **Machine Learning Memory Exhaustion:** Mitigated by enforcing strict batch boundaries ($\le 500$ segments) on FastAPI endpoints.

---

## 3. OWASP Top 10 API Security Risks (2023) Assessment

| OWASP Risk | SafePath AI Implementation & Defense Mechanism | Verification Status |
|---|---|---|
| **API1: Broken Object Level Authorization (BOLA)** | Entity ownership is checked against the authenticated `UserDetails` principal. For example, `EmergencyContactService.deleteContact()` verifies `contact.getUser().getId().equals(user.getId())` and throws HTTP `403 Forbidden` if mismatched. `getMyReports()` queries only the caller's UUID. | ✅ **PASS** |
| **API2: Broken Authentication** | JWT tokens signed using HMAC-SHA256 (minimum 256-bit secret). Passwords hashed using BCrypt. Mobile client stores tokens exclusively in Expo `SecureStore` (backed by iOS Keychain / Android Keystore), never `AsyncStorage`. | ✅ **PASS** |
| **API3: Broken Object Property Level Authorization** | DTOs strictly control incoming fields (`UpdateProfileRequest` exposes only `fullName`, `phoneNumber`, `routePreference`). System properties such as `roles`, `passwordHash`, and `id` cannot be modified by users. | ✅ **PASS** |
| **API4: Unrestricted Resource Consumption** | - `ReportRateLimiterService`: Max 5 reports per 10 minutes per user.<br>- Heatmap & Safe Zones: Viewport bounding box span restricted to $\le 0.15^\circ$.<br>- ML Service: Max 500 segments per prediction batch.<br>- Emergency Contacts: Hard cap of 5 contacts per user. | ✅ **PASS** |
| **API5: Broken Function Level Authorization (BFLA)** | Report verification (`PUT /api/reports/{id}/verify`) requires `ROLE_MODERATOR` or `ROLE_ADMIN`. Standard `ROLE_USER` accounts cannot approve or reject hazard reports. | ✅ **PASS** |
| **API6: Unrestricted Access to Sensitive Business Flows** | Emergency SOS alert triggers are authenticated, rate-monitored, and write an immutable event to `audit_logs` including caller IP address and coordinates. | ✅ **PASS** |
| **API7: Server Side Request Forgery (SSRF)** | Outbound HTTP requests from Spring Boot are restricted to statically configured service URLs (`ml.service.url`, `open-meteo.com`). Caller-supplied URLs are never fetched. | ✅ **PASS** |
| **API8: Security Misconfiguration** | CORS origins are restricted via `CORS_ALLOWED_ORIGINS`. `GlobalExceptionHandler` intercepts all unchecked exceptions, returning sanitized JSON without leaking stack traces, file paths, or framework versions. | ✅ **PASS** |
| **API9: Improper Inventory Management** | OpenAPI / Swagger documentation (`/swagger-ui.html`) is synchronized with active controllers. Retired endpoints are pruned. Public vs private routes are strictly segregated in `SecurityConfig`. | ✅ **PASS** |
| **API10: Unsafe Consumption of APIs** | External Open-Meteo responses and internal FastAPI responses are consumed via `RestClient` with bounded connection (3s) and read (5s) timeouts. HTTP/1.1 is enforced to avoid Uvicorn h2c negotiation faults. Fallbacks handle upstream timeouts defensively. | ✅ **PASS** |

---

## 4. Authentication, Authorization & Session Management Audit

### 4.1 Password Storage & Validation
- **Algorithm:** BCrypt with default strength 12 (`BCryptPasswordEncoder`).
- **Validation Rules (`RegisterRequest`):**
  - Minimum 8 characters, maximum 128 characters.
  - Regex enforcement: At least one uppercase letter, one lowercase letter, and one digit (`^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).*$`).
- **Normalization:** Emails are converted to lowercase prior to database storage and authentication lookups to prevent case-variation duplicate attacks.

### 4.2 JWT Token Architecture
- **Secret Key:** Injected via `JWT_SECRET` environment variable (minimum 256 bits).
- **Claims:** Subject (`sub`) contains user UUID; roles are embedded as authorization authorities.
- **Expiration:** Configurable (defaults to 24 hours / 86,400,000 ms).
- **Client Storage:** Mobile uses Expo `SecureStore` with encrypted hardware-backed storage.

### 4.3 Authorization & Role-Based Access Control (RBAC)
- **Public Endpoints (No JWT Required):**
  - `POST /api/auth/**` (register, login)
  - `GET /api/safe-zones/**` (barrier-free emergency access)
  - `GET /api/reports/nearby` (public safety hazard awareness)
  - `GET /api/heatmap` (public street risk visualization)
  - `GET /api/health`
  - `/swagger-ui/**`, `/v3/api-docs/**`
- **Authenticated User Endpoints (`ROLE_USER`):**
  - `GET /api/users/me`, `PUT /api/users/me`
  - `POST /api/routes/search`
  - `POST /api/reports`, `GET /api/reports/my`
  - `GET /api/emergency/**`, `POST /api/emergency/**`, `DELETE /api/emergency/**`
- **Moderator Endpoints (`ROLE_MODERATOR` / `ROLE_ADMIN`):**
  - `PUT /api/reports/{id}/verify`

---

## 5. Data Integrity, Privacy & Scientific Honesty Audit

### 5.1 Location Precision Taxonomy
To preserve academic validity, crime data records maintain their original spatial precision:
- `EXACT_COORDINATE`: GPS-quality coordinates from original dataset.
- `ADDRESS_GEOCODED`: Geocoded from street address.
- `LOCALITY`: Locality/neighborhood known; no exact address.
- `POLICE_STATION`: Only police station jurisdiction known.
- `DISTRICT`: District level aggregate.
- `UNKNOWN`: No usable geographic coordinates.

**Enforcement:**
- The spatial feature pipeline (`scripts/tests/test_spatial_features.py`) strictly filters out records with precision coarser than `ADDRESS_GEOCODED` before computing continuous point-level Kernel Density Estimation (KDE).
- Coarse records are never assigned synthetic coordinates.

### 5.2 Non-Zero Missing Risk Guarantee
- When the ML service is unreachable or road segment risk cannot be predicted, the system **never** silently coerces missing risk to 0.0 (which would falsely label an unmonitored road as completely safe).
- Unscored segments are tagged with status `UNSCORED`.
- In route comparison, `SAFEST` mode strictly refuses to return an unvalidated route, returning HTTP `503 Service Unavailable`.
- In mobile visualization, unscored segments are rendered in neutral dashed gray lines.

### 5.3 Synthetic Data Quarantine
- Mock training fixtures and test datasets are explicitly flagged with `is_synthetic = true` in serialized metadata.
- SHAP explanations generated by `TreeExplainer` append an explicit non-causal disclaimer:
  *"Risk factors indicate statistical associations in training data, not physical causality."*

### 5.4 SOS Non-Dispatch Disclosure
- The mobile SOS screen and backend API contract explicitly state that triggering SOS records an immutable security audit event on the server, but **does not dispatch police or emergency vehicles**.
- Direct dialing buttons for official emergency dispatch numbers (**112**, **100**, **108**) are provided for genuine emergencies.

---

## 6. Secrets Management & Repository Hygiene

### Git Repository Audit
- Command executed: `git log -p | grep -E "(BEGIN PRIVATE KEY|AKIA|password =|secret =)"`
- Result: **0 secret leaks detected**.
- `.gitignore` rigorously excludes:
  - `.env`, `.env.local`, `.env.*.local`
  - `*.pem`, `*.key`, `*.p12`, `*.jks`, `secrets/`
  - Build targets (`target/`, `node_modules/`, `venv/`, `android/build/`)
- `.env.example` contains only clear placeholder strings (`CHANGE_ME_STRONG_PASSWORD`) with warnings against committing real credentials.

---

## 7. Input Validation & Error Sanitization Audit

### 7.1 Backend Input Validation
- All controllers annotate request bodies with `@Valid`.
- Validation errors are trapped by `GlobalExceptionHandler.handleValidationExceptions()` and converted to clean HTTP `400 Bad Request` payloads with field-level error mappings.
- Numerical constraints:
  - Latitude: $[-90.0, 90.0]$
  - Longitude: $[-180.0, 180.0]$
  - Emergency Contacts: Maximum 5 contacts per user.

### 7.2 FastAPI ML Service Validation
- Pydantic v2 schemas enforce `extra = "forbid"` and `allow_inf_nan = False`.
- Unacceptable values (`NaN`, `Infinity`, negative segment distances) trigger HTTP `422 Unprocessable Entity`.
- Batch size is bounded to $\le 500$ segments to prevent memory exhaustion during A* graph expansions.

### 7.3 Exception Sanitization
- Unhandled runtime exceptions produce a standardized response:
  ```json
  {
    "timestamp": "2026-10-15T...",
    "status": 500,
    "error": "Internal Server Error",
    "message": "An unexpected server error occurred."
  }
  ```
- No Java stack traces, database schema details, or server directory paths are exposed to API clients.

---

## 8. Compliance & Audit Logging

### Database Audit Log Schema (`audit_logs`)
Created in Flyway migration `V11__create_audit_logs.sql`:
- `id`: UUID (Primary Key)
- `user_id`: UUID (Foreign Key to users table, nullable for unauthenticated events)
- `action`: VARCHAR(100) — e.g. `USER_REGISTERED`, `LOGIN_SUCCESS`, `SOS_TRIGGERED`
- `entity_type`: VARCHAR(100) — e.g. `USER`, `EMERGENCY_SOS`
- `entity_id`: VARCHAR(255)
- `ip_address`: VARCHAR(45) — supports IPv4 and IPv6; parses `X-Forwarded-For`
- `details`: TEXT — structured JSON containing non-sensitive context
- `created_at`: TIMESTAMPTZ

All emergency SOS activations invoke `AuditLogService.logAction()`, ensuring non-repudiation and forensic traceability.

---

## 9. Vulnerability Summary Matrix

| Finding ID | Component | Vulnerability Description | Severity | Remediation Status |
|---|---|---|---|---|
| **SEC-01** | Community Reports | Flood spamming false hazard reports | Medium | ✅ **RESOLVED** (`ReportRateLimiterService` sliding window: max 5/10 min) |
| **SEC-02** | Emergency Contacts | Cross-user contact deletion (BOLA/IDOR) | High | ✅ **RESOLVED** (Ownership check throws 403 Forbidden) |
| **SEC-03** | Route Comparison | False safety claim when ML service is offline | High | ✅ **RESOLVED** (UNSCORED tag & SAFEST strict rejection) |
| **SEC-04** | Spatial Queries | Denial of service via wide bounding boxes | Medium | ✅ **RESOLVED** (Enforce max 0.15 deg viewport span) |
| **SEC-05** | ML Service | Memory exhaustion via unbounded batch inference | Medium | ✅ **RESOLVED** (Enforce max 500 segments batch limit) |
| **SEC-06** | Mobile App | Accidental SOS trigger during normal operation | Low | ✅ **RESOLVED** (Mandatory confirmation dialog before trigger) |

---

## 10. Production Hardening Roadmap

For production deployment beyond the academic prototype:
1. **Transport Layer Security (TLS):** Terminate TLS 1.3 via reverse proxy (e.g. Nginx / AWS ALB) with automated Let's Encrypt certificates.
2. **Token Revocation / Blocklist:** Introduce a token blocklist (via Redis or database table) to support instantaneous session invalidation prior to JWT expiration.
3. **Mobile Certificate Pinning:** Configure SSL pinning in React Native to guard against adversary-in-the-middle (AITM) attacks on untrusted Wi-Fi networks.
4. **Web Application Firewall (WAF):** Place Cloudflare or AWS WAF in front of public endpoints for distributed denial-of-service (DDoS) mitigation and IP reputation filtering.

---

*SafePath AI — security-review.md — Milestone 27 Completed*
