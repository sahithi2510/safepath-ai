# SafePath AI — Spatial & Temporal Feature Pipeline

> **Document Type:** Feature Engineering Specification & Methodology  
> **Status:** Implemented & Verified (Milestone 12)  
> **Authoritative Rules:** Governed strictly by `AGENTS.md` §2 (Data Integrity), §4 (Routing), and `docs/ml-pipeline.md`

---

## 1. Overview & Objectives

The SafePath AI Spatial and Temporal Feature Pipeline (`scripts/feature_pipeline/`) derives feature vectors for road segments to serve as input to baseline (Random Forest) and primary (XGBoost) risk models.

The pipeline adheres strictly to academic integrity and anti-leakage principles:
1. **Zero Fabrication:** Never converts coarse district- or locality-level data into fake coordinates or street labels.
2. **Precision Isolation:** Only records tagged `EXACT_COORDINATE` or `ADDRESS_GEOCODED` are admitted into point-based spatial density functions.
3. **Data Leakage Prevention:** Masks out future crime occurrences relative to the query timestamp; enforces strictly chronological train/validation/test dataset splits.
4. **Continuous Risk Representation:** KDE densities are mathematically defined continuous risk indicators, never misrepresented as discrete crime counts.

---

## 2. Feature Specification (`FEATURE_NAMES`)

The output feature set matches `ml-service/app/services/preprocessor.py` exactly:

| # | Feature Name | Source / Method | Range / Type | Notes |
|:---:|---|---|---|---|
| 1 | `crime_density_500m` | Gaussian KDE within 500m radius | $[0, \infty)$, float | Only exact/geocoded points |
| 2 | `crime_density_1km` | Gaussian KDE within 1000m radius | $[0, \infty)$, float | Only exact/geocoded points |
| 3 | `severity_weighted_density` | Severity-weighted KDE within 1km | $[0, \infty)$, float | Weights: 1 (minor) to 5 (critical) |
| 4 | `recent_crime_density` | KDE for last 30 days prior to $T$ | $[0, \infty)$, float | Anti-leakage temporal mask |
| 5 | `night_crime_density` | KDE for crimes during 20:00–05:00 | $[0, \infty)$, float | Temporal slice |
| 6 | `hour_of_day` | Evaluation departure time | $[0, 23]$, float | Fallback: 12.0 (midday) |
| 7 | `day_of_week` | Evaluation departure time | $[0, 6]$, float | 0=Monday, 6=Sunday; Fallback: 0.0 |
| 8 | `lighting_score` | OSM `lit` tag proxy | $[0.0, 1.0]$, float | 1.0=lit, 0.0=unlit, 0.5=missing default |
| 9 | `activity_proxy` | Road type + time-of-day factor | $[0.0, 1.0]$, float | Primary=0.85, Service=0.30 adjusted by time |
| 10 | `nearest_police_m` | Projected Euclidean distance | $[0, \infty)$, meters | Fallback: 2000.0m |
| 11 | `nearest_hospital_m` | Projected Euclidean distance | $[0, \infty)$, meters | Fallback: 2000.0m |
| 12 | `nearest_transport_m` | Projected Euclidean distance | $[0, \infty)$, meters | Fallback: 500.0m |
| 13 | `weather_precipitation` | Context (Open-Meteo) | $[0, \infty)$, mm | Fallback: 0.0mm |
| 14 | `weather_visibility` | Context (Open-Meteo) | $[0, \infty)$, km | Fallback: 10.0km |
| 15 | `verified_report_count` | Community reports (500m) | $[0, \infty)$, int | Only status = 'VERIFIED' |

---

## 3. Mathematical Formulation of Spatial Densities

### 3.1 Projected Coordinate Space
Calculations occur in **EPSG:3857 (Web Mercator)** projected metric space:
* Raw WGS84 GPS coordinates (`EPSG:4326`) are projected so that Euclidean distances correspond to meters on the ground.
* Segment locations are represented by the midpoint of the road segment `LineString` or point coordinates.

### 3.2 Gaussian Kernel Density Function
For an evaluation point $p = (x_p, y_p)$ and a set of verified crime points $\{c_1, \dots, c_N\}$ with Euclidean distances $d_i = \|p - c_i\|_2$:

$$K(d_i; \sigma) = \exp\left(-\frac{d_i^2}{2\sigma^2}\right)$$

Where bandwidth $\sigma = 200\text{ meters}$ (`kde_bandwidth_meters` in `FeaturePipelineConfig`).

### 3.3 Density Metrics
1. **Radius-Bounded Density:**
   $$\text{crime\_density\_500m}(p) = \sum_{i: d_i \le 500} K(d_i; \sigma)$$
   $$\text{crime\_density\_1km}(p) = \sum_{i: d_i \le 1000} K(d_i; \sigma)$$

2. **Severity-Weighted Density:**
   Each incident is weighted by its validated severity weight $w_i \in [1, 5]$:
   $$\text{severity\_weighted\_density}(p) = \sum_{i: d_i \le 1000} w_i \cdot K(d_i; \sigma)$$

---

## 4. Anti-Leakage Controls (`leakage_guard.py`)

To prevent data leakage in training and evaluation:
1. **Temporal Observation Masking:**
   When evaluating features at time $T$, any crime incident with $\text{incident\_datetime} \ge T$ is strictly filtered out. A road segment evaluated on March 15 cannot observe crimes that occurred on March 16.
2. **Chronological Splitting:**
   Datasets are partitioned by date:
   * **Train:** 70% oldest observations.
   * **Validation:** 15% middle observations.
   * **Test:** 15% most recent observations.
   Random K-fold splitting across time is prohibited.

---

## 5. Pipeline Structure

```
scripts/feature_pipeline/
├── __init__.py
├── schemas.py              # Configuration & feature definitions
├── temporal.py             # Temporal extractor & cyclical encodings (sin/cos)
├── spatial.py              # Metric KDE, safe-zone distance, lighting & activity
├── leakage_guard.py        # Temporal masking & chronological train/val/test split
└── pipeline.py             # FeaturePipeline orchestrator
```

### CLI Execution (`scripts/generate_features.py`)
```bash
# Validate feature pipeline in dry-run mode
python scripts/generate_features.py \
    --crimes-file scripts/fixtures/mock_crime_records.csv \
    --segments-file scripts/fixtures/mock_road_segments.csv \
    --safe-zones-file scripts/fixtures/mock_safe_zones.csv \
    --dry-run

# Generate and export feature table to CSV
python scripts/generate_features.py \
    --crimes-file datasets/processed/cleaned_crimes.csv \
    --segments-file datasets/raw/road_segments.csv \
    --output datasets/processed/segment_features.csv
```

---

## 6. Assumptions & Limitations

| Item | Classification | Documentation |
|---|---|---|
| Lighting score default = 0.5 | **ASSUMPTION** | When OSM `lit` tag is absent, a neutral midpoint assumption is adopted. |
| Activity proxy formula | **ASSUMPTION** | Pedestrian density is estimated from OSM road classification multiplied by diurnal curve. |
| Bandwidth $\sigma = 200m$ | **IMPLEMENTATION CHOICE** | Chosen based on typical urban block lengths (100–300m). |
| Coarse crime exclusion | **FACT** | Records with `DISTRICT`, `LOCALITY`, or `POLICE_STATION` precision are excluded from point KDE to preserve scientific honesty. |
| No real-time crime updates | **LIMITATION** | Historical data drives features; real-time updates rely on community reports and weather. |
