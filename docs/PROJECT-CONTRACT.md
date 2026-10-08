# SafePath AI — Project Contract

> **Version:** 1.0.0  
> **Date:** 2026-10-08  
> **Status:** BINDING

This document establishes the non-negotiable rules for the SafePath AI project.
Any deviation from these rules compromises the academic and scientific integrity of the project.

---

## 1. Core Principle: Scientific Honesty

This is a final-year academic project. Its value lies in its intellectual integrity, not in superficially impressive claims.

**If data is missing — say so.**  
**If a model is not trained — say so.**  
**If accuracy is unknown — say so.**  
**If a threshold is arbitrary — say so.**

---

## 2. Data Fabrication — PROHIBITED

The following are absolutely prohibited:

| Prohibited Action | Consequence |
|------------------|-------------|
| Inventing crime coordinates | Academic fraud |
| Inventing police/hospital locations | Academic fraud |
| Inventing road network data | Academic fraud |
| Inventing ML accuracy metrics | Academic fraud |
| Presenting mock API data as real | Academic fraud |
| Silently converting district-level to street-level data | Scientific invalidity |

---

## 3. Location Precision Contract

Every crime record must store its `location_precision`:

| Precision | Definition |
|-----------|-----------|
| `EXACT_COORDINATE` | GPS-quality coordinate from the original dataset |
| `ADDRESS_GEOCODED` | Geocoded from a real address using a legitimate service; original address preserved |
| `LOCALITY` | Only locality/neighbourhood known |
| `POLICE_STATION` | Only associated police station known |
| `DISTRICT` | Only district known |
| `UNKNOWN` | No usable geographic information |

**Rules:**
- Do NOT silently upgrade precision.
- Do NOT use `DISTRICT` records for road-segment ML training without explicit, documented justification.
- Geocoding must use a real service (e.g., Nominatim). The original address must be preserved.

---

## 4. ML Honesty Contract

| Rule | Requirement |
|------|-------------|
| No fabricated metrics | Report metrics only after actual training and evaluation |
| No fabricated SHAP | Only use SHAP values from a real model |
| No false claims | Do not claim the model is trained unless it is |
| Document thresholds | All safety score thresholds must be documented as `IMPLEMENTATION CHOICE` |
| Proper methodology | Train/validation/test split; no temporal leakage |

---

## 5. Architecture Contract

| Component | Must | Must NOT |
|-----------|------|---------|
| React Native | Handle UI, display, API calls | Contain routing logic or ML |
| Spring Boot | Auth, routing, A*, DB, reports | Contain Python ML models |
| FastAPI | ML inference, SHAP | Contain business routing logic |
| Python scripts | Training, evaluation | Run on mobile device |
| PostgreSQL | All persistent/spatial data | Be bypassed by direct mobile access |

---

## 6. Safety Score Contract

- The ML output (raw risk score) and the user-facing safety score are separate concepts.
- If a 0–100 safety score is used, document exactly how it is derived from the raw output.
- Categories (`Very Unsafe`, `Unsafe`, `Moderate`, `Safe`, `Very Safe`) are **application-defined classifications**, not universal scientific standards.
- This must be stated clearly in the UI, documentation, and dissertation.

---

## 7. Community Reports Contract

- A single unverified community report must NOT change a road segment's safety classification directly.
- Reports must pass through a `PENDING → VERIFIED | REJECTED` workflow.
- Only verified reports may influence safety scoring, and their influence must be proportional and bounded.

---

## 8. Demo Data Contract

- Mock data used during UI development must be labelled `DEMO DATA` or `MOCK DATA`.
- Demo data must be isolated from real data flows.
- The application must have a configurable `DEMO_MODE` flag.
- Demo mode must never be active in production builds.

---

## 9. Routing Contract

The A\* cost function is:
```
cost(edge) = α * normalized_time + β * normalized_distance + γ * normalized_risk
where α + β + γ = 1
```

- Weights must be configurable per route mode.
- Default weights must be documented as **IMPLEMENTATION CHOICES**.
- Route modes must behave as specified:
  - `FASTEST`: α high, γ low
  - `BALANCED`: α ≈ β ≈ γ (balanced)
  - `SAFEST`: γ high, α low
- These behaviors must be verified by unit tests.

---

## 10. Security Contract

- JWT secret must come from environment variables.
- Passwords must be hashed with BCrypt.
- No secrets in Git history.
- CORS must be explicitly configured.
- API inputs must be validated with Bean Validation.
- Stack traces must not be exposed in API error responses.
- Mobile tokens must be stored in Expo SecureStore.

---

## 11. Git Contract

- Never commit `.env` files.
- Never commit model binaries over 100MB without Git LFS.
- Never commit raw datasets without explicit permission and documentation.
- Use conventional commit messages (`feat:`, `fix:`, `docs:`, `test:`, `chore:`).

---

## Signature of Compliance

By contributing to this project, all developers (human or AI) agree to uphold this contract.

*SafePath AI Project Contract v1.0.0 — 2026-10-08*
