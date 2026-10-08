# SafePath AI — ML Pipeline

> **Last Updated:** 2026-10-08  
> **Status:** Design document (implementation in Milestones 11–17)  
> **IMPORTANT:** No metrics are reported in this document. Metrics will only be added after actual model training and evaluation.

---

## 1. ML Objective

**Predict relative road-segment risk** — a continuous or categorical score representing how dangerous a road segment is expected to be at a given time and context, relative to other segments.

This is framed as a **supervised learning problem**:
- Target: road-segment risk (derived from features; see Section 3 for honest target construction)
- Input: spatial, temporal, and contextual features per segment

---

## 2. Models

### 2.1 Random Forest (Baseline)
- scikit-learn `RandomForestClassifier` or `RandomForestRegressor`
- Purpose: Establishes a meaningful baseline; interpretable via feature importance
- Hyperparameters: documented after actual tuning

### 2.2 XGBoost (Primary Model)
- `xgboost.XGBClassifier` or `XGBRegressor`
- Purpose: Primary model; typically outperforms RF on tabular data
- Hyperparameters: documented after actual tuning

### 2.3 SHAP Explainer
- `shap.TreeExplainer` for both RF and XGBoost
- Purpose: Per-prediction feature contribution explanations
- Used in the `/explain` FastAPI endpoint

---

## 3. Target Construction

> **IMPORTANT:** The target label for ML training must be constructed honestly.

The approach depends on available data:

### If exact-coordinate crime records are available:
- Compute crime density near each road segment using KDE
- Weight by severity
- Construct a risk score from the density (normalized)
- Document the construction formula exactly

### If only district-level data is available:
- District-level crime rate can be used as a **regional proxy feature** only
- Do NOT claim the model predicts street-level risk if it is only trained on district aggregates
- Document this limitation explicitly

### If community reports (verified) are available:
- Verified reports near a segment can contribute to the target
- Weight by severity and recency

---

## 4. Feature Engineering

### 4.1 Spatial Crime Features
*(Valid only when `EXACT_COORDINATE` or `ADDRESS_GEOCODED` data exists)*

| Feature | Formula | Bandwidth | Notes |
|---------|---------|-----------|-------|
| `crime_density_500m` | KDE over crime points within 500m | Gaussian, σ=200m | IMPLEMENTATION CHOICE |
| `crime_density_1km` | KDE within 1km | Gaussian, σ=400m | IMPLEMENTATION CHOICE |
| `severity_weighted_density` | KDE weighted by severity score | Same | |
| `recent_crime_density` | KDE for last 30 days only | Same | Requires timestamps |
| `night_crime_density` | KDE for 20:00–05:00 crimes | Same | Requires timestamps |

> **IMPLEMENTATION CHOICE:** KDE bandwidth values are chosen heuristically. They should be tuned via cross-validation if time permits.

### 4.2 Temporal Features

| Feature | Source | Notes |
|---------|--------|-------|
| `hour_of_day` | Departure time | 0–23 |
| `day_of_week` | Departure time | 0=Monday, 6=Sunday |
| `is_weekend` | Derived | Binary |
| `is_night` | Derived (20:00–05:00) | Binary; IMPLEMENTATION CHOICE for boundaries |
| `is_rush_hour` | Derived (07:00–09:00, 17:00–20:00) | Binary; IMPLEMENTATION CHOICE |

### 4.3 Lighting Features

| Feature | Source | Notes |
|---------|--------|-------|
| `lighting_score` | OSM `lit` tag | 1.0=yes, 0.5=24/7, 0.0=no; `UNKNOWN`→0.5 (midpoint assumption, documented) |

> **ASSUMPTION:** When OSM `lit` tag is absent, we use 0.5 (moderate lighting) as a conservative midpoint. This is an assumption, not a fact.

### 4.4 Activity / Crowd Proxy

| Feature | Source | Notes |
|---------|--------|-------|
| `activity_proxy` | Road type (OSM highway tag) + time of day | Estimated proxy only |

> **ASSUMPTION:** Pedestrian activity is estimated from road type and time. This is a proxy without direct measurement.

### 4.5 Emergency Service Proximity

| Feature | Source | Notes |
|---------|--------|-------|
| `nearest_police_m` | PostGIS query on safe_zones | Meters; from legitimate OSM data |
| `nearest_hospital_m` | PostGIS query on safe_zones | |
| `nearest_transport_m` | PostGIS query on safe_zones | |

### 4.6 Weather Features (at prediction time)

| Feature | Source | Notes |
|---------|--------|-------|
| `precipitation_mm` | Open-Meteo | Optional; system works without it |
| `visibility_km` | Open-Meteo | Optional |
| `is_night_weather` | Derived from time | |

### 4.7 Community Report Features

| Feature | Source | Notes |
|---------|--------|-------|
| `verified_report_count_500m` | community_reports (VERIFIED only) | Only verified reports |
| `recent_verified_reports` | Last 30 days, VERIFIED | |

---

## 5. Preprocessing Pipeline

```
Raw features
    │
    ▼
Missing value handling
│  - Spatial features: fill with 0 if no data in range (document)
│  - Lighting: fill with 0.5 (documented assumption)
│  - Weather: fill with defaults if unavailable (no precipitation, moderate visibility)
│  - Emergency proximity: fill with city-max distance if no data
    │
    ▼
Normalization
│  - MinMaxScaler for spatial/distance features
│  - Leave binary/categorical as-is
    │
    ▼
Feature vector → Model input
```

The preprocessing pipeline **must be identical** at training time and inference time. The fitted scaler is saved alongside the model.

---

## 6. Training Methodology

> **Note:** This section describes the intended approach. Actual results will be documented after implementation.

### Train/Validation/Test Split
- **Temporal split** (preferred): Train on older data, validate/test on more recent data
  - Rationale: Prevents temporal leakage
  - Split: 70% train / 15% validation / 15% test
- If insufficient temporal data: Random split with documented rationale

### Cross-Validation
- Temporal cross-validation (TimeSeriesSplit) for hyperparameter tuning
- Spatial cross-validation if time permits (to test geographic generalization)

### Avoiding Leakage
- **Temporal leakage:** Never use future crime data as a feature for past road segments
- **Target leakage:** Never use raw crime counts in the same temporal window as the target

---

## 7. Evaluation

> **IMPORTANT:** No metrics are populated here. This section will be completed after actual training.

### If framed as Classification (risk levels: low/medium/high):
- Precision
- Recall
- F1 (per class and macro/weighted)
- ROC-AUC
- PR-AUC (especially important if classes are imbalanced)
- Confusion matrix

### If framed as Regression:
- MAE
- RMSE
- R²

### Additionally:
- Feature importance (RF: mean decrease impurity; XGBoost: gain)
- SHAP summary plots
- Comparison: XGBoost vs Random Forest vs naive baseline

---

## 8. Model Artifacts

After training, the following are saved to `models/`:

```
models/
├── random_forest/
│   ├── model.joblib        # Trained RF model
│   ├── scaler.joblib       # Fitted scaler
│   ├── feature_names.json  # Feature order
│   └── metadata.json       # Training date, metrics, data used
├── xgboost/
│   ├── model.joblib
│   ├── scaler.joblib
│   ├── feature_names.json
│   └── metadata.json
└── README.md               # What models exist, when trained
```

> **Note:** Model binaries are excluded from Git. See `models/README.md` for how to obtain/train them.

---

## 9. SHAP Explainability

SHAP (SHapley Additive exPlanations) provides:
- Global feature importance
- Per-prediction feature contributions

Used in the `/explain` endpoint to generate route safety explanations.

**What SHAP explains:** Why a specific segment received a specific risk score, based on which features pushed the score up or down.

**What SHAP does NOT explain:** Ground truth crime risk. It explains the model's reasoning, not absolute safety.

This limitation must be communicated to users in the explanation UI.

---

## 10. FastAPI ML Service Design

### Startup
```python
# Load models and scalers at startup
# If models don't exist, log warning and serve 503 for prediction endpoints
# Health endpoint always returns 200 even without models
```

### POST /predict-risk

Input:
```json
{
  "segment_id": "uuid",
  "features": {
    "crime_density_500m": 0.23,
    "severity_weighted_density": 0.15,
    "hour_of_day": 21,
    "day_of_week": 5,
    "lighting_score": 0.3,
    "nearest_police_m": 800.0,
    "precipitation_mm": 0.0,
    "verified_report_count_500m": 1
  }
}
```

Output:
```json
{
  "segment_id": "uuid",
  "risk_score": 0.72,
  "safety_score": 0.28,
  "risk_level": "HIGH",
  "model_version": "xgboost_v1",
  "model_trained_at": "2026-10-XX",
  "feature_completeness": 0.875,
  "demo": false
}
```

---

## 11. Limitations

| Limitation | Type |
|-----------|------|
| Training data quality depends on available crime dataset | FACT |
| KDE features are only valid when coordinate-precise crime data exists | FACT |
| Lighting score is a proxy from OSM, not measured | ASSUMPTION documented |
| Activity proxy is estimated, not measured | ASSUMPTION documented |
| No real-time crime updates | LIMITATION |
| Model may not generalize to areas not in training data | LIMITATION |
| Safety score thresholds are application-defined | IMPLEMENTATION CHOICE |

---

*SafePath AI — ml-pipeline.md — 2026-10-08*
