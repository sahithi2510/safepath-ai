# ============================================================
# AGENTS.md — SafePath AI Development Rules & Constraints
# ============================================================
# This file is the authoritative behavioral guide for any AI
# agent, collaborator, or developer working on this project.
# All rules herein are NON-NEGOTIABLE.
# ============================================================

## 1. Project Identity

**SafePath AI** is a final-year academic software engineering project.
It must be treated as a serious, production-quality codebase, not a demo or toy application.

---

## 2. Non-Negotiable Data Integrity Rules

These rules exist to preserve **academic validity** and **scientific honesty**.

### 2.1 NEVER Fabricate Data

- Do NOT invent crime records.
- Do NOT invent latitude/longitude coordinates for crimes that have no coordinates.
- Do NOT invent police station, hospital, or pharmacy locations.
- Do NOT invent road segment data.
- Do NOT invent weather readings.
- Do NOT invent ML accuracy or evaluation metrics.
- Do NOT invent API responses and present them as real.
- Do NOT invent research findings.

### 2.2 Handle Missing Data Honestly

- If real coordinates are unavailable, store `location_precision = UNKNOWN | DISTRICT | LOCALITY | POLICE_STATION`.
- Do NOT silently convert approximate locations into exact coordinates.
- Do NOT use district-level crime data for exact road-segment ML training unless the spatial precision is explicitly documented and justified.
- Clearly document any limitation caused by missing data.

### 2.3 Location Precision Taxonomy

Crime data must be stored with one of these precision levels:

| Level            | Meaning                                              |
|------------------|------------------------------------------------------|
| `EXACT_COORDINATE` | GPS-quality coordinates from the original dataset |
| `ADDRESS_GEOCODED` | Address present; geocoded using a legitimate service |
| `LOCALITY`       | Locality/neighbourhood known; no exact address       |
| `POLICE_STATION` | Only police station is known                         |
| `DISTRICT`       | Only district is known                               |
| `UNKNOWN`        | No usable geographic information                     |

### 2.4 ML Honesty

- Do NOT report model metrics until the model has **actually been trained and evaluated** on real data.
- Do NOT claim a model is trained unless it is.
- Do NOT use arbitrary thresholds without documenting them as **IMPLEMENTATION CHOICES**.
- Do NOT fabricate SHAP values or explanations.

### 2.5 Demo / Mock Data

- All mock or synthetic data **must** be clearly labelled `DEMO DATA` or `MOCK DATA`.
- Demo data must be **isolated** from production data flows.
- The application must have a clear distinction between demo mode and real-data mode.

---

## 3. Architecture Rules

| Layer             | Responsibility                                           |
|-------------------|----------------------------------------------------------|
| React Native Expo | UI, map display, user interaction, API calls only        |
| Spring Boot       | Auth, routing, reports, safe zones, DB access, A* engine |
| PostgreSQL/PostGIS| Persistent data, spatial queries                         |
| FastAPI           | ML inference, SHAP, feature preprocessing                |
| Python scripts    | Offline data processing, model training, evaluation      |

- Do **NOT** put ML model training inside Spring Boot.
- Do **NOT** put routing logic inside React Native.
- Do **NOT** train models on the mobile device.
- Do **NOT** expose database entities directly from API controllers.
- Do **NOT** store secrets in source code.
- Do **NOT** commit `.env` files to Git.

---

## 4. Routing Algorithm Rules

- Use **A\*** as the primary algorithm.
- The cost function is:
  `cost = α * normalized_time + β * normalized_distance + γ * normalized_risk`
  where `α + β + γ = 1`.
- Document all weight choices as **IMPLEMENTATION CHOICES** unless empirically optimized.
- Do NOT use unexplained magic numbers.
- Route modes:
  - `FASTEST`: prioritize travel time.
  - `BALANCED`: balance time and risk.
  - `SAFEST`: prioritize risk minimization.

---

## 5. Safety Score Rules

- The ML output and user-facing safety score are **separate concepts**.
- If using a 0–100 safety score, document **exactly** how it is derived.
- Risk categories (`Very Unsafe`, `Unsafe`, `Moderate`, `Safe`, `Very Safe`) are **application-defined**, not universal scientific categories. Document this.

---

## 6. Development Workflow Rules

1. Before beginning any milestone, **read `docs/development-progress.md`**.
2. Identify the **next incomplete milestone**.
3. Inspect only the **relevant project files**.
4. Implement the milestone.
5. Run appropriate tests/build checks.
6. Fix important errors.
7. Update `docs/development-progress.md`.
8. Continue to the next milestone only when the current one is **stable**.

- Do NOT repeatedly rebuild completed features.
- Do NOT reread the entire repository unnecessarily.
- Keep `development-progress.md` accurate at all times.

---

## 7. Security Rules

- Use **JWT** for authentication.
- Hash passwords using **bcrypt**.
- Use environment variables for all secrets.
- Configure **CORS** explicitly.
- Use **HTTPS** in production.
- Use **Bean Validation** on all API inputs.
- Implement **global exception handling** to avoid leaking stack traces.
- Store mobile tokens using **Expo SecureStore**.

---

## 8. Testing Rules

- Write meaningful tests — not just coverage padding.
- Critical behaviors that MUST be verified by tests:
  - `FASTEST` route prioritizes travel time over safety.
  - `SAFEST` route prioritizes safety over time.
  - `BALANCED` route produces a measurable compromise.
- Do NOT simply assert these behaviors — write tests that **verify** them with concrete scenarios.

---

## 9. Community Reports Rules

- Unverified reports must have **limited or no direct influence** on the primary safety model.
- A single unverified report must NOT make a road arbitrarily unsafe.
- Report states: `PENDING → VERIFIED | REJECTED`.

---

## 10. Git Rules

- Use logical commits after each stable milestone.
- Example format: `feat: add routing engine with A* implementation`
- **NEVER** commit secrets, `.env` files, or credentials.
- Use `.gitignore` correctly.

---

## 11. Documentation Rules

- Distinguish between:
  - `FACT` — empirically supported
  - `ASSUMPTION` — reasonable but unverified
  - `IMPLEMENTATION CHOICE` — arbitrary decision that should be documented
  - `LIMITATION` — known gap or weakness

- Maintain `docs/development-progress.md` as the **persistent project state file**.
- Create `docs/final-implementation-summary.md` only at the **end**, containing only what was **actually** implemented.

---

## 12. What NOT to Build

- Do NOT add Kubernetes.
- Do NOT add Kafka.
- Do NOT add Redis (unless there is a genuine, documented requirement).
- Do NOT add unnecessary microservices.
- Do NOT add complex cloud infrastructure.
- Keep the stack: **Spring Boot monolith + FastAPI ML service + PostgreSQL + React Native Expo**.

---

*Last updated: 2026-10-08*
*Version: 1.0.0*
