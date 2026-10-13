# SafePath AI — Route Comparison Specification

## 1. Overview & Objectives

SafePath AI provides multi-criteria pedestrian route comparison between an origin and destination coordinate. Rather than presenting a single opaque path, the system simultaneously evaluates and contrasts three candidate routes:

1. **`FASTEST`**: Prioritizes transit duration independently of machine learning safety inferences ($\alpha=0.8, \beta=0.2, \gamma=0.0$).
2. **`BALANCED`**: Represents a calibrated trade-off between walking time, detour distance, and street-level risk ($\alpha=0.3, \beta=0.2, \gamma=0.5$).
3. **`SAFEST`**: Heavily penalizes predicted segment risk to steer pedestrians toward well-lit, active, and lower-risk corridors ($\alpha=0.1, \beta=0.1, \gamma=0.8$).

### Academic & Scientific Honesty Notice
- **Relative Risk vs. Crime Probability:** Safety scores and risk categories represent **uncalibrated relative scores** derived from street-level spatial/temporal feature vectors. They do **not** represent verified statistical probabilities of crime occurrence.
- **Unscored Routes:** When the FastAPI ML service is unreachable or unready, candidate paths are marked as **`UNSCORED`**. The system strictly refuses to fabricate synthetic safety scores or present unscored routes as "safest".
- **Duplicate-Path Transparency:** If competing routing modes select the identical sequence of road segments on a constrained network, the system detects this and marks duplicate alternatives truthfully rather than presenting identical physical paths as distinct choices.

---

## 2. API Contract & Endpoints

### 2.1 Route Search & Comparison
- **`POST /api/routes/search`**
- **`POST /api/routes/compare`** (Alias endpoint)
- **Authentication:** Bearer JWT required

#### Request Payload (`RouteDto.SearchRequest`)
```json
{
  "origin": {
    "latitude": 12.9756,
    "longitude": 77.6066
  },
  "destination": {
    "latitude": 12.9716,
    "longitude": 77.6074
  },
  "travelMode": "WALKING"
}
```
*Note: Also supports flat coordinate fields (`startLat`, `startLon`, `endLat`, `endLon`) for client convenience.*

#### Response Payload (`RouteDto.SearchResponse`)
```json
{
  "requestId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
  "fastestRoute": {
    "routeMode": "FASTEST",
    "distanceMeters": 450.0,
    "durationSeconds": 321.4,
    "safetyScore": 45.0,
    "riskLevel": "MODERATE",
    "safetyLabel": "Moderate",
    "litSegmentsPercentage": 50.0,
    "isDistinct": true,
    "duplicateOf": null,
    "isMlScored": true,
    "modelVersion": "xgb-v1.0",
    "importantFactors": [
      "Minimizes estimated walking duration",
      "50% of route is along verified lit segments"
    ],
    "geometry": {
      "type": "LineString",
      "coordinates": [[77.6066, 12.9756], [77.6074, 12.9716]]
    },
    "coordinates": [
      { "latitude": 12.9756, "longitude": 77.6066 },
      { "latitude": 12.9716, "longitude": 77.6074 }
    ],
    "disclaimer": "Relative risk estimate derived from street attributes; not a validated empirical probability of crime occurrence."
  },
  "balancedRoute": {
    "routeMode": "BALANCED",
    "distanceMeters": 580.0,
    "durationSeconds": 414.2,
    "safetyScore": 82.5,
    "riskLevel": "VERY_LOW",
    "safetyLabel": "Very Safe",
    "litSegmentsPercentage": 100.0,
    "isDistinct": true,
    "duplicateOf": null,
    "isMlScored": true
  },
  "safestRoute": {
    "routeMode": "SAFEST",
    "distanceMeters": 580.0,
    "durationSeconds": 414.2,
    "safetyScore": 82.5,
    "riskLevel": "VERY_LOW",
    "safetyLabel": "Very Safe",
    "litSegmentsPercentage": 100.0,
    "isDistinct": false,
    "duplicateOf": "BALANCED",
    "isMlScored": true
  },
  "tradeOffExplanation": "SAFEST route trades +1.5 min (+130 m) for a +37.5 pt improvement in safety score (100% vs 50% lit streets).",
  "hasDistinctAlternatives": true,
  "distinctRouteCount": 2,
  "computedAt": "2026-10-13T13:20:00Z",
  "mlServiceAvailable": true,
  "weatherContextAvailable": false
}
```

---

## 3. Backend Route Generation & Comparison Logic

```
   [Client Request: Origin & Destination]
                   │
                   ▼
     Fetch Walkable Road Network (5km radius)
                   │
                   ▼
     Single Batch ML Risk Inference (/predict-risk/batch)
                   │
         ┌─────────┴─────────┐
         ▼                   ▼
    [ML Available]     [ML Offline]
         │                   │
         ├───────────────────┼──> FASTEST: A* (γ=0.0, time-priority)
         ├───────────────────┼──> BALANCED: A* (fallback unlit=0.7, lit=0.4)
         │                   └──> SAFEST: Mark UNSCORED (deferred)
         ▼
     SAFEST: A* (γ=0.8, risk-priority)
                   │
                   ▼
     Duplicate-Path Detection (Sequential Segment Comparison)
                   │
                   ▼
     Empirical Trade-Off Synthesis (Δt, Δd, Δsafety, % lit)
                   │
                   ▼
    [SearchResponse Returned to Client]
```

### 3.1 Efficient Single-Batch Inference
Rather than executing redundant network queries per routing preference, `RouteService.searchRoutes()`:
1. Identifies all unique walkable segments within the bounding radius ($R = 5000\text{m}$).
2. Executes a **single batch inference call** to the FastAPI ML service (`POST /predict-risk/batch`) with automatic client-side chunking ($\le 500$ segments/chunk).
3. Caches the inferred risk scores in an in-memory hash map passed to the A* routing engine for all three preference evaluations.

### 3.2 Duplicate-Path Detection
Because real-world road networks may feature only a single walkable connection between origin and destination, identical routes can emerge across different weight profiles.
- Paths $P_1$ and $P_2$ are equivalent if and only if they traverse the exact same sequence of segment identifiers:
  $$\text{arePathsEqual}(P_1, P_2) \iff |P_1| = |P_2| \land \forall i: \text{id}(P_1[i]) = \text{id}(P_2[i])$$
- **Primary Poles:** `FASTEST` and `SAFEST` anchor the speed-versus-safety spectrum.
- **Duplicate Tagging Rules:**
  - If `SAFEST` matches `FASTEST`, `SAFEST` is tagged `duplicateOf: "FASTEST", isDistinct: false`.
  - If `BALANCED` matches `FASTEST`, `BALANCED` is tagged `duplicateOf: "FASTEST", isDistinct: false`.
  - If `BALANCED` matches `SAFEST`, `BALANCED` is tagged `duplicateOf: "SAFEST", isDistinct: false`.
  - Otherwise, each path is marked `isDistinct: true, duplicateOf: null`.

### 3.3 Transparent Trade-Off Explanation
The backend dynamically synthesizes a concise, human-readable trade-off statement:
- **ML Offline:**
  > "ML risk service was unavailable. Alternative routes were generated using physical network topology and lighting heuristics without safety model scoring."
- **Single Feasible Route:**
  > "A single feasible path exists on the available road network between origin and destination. FASTEST, BALANCED, and SAFEST follow the identical route."
- **Divergent Paths:**
  > "SAFEST route trades +1.5 min (+130 m) for a +37.5 pt improvement in safety score (100% vs 50% lit streets)."

---

## 4. Mobile Screen Architecture

The mobile route comparison interface (`mobile/app/(tabs)/routes.tsx`) provides:

1. **Origin & Destination Selection:**
   - Coordinate inputs with quick presets (e.g. Bangalore MG Road / Brigade Road corridor).
2. **Side-by-Side 3-Way Mode Selector:**
   - Card tabs for `FASTEST`, `BALANCED`, and `SAFEST`.
   - Displays duration, distance, safety score pill, and duplicate tags.
3. **Multi-Polyline Map Display:**
   - Displays all candidate routes on `MapView`.
   - Unselected routes render with muted translucent lines (`#AEAEB2`, strokeWidth 3).
   - Currently active route highlights prominently (`#007AFF` for FASTEST, `#5856D6` for BALANCED, `#34C759` for SAFEST, strokeWidth 6).
   - Origin marker (Green) and Destination marker (Red).
4. **Context & Safety Details:**
   - Key factors list (`• 100% of route along verified lit segments`).
   - Detailed metrics (distance, estimated walk time, length-weighted safety score).
   - Prominent disclaimer clarifying relative risk semantics.
5. **Robust State Handling:**
   - Loading indicator with descriptive status.
   - Error banner with 1-tap retry button.
   - Offline ML banner when safety scoring is unavailable.

---

## 5. Verification & Test Coverage

### Backend Unit & Integration Tests
- **`RouteComparisonServiceTest`** (5 tests):
  - `testSearchRoutes_DistinctAlternatives_ProducedAndExplained`: Verifies distinct route identification, length-weighted safety scoring, and trade-off synthesis.
  - `testSearchRoutes_DuplicatePathDetection_IdenticalRoutesFlagged`: Verifies truthful tagging when candidate routes coincide on single-path networks.
  - `testSearchRoutes_MlServiceUnavailable_DegradesGracefullyWithUnscoredStatus`: Verifies non-ML fallback, preservation of FASTEST/BALANCED, and explicit UNSCORED state for SAFEST.
  - `testSearchRoutes_NoRoadNetwork_ThrowsNoRouteFoundException`: Verifies HTTP 404 domain exception when graph has no local road segments.
  - `testSearchRoutes_MissingCoordinates_ThrowsIllegalArgumentException`: Verifies input validation on missing coordinates.
- **`RouteControllerTest`** (3 tests):
  - `testSearchRoutes_ReturnsOk`: Verifies `POST /api/routes/search`.
  - `testCompareRoutes_ReturnsOk`: Verifies alias `POST /api/routes/compare`.
  - `testPlanRoute_MaintainsBackwardsCompatibility`: Verifies legacy `POST /api/routes`.

### Mobile Static Checks
- TypeScript compilation (`npx tsc --noEmit`): **0 errors**.
