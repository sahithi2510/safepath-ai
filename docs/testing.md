# SafePath AI — Testing Strategy

> **Last Updated:** 2026-10-08

---

## 1. Principles

- Write meaningful tests — not just coverage padding
- Critical behaviors must be **verified**, not asserted
- Tests must fail if the expected behavior is absent
- Every milestone should include relevant tests before proceeding

---

## 2. Backend Tests (Spring Boot / JUnit 5)

### 2.1 Authentication Tests

| Test | Scenario | Expected |
|------|---------|---------|
| register_success | Valid registration data | 201, user created |
| register_duplicate_email | Email already exists | 409 |
| register_invalid_email | Malformed email | 400, field error |
| register_weak_password | Short/simple password | 400, field error |
| login_success | Valid credentials | 200, JWT returned |
| login_wrong_password | Incorrect password | 401 |
| login_unknown_email | Email not registered | 401 |
| protected_endpoint_with_token | Valid JWT | 200 |
| protected_endpoint_without_token | No Authorization header | 401 |
| protected_endpoint_expired_token | Expired JWT | 401 |
| protected_endpoint_invalid_token | Malformed token | 401 |

### 2.2 Route Service Tests

| Test | Scenario | Expected |
|------|---------|---------|
| route_found | Valid origin/destination | 3 routes returned |
| route_invalid_coords | Coordinates out of range | 400 |
| route_no_path | Unreachable destination | 404 |
| route_ml_unavailable | ML service down | Routes returned with safety scores null, mlServiceAvailable=false |
| route_fastest_vs_safest | Same origin/destination | FASTEST.duration < SAFEST.duration; SAFEST.safetyScore > FASTEST.safetyScore |

### 2.3 Route Mode Verification (Critical)

> **These tests must verify the behavior, not just assert it.**

```java
@Test
void fastest_route_has_shorter_duration_than_safest() {
    // Given a route request
    // When requesting routes
    // Then fastestRoute.durationSeconds < safestRoute.durationSeconds
    // AND safestRoute.safetyScore >= fastestRoute.safetyScore
    // (in a test graph where these properties can be controlled)
}

@Test
void safest_route_avoids_high_risk_segment() {
    // Given a graph where the direct path has high risk
    // And an alternate path has lower risk but longer duration
    // When requesting SAFEST mode
    // Then the route avoids the high-risk segment
}

@Test
void balanced_route_is_between_fastest_and_safest() {
    // Given results from all three modes
    // BALANCED.duration should be between FASTEST.duration and SAFEST.duration
    // (within a reasonable tolerance)
}
```

### 2.4 A\* Algorithm Tests

| Test | Scenario | Expected |
|------|---------|---------|
| astar_finds_shortest_path | Small test graph | Known optimal path returned |
| astar_no_path | Disconnected graph | Null returned |
| astar_single_node | Start == goal | Empty path |
| astar_weight_sensitivity | Increase γ (risk weight) | Route avoids high-risk edges |
| astar_admissible_heuristic | Any test graph | A\* result ≤ actual optimal cost |
| dijkstra_matches_astar | Same graph, γ=0 | Identical results |

### 2.5 Community Report Tests

| Test | Scenario | Expected |
|------|---------|---------|
| submit_report_authenticated | Valid report | 201, PENDING status |
| submit_report_unauthenticated | No JWT | 401 |
| get_nearby_reports | Nearby reports exist | Only VERIFIED returned |
| verify_report_admin | Admin verifies | Status = VERIFIED |
| verify_report_user | Non-admin attempts | 403 |
| reject_report | Admin rejects | Status = REJECTED |

### 2.6 Validation Tests

| Test | Scenario | Expected |
|------|---------|---------|
| route_null_origin | Null origin | 400, field error |
| report_null_coords | Missing coordinates | 400 |
| contact_empty_name | Empty name | 400 |

---

## 3. Routing Algorithm Tests (Unit)

Location: `backend/src/test/java/com/safepath/backend/routing/`

Test graph should be small, deterministic, and controllable — not loaded from the database.

```
Test Graph:
A --[time=1, risk=0.1]--> B --[time=1, risk=0.1]--> C
A --[time=5, risk=0.9]--> D --[time=5, risk=0.9]--> C

Fastest from A to C: A→B→C (cost dominated by time)
Safest from A to C: A→B→C (both agree here — use different graph)

Refined Test Graph:
A --[time=1, risk=0.9]--> C  (fast but dangerous)
A --[time=10, risk=0.1]--> B --[time=1, risk=0.1]--> C  (slow but safe)

FASTEST should choose A→C
SAFEST should choose A→B→C
BALANCED should depend on weights (document expected result)
```

---

## 4. ML Service Tests (Python / pytest)

### 4.1 Preprocessing Tests

| Test | Scenario | Expected |
|------|---------|---------|
| preprocess_complete_features | All features present | Valid feature vector |
| preprocess_missing_lighting | lighting_score missing | Uses default 0.5 |
| preprocess_missing_weather | Weather features absent | Uses 0.0 precipitation |
| preprocess_invalid_range | Negative distance | Error or clamped |
| scaler_consistency | Train-time vs inference-time scaler | Identical outputs |

### 4.2 Inference Tests

| Test | Scenario | Expected |
|------|---------|---------|
| predict_returns_valid_score | Valid input | 0.0 ≤ risk_score ≤ 1.0 |
| batch_predict | 10 segments | 10 results, same order |
| predict_high_risk_features | All risk indicators high | risk_score > 0.7 |
| predict_low_risk_features | All safety indicators high | risk_score < 0.3 |
| model_not_loaded | Models directory empty | 503 response |
| explain_returns_contributions | Valid segment | List of factor contributions |

### 4.3 API Endpoint Tests

| Test | Scenario | Expected |
|------|---------|---------|
| GET /health | Service running | 200 {"status": "healthy"} |
| POST /predict-risk valid | Valid payload | 200 with risk_score |
| POST /predict-risk invalid | Missing required field | 422 |
| POST /explain | Valid segment | 200 with factors |

---

## 5. Database Tests

| Test | Scenario | Expected |
|------|---------|---------|
| migrations_run_clean | Fresh database | All V1–V12 migrations succeed |
| postgis_available | After migration | ST_Distance works |
| nearby_safe_zones_query | PostGIS query | Correct results within radius |
| crime_spatial_index | GIST index used | Query explain plan shows index |

---

## 6. Mobile Tests (Jest / React Native Testing Library)

| Screen / Component | Test |
|-------------------|-----|
| LoginScreen | Shows validation errors for empty fields |
| LoginScreen | Shows network error when API down |
| RegisterScreen | Shows matching password validation |
| MapScreen | Renders permission denied state |
| RouteComparisonScreen | Displays 3 routes correctly |
| RouteComparisonScreen | Handles API error state |

---

## 7. Integration Tests

End-to-end flows tested with a test database:

1. Register → Login → Search Route → View Details
2. Submit Report → Report appears in "My Reports" → Admin verifies → Appears in nearby reports
3. Add Emergency Contact → Retrieve contacts → Delete contact

---

## 8. CI Integration

GitHub Actions runs on every push to `main` and `develop`:

```yaml
jobs:
  backend-test:
    - Run: ./mvnw test
    - Report: JUnit XML
    
  ml-test:
    - Run: python -m pytest tests/
    - Report: pytest XML
```

---

*SafePath AI — testing.md — 2026-10-08*
