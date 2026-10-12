# SafePath AI — FastAPI ML Inference Service Specification

**Milestone 16 Technical Specification & API Documentation**  
*Document Version:* 1.0.0  
*Service Version:* 1.0.0  
*Framework:* FastAPI / Pydantic v2 / Uvicorn  

---

## 1. System Overview & Architecture Role

The **FastAPI ML Service** provides low-latency, real-time machine learning inference and explainability for SafePath AI. It runs as an independent internal service consumed primarily by the Spring Boot backend during route planning and safety queries.

### Architectural Boundaries (`AGENTS.md` §3 Compliance)
- **Inference Only:** The service only loads pre-trained model artifacts serialized by offline training scripts (`scripts/model_training/`). Model training is strictly offline.
- **Stateless Operation:** Requests are evaluated statelessly without database writes or session state.
- **Model In-Memory Caching:** Models and scalers are loaded once into application memory during application startup (`lifespan`), avoiding repeated disk I/O per request.
- **Safe Degradation:** If trained model artifacts are missing at startup, the server boots normally, exposes degraded health status via `GET /health`, and returns clean `503 Service Unavailable` responses for inference endpoints instead of crashing.

```
                      +-----------------------------+
                      |     Spring Boot Backend     |
                      |  (A* Engine / Route Search) |
                      +--------------+--------------+
                                     |
                         HTTP REST   | JSON Payloads
                                     v
+--------------------------------------------------------------------------+
|                        FastAPI ML Service (:8001)                        |
|                                                                          |
|   +-------------------+  +---------------------+  +------------------+   |
|   |    GET /health    |  |  POST /predict-risk |  |   POST /explain  |   |
|   +---------+---------+  +----------+----------+  +---------+--------+   |
|             |                       |                       |            |
|             v                       v                       v            |
|     +---------------+      +-----------------+     +-----------------+   |
|     | Readiness &   |      |  Preprocessor   |     | TreeExplainer   |   |
|     | Model Status  |      |  (15 Features)  |     | (SHAP Engine)   |   |
|     +---------------+      +--------+--------+     +--------+--------+   |
|                                     |                       |            |
|                                     v                       v            |
|                        +-------------------------+                       |
|                        |  XGBoost / Random Forest|                       |
|                        |  (In-Memory Artifacts)  |                       |
|                        +-------------------------+                       |
+--------------------------------------------------------------------------+
```

---

## 2. Environment Configuration & Startup

All service parameters can be configured through environment variables or a `.env` file located in the project root or `ml-service/` directory.

### Configuration Parameters

| Variable | Default | Description |
|:---|:---|:---|
| `ML_HOST` | `0.0.0.0` | Bind IP address for the HTTP server |
| `ML_PORT` | `8001` | TCP port for incoming HTTP connections |
| `ML_MODEL_DIR` | `./models` | Filesystem path to directory containing model artifacts |
| `ACTIVE_MODEL` | `xgboost` | Preferred model architecture (`xgboost` or `random_forest`) |
| `MAX_BATCH_SIZE`| `500` | Maximum number of road segments allowed in a single batch request |
| `ML_LOG_LEVEL` | `info` | Logging verbosity (`debug`, `info`, `warning`, `error`) |

### Starting the Service

Activate the Python virtual environment and run Uvicorn:

```bash
# Windows
cd ml-service
venv\Scripts\activate
uvicorn app.main:app --host 0.0.0.0 --port 8001 --reload

# Linux / macOS
cd ml-service
source venv/bin/activate
uvicorn app.main:app --host 0.0.0.0 --port 8001 --reload
```

---

## 3. Supported Features & Preprocessing Pipeline

The inference pipeline requires and enforces the exact 15 features established in `scripts/feature_pipeline/` and `docs/ml-pipeline.md`.

### Feature Taxonomy & Default Imputation (`FEATURE_DEFAULTS`)

| Index | Feature Name | Type | Valid Range | Documented Default | Imputation Rationale / Assumption |
|:---:|:---|:---:|:---:|:---:|:---|
| 0 | `crime_density_500m` | float | $\ge 0.0$ | `0.0` | Zero local density when no crimes recorded nearby |
| 1 | `crime_density_1km` | float | $\ge 0.0$ | `0.0` | Zero broader area density when no crimes recorded |
| 2 | `severity_weighted_density` | float | $\ge 0.0$ | `0.0` | Neutral baseline when no severe crimes recorded |
| 3 | `recent_crime_density` | float | $\ge 0.0$ | `0.0` | Zero 30-day density when no recent crimes recorded |
| 4 | `night_crime_density` | float | $\ge 0.0$ | `0.0` | Zero night-time density when no night crimes recorded |
| 5 | `hour_of_day` | int | $0 \dots 23$ | `12` | Midday (12:00) represents neutral daylight baseline |
| 6 | `day_of_week` | int | $0 \dots 6$ | `0` | Monday represents neutral weekday baseline |
| 7 | `lighting_score` | float | $0.0 \dots 1.0$ | `0.5` | Moderate lighting assumption when OSM `lit` tag absent |
| 8 | `activity_proxy` | float | $0.0 \dots 1.0$ | `0.5` | Moderate pedestrian activity proxy when unknown |
| 9 | `nearest_police_m` | float | $\ge 0.0$ | `2000.0` | Conservative 2 km distance default |
| 10 | `nearest_hospital_m` | float | $\ge 0.0$ | `2000.0` | Conservative 2 km distance default |
| 11 | `nearest_transport_m` | float | $\ge 0.0$ | `500.0` | 500 m average transit access default |
| 12 | `weather_precipitation` | float | $\ge 0.0$ | `0.0` | Clear conditions (no precipitation) default |
| 13 | `weather_visibility` | float | $\ge 0.0$ | `10.0` | Standard clear atmospheric visibility (10 km) |
| 14 | `verified_report_count` | int | $\ge 0$ | `0` | Zero unverified reports |

### Imputation Tracking & Feature Completeness
- All 15 features are optional in the request payload. Missing fields are automatically substituted with the documented defaults above.
- The service computes `feature_completeness = count(present) / 15.0`.
- The service explicitly returns `imputed_features: List[str]` in every prediction response so callers (and end users) know which feature values were imputed rather than observed.

---

## 4. API Endpoints Reference

### 4.1 Health Check — `GET /health`

Verifies operational health and model readiness. Does not claim complete health simply because the HTTP daemon is running.

#### Response Schema (`200 OK`)
```json
{
  "status": "UP",
  "models_loaded": true,
  "active_model": "xgboost",
  "model_version": "1.0.0",
  "model_trained_at": "2026-10-11T15:15:42.490277+00:00",
  "is_synthetic_demonstration": true,
  "detail": "Service is running normally with trained models loaded."
}
```

*When model artifacts are missing or corrupt:*
```json
{
  "status": "DEGRADED",
  "models_loaded": false,
  "active_model": null,
  "model_version": null,
  "model_trained_at": null,
  "is_synthetic_demonstration": false,
  "detail": "Service is running, but no trained models were found."
}
```

---

### 4.2 Single Segment Risk Prediction — `POST /predict-risk`
*(Alias for backward compatibility: `POST /predict/single`)*

Evaluates the relative risk and safety score for an individual road segment.

#### Request Example (`POST /predict-risk`)
```json
{
  "segment_id": "seg-delhi-connaught-001",
  "features": {
    "crime_density_500m": 0.45,
    "crime_density_1km": 0.85,
    "severity_weighted_density": 0.35,
    "recent_crime_density": 0.15,
    "night_crime_density": 0.25,
    "hour_of_day": 21,
    "day_of_week": 4,
    "lighting_score": 0.3,
    "activity_proxy": 0.4,
    "nearest_police_m": 850.0,
    "nearest_hospital_m": 1200.0,
    "nearest_transport_m": 150.0,
    "weather_precipitation": 0.0,
    "weather_visibility": 8.0,
    "verified_report_count": 0
  }
}
```

#### Response Example (`200 OK`)
```json
{
  "segment_id": "seg-delhi-connaught-001",
  "raw_model_output": 0.2841,
  "risk_score": 0.5706,
  "safety_score": 0.4294,
  "risk_level": "MODERATE",
  "model_type": "xgboost",
  "model_version": "1.0.0",
  "model_trained_at": "2026-10-11T15:15:42.490277+00:00",
  "feature_completeness": 1.0,
  "imputed_features": [],
  "is_synthetic_demonstration": true,
  "demo": false,
  "notes": "Relative risk score within model training distribution. Uncalibrated score, not a validated real-world probability."
}
```

---

### 4.3 Batch Segment Risk Prediction — `POST /predict-risk/batch`
*(Alias for backward compatibility: `POST /predict/batch`)*

Evaluates multiple road segments simultaneously for graph cost computation in the A* routing engine.

#### Limits:
- Minimum segments: `1`
- Maximum segments: `500` (configurable via `MAX_BATCH_SIZE`)

#### Request Example (`POST /predict-risk/batch`)
```json
{
  "segments": [
    {
      "segment_id": "seg-101",
      "features": {
        "crime_density_500m": 0.2,
        "hour_of_day": 14,
        "lighting_score": 0.8
      }
    },
    {
      "segment_id": "seg-102",
      "features": {
        "crime_density_500m": 1.1,
        "hour_of_day": 23,
        "lighting_score": 0.1
      }
    }
  ]
}
```

#### Response Example (`200 OK`)
```json
{
  "predictions": [
    {
      "segment_id": "seg-101",
      "raw_model_output": -1.4521,
      "risk_score": 0.1897,
      "safety_score": 0.8103,
      "risk_level": "VERY_LOW",
      "model_type": "xgboost",
      "model_version": "1.0.0",
      "model_trained_at": "2026-10-11T15:15:42.490277+00:00",
      "feature_completeness": 0.2,
      "imputed_features": [
        "crime_density_1km",
        "severity_weighted_density",
        "recent_crime_density",
        "night_crime_density",
        "day_of_week",
        "activity_proxy",
        "nearest_police_m",
        "nearest_hospital_m",
        "nearest_transport_m",
        "weather_precipitation",
        "weather_visibility",
        "verified_report_count"
      ],
      "is_synthetic_demonstration": true,
      "demo": false,
      "notes": "Relative risk score within model training distribution. Uncalibrated score, not a validated real-world probability."
    },
    {
      "segment_id": "seg-102",
      "raw_model_output": 1.9214,
      "risk_score": 0.8723,
      "safety_score": 0.1277,
      "risk_level": "VERY_HIGH",
      "model_type": "xgboost",
      "model_version": "1.0.0",
      "model_trained_at": "2026-10-11T15:15:42.490277+00:00",
      "feature_completeness": 0.2,
      "imputed_features": [
        "crime_density_1km",
        "severity_weighted_density",
        "recent_crime_density",
        "night_crime_density",
        "day_of_week",
        "activity_proxy",
        "nearest_police_m",
        "nearest_hospital_m",
        "nearest_transport_m",
        "weather_precipitation",
        "weather_visibility",
        "verified_report_count"
      ],
      "is_synthetic_demonstration": true,
      "demo": false,
      "notes": "Relative risk score within model training distribution. Uncalibrated score, not a validated real-world probability."
    }
  ],
  "model_version": "1.0.0",
  "model_type": "xgboost",
  "total_segments": 2,
  "is_synthetic_demonstration": true,
  "demo": false
}
```

---

### 4.4 SHAP Feature Attribution — `POST /explain`
*(Alias: `POST /explain/`)*

Computes exact tree-based Shapley feature contributions ($\phi_i$) for a segment prediction using SHAP `TreeExplainer`.

#### Request Example (`POST /explain`)
```json
{
  "segment_id": "seg-explain-001",
  "features": {
    "crime_density_500m": 0.65,
    "crime_density_1km": 1.20,
    "lighting_score": 0.2,
    "activity_proxy": 0.3,
    "hour_of_day": 23
  }
}
```

#### Response Example (`200 OK`)
```json
{
  "segment_id": "seg-explain-001",
  "raw_model_output": 0.8421,
  "risk_score": 0.6989,
  "safety_score": 0.3011,
  "base_risk": 0.3333,
  "model_type": "xgboost",
  "model_version": "1.0.0",
  "factors": [
    {
      "name": "crime_density_1km",
      "value": 1.2,
      "raw_value": 1.2,
      "contribution": 0.2451,
      "direction": "increases_risk",
      "description": "Broader area crime density associates with higher model predicted risk",
      "is_imputed": false
    },
    {
      "name": "lighting_score",
      "value": 0.2,
      "raw_value": 0.2,
      "contribution": 0.1582,
      "direction": "increases_risk",
      "description": "Street lighting level associates with higher model predicted risk",
      "is_imputed": false
    },
    {
      "name": "hour_of_day",
      "value": 23.0,
      "raw_value": 23.0,
      "contribution": 0.1054,
      "direction": "increases_risk",
      "description": "Time of day correlates with higher risk in training distribution",
      "is_imputed": false
    }
  ],
  "reconstruction_valid": true,
  "reconstruction_error": 0.000004,
  "is_synthetic_demonstration": true,
  "disclaimer": "These explanations reflect the model's statistical feature attributions, not direct measurements of safety. All risk categories are application-defined. See project documentation for limitations.",
  "causality_warning": "SHAP values describe statistical feature attributions in the model, not physical real-world causation. A feature attribution does not imply that modifying the feature directly changes real-world safety."
}
```

---

## 5. Prediction Semantics & Score Taxonomy

To ensure scientific honesty (`AGENTS.md` §2, §5), model predictions are strictly distinguished across three operational tiers:

```
[Raw Model Output]           [Predicted Relative Risk]       [Application Safety Score]
 Margin z or Tree Vote   -->   Risk Score ∈ [0.0, 1.0]   -->   Safety Score ∈ [0.0, 1.0]
 (Internal model space)        (Relative Risk Score)           (Derived: 1.0 - Risk Score)
```

### 1. Raw Model Output
- **XGBoost Classifier:** Uncalibrated log-odds decision margin $z \in (-\infty, +\infty)$ obtained via `predict(X, output_margin=True)`.
- **Random Forest Classifier:** Direct ensemble tree vote fraction for Class 1: $\frac{1}{T}\sum_{t=1}^T \mathbb{I}(h_t(x)=1)$.
- **Regressor:** Direct continuous risk prediction.

### 2. Predicted Relative Risk (`risk_score`)
- Mapped to unit interval $[0.0, 1.0]$ via standard logistic sigmoid $\sigma(z) = \frac{1}{1 + e^{-z}}$ for XGBoost or class probability for Random Forest.
- **Direction:** Class 1 represents high risk. As `risk_score` approaches 1.0, relative risk increases.
- **Uncalibrated Warning:** Unless formal calibration (Platt scaling or isotonic regression) has been validated against real ground-truth outcomes, this value is explicitly documented as an *uncalibrated relative risk score*, not a true Bayesian probability.

### 3. Application Safety Score (`safety_score`)
- Derived mathematically via linear complement:
  $$\text{safety\_score} = \text{round}(1.0 - \text{risk\_score}, 4)$$
- Higher safety score $\implies$ safer road segment. When risk is 0.0, safety is 1.0. When risk is 1.0, safety is 0.0.

### 4. Application Risk Levels (`risk_level`)
Application-defined categories documented as implementation choices:
- `VERY_LOW`: $\text{risk\_score} < 0.20$
- `LOW`: $0.20 \le \text{risk\_score} < 0.40$
- `MODERATE`: $0.40 \le \text{risk\_score} < 0.60$
- `HIGH`: $0.60 \le \text{risk\_score} < 0.80$
- `VERY_HIGH`: $\text{risk\_score} \ge 0.80$

---

## 6. Input Validation, Limits & Error Responses

The service strictly validates inputs and returns structured JSON errors with appropriate HTTP status codes.

### Rejection Rules:
1. **Non-Finite Values (`NaN`, `+Inf`, `-Inf`):** Rejected with `422 Unprocessable Entity` via Pydantic `allow_inf_nan=False`.
2. **Out-of-Range Bounds:** `hour_of_day < 0` or `> 23`, `day_of_week < 0` or `> 6`, `lighting_score < 0.0` or `> 1.0`, etc. rejected with `422 Unprocessable Entity`.
3. **Extra Fields:** Any unexpected feature names rejected with `422 Unprocessable Entity` via `extra="forbid"`.
4. **Empty Segments / Empty Batches:** `segments: []` rejected with `400 Bad Request`.
5. **Batch Size Limit:** Batches exceeding 500 segments rejected with `400 Bad Request`.
6. **Unready Models:** Requests received before models are loaded return `503 Service Unavailable`.
7. **Explainer Unavailable:** Explanations requested for unsupported model types return `501 Not Implemented`.

### Information Disclosure Guard
The service includes global exception handlers for `RequestValidationError`, `HTTPException`, and generic unhandled `Exception`:
- **No Stack Traces Leaked:** Traceback strings are never returned to clients.
- **No Server Paths Leaked:** Local directory paths (`C:\Users\...`) are never echoed in HTTP error bodies.
- **No Secrets Leaked:** Environment secrets and API tokens are never included in responses.

#### Structured Error Format Example (`422 Unprocessable Entity`)
```json
{
  "error": "Validation Error",
  "status_code": 422,
  "detail": "Input validation failed for road segment features.",
  "validation_errors": [
    {
      "field": "body -> features -> hour_of_day",
      "message": "Input should be less than or equal to 23",
      "type": "less_than_equal"
    }
  ]
}
```

#### Structured Error Format Example (`400 Bad Request`)
```json
{
  "error": "HTTP Error",
  "status_code": 400,
  "detail": "Batch size exceeds maximum limit of 500 segments. Received 520 segments."
}
```

---

## 7. Verification & Automated Test Coverage

The test suite in `ml-service/tests/test_api.py` includes 15 automated integration tests verifying:
- `/health` in both ready (`UP`) and unready (`DEGRADED`) states.
- Single prediction on `/predict-risk` and `/predict/single`.
- Mathematical coherence of `safety_score == 1.0 - risk_score`.
- Default imputation tracking and feature completeness scoring.
- Batch prediction on `/predict-risk/batch` and `/predict/batch`.
- Batch limit enforcement (empty batch and > 500 batch rejected with 400).
- Rejection of non-finite values (`NaN`, `Inf`, `-Inf`).
- Rejection of out-of-range bounds on domain fields.
- Rejection of unknown feature fields.
- SHAP TreeExplainer additive reconstruction verification and non-causal disclosures.
- Information security protection against stack trace and path leakage.
