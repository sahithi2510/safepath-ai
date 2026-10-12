# SafePath AI — XGBoost Model Specification & Evaluation

> **Document Type:** Machine Learning Primary Model Specification, Baseline Comparison & Evaluation Guide  
> **Status:** Implemented & Verified (Milestone 14)  
> **Authoritative Rules:** Governed strictly by `AGENTS.md` §2 (Data Integrity), §3 (Architecture), and `docs/ml-pipeline.md`

---

## 1. Overview & Objectives

Milestone 14 implements the primary tabular model for SafePath AI: an **XGBoost (Extreme Gradient Boosting)** model using `xgboost==2.1.1`.

### Primary Goals:
1. Implement a reproducible, production-grade gradient boosting model with configurable hyperparameters and deterministic random seeding (`random_state=42`).
2. Implement explicit class imbalance handling using `scale_pos_weight = n_neg / n_pos`.
3. Provide a strict side-by-side, fair comparison against both the **Random Forest baseline** (Milestone 13) and a **Naive baseline** (`DummyClassifier` / `DummyRegressor`) on identical chronological train/val/test splits (70/15/15).
4. Persist independent production artifacts to `models/xgboost/` without mutating or overwriting the Random Forest baseline artifacts in `models/random_forest/`.
5. Ensure end-to-end compatibility with FastAPI `ml-service` (`ModelService`), which attempts to load XGBoost first and falls back to Random Forest.

---

## 2. Data Integrity & Target Variable Audit

### 2.1 Genuine Data Availability Status
In strict adherence to `AGENTS.md` §2.1 & §2.4:
* **No genuine labeled training dataset** is currently committed in `datasets/raw/` or `datasets/processed/` (as documented in `datasets/README.md`).
* The current pipeline has been verified using a synthetic training fixture ([scripts/fixtures/mock_training_dataset.csv](file:///c:/Users/91967/OneDrive/Desktop/Safe%20path%20AI/scripts/fixtures/mock_training_dataset.csv) labeled `# DEMO DATA - SYNTHETIC TRAINING FIXTURE - NOT REAL CRIME DATA`).
* **Scientific Honesty & Non-Circularity Audit:**
  - In `scripts/fixtures/generate_mock_training_data.py`, synthetic risk scores were generated using a fixed formula combining crime densities, lighting, night indicators, and police distance.
  - Because synthetic labels were generated from a mathematical combination of features, machine learning models trained on this fixture reflect the mathematical rule embedded in the synthetic generator.
  - **Crucial Limitation:** High accuracy or low MAE on synthetic fixtures demonstrates **pipeline correctness, feature schema consistency, serialization reliability, and software robustness** — **NOT empirical real-world safety prediction accuracy**.
  - Models trained on this fixture must never be claimed to have validated real-world predictive validity.

### 2.2 Real-World Target Construction Formulation
For valid real-world evaluation, the ground-truth target must be collected independently:
* **Observation Window:** Given feature snapshot time $T$ (e.g., historical aggregations up to 18:00 on day $D$), the ground-truth target must be observed during a future forward interval $[T, T + \Delta t]$ (e.g., $18:00 \text{ to } 22:00$).
* **Binary Classification Target:**
  $$\text{risk\_label} = \begin{cases} 1 & \text{if forward severity-weighted crime incident count} > \tau \\ 0 & \text{otherwise} \end{cases}$$
* **Continuous Regression Target:**
  $$\text{target\_risk} = \min\left(1.0, \frac{\text{forward\_severity\_weighted\_crimes}}{K}\right)$$
* This separates feature extraction ($t < T$) from outcome verification ($t \ge T$), preventing circularity.

---

## 3. Training & Evaluation Pipeline Architecture

```
scripts/
├── model_training/
│   ├── __init__.py              # Exports XGBoostConfig, XGBoostTrainer, ModelComparison
│   ├── schemas.py              # XGBoostConfig, ModelComparison, TrainingConfig, FEATURE_NAMES
│   ├── trainer.py              # RandomForestTrainer
│   └── xgboost_trainer.py      # XGBoostTrainer with imbalance & fair baseline comparison
├── train_xgboost.py            # CLI entry point with --compare and --dry-run
└── tests/
    └── test_xgboost_training.py # Verification unit tests (7 test cases)
```

### 3.1 Anti-Leakage Safeguards
1. **Strict Chronological Splitting:** Dataset records are sorted chronologically by `snapshot_time` and partitioned via [TemporalBoundaryManager](file:///c:/Users/91967/OneDrive/Desktop/Safe%20path%20AI/scripts/feature_pipeline/leakage_guard.py):
   - **Training Set (70%):** 84 records
   - **Validation Set (15%):** 18 records
   - **Test Set (15%):** 18 records
2. **Preprocessing Isolation:** `MinMaxScaler` is **fitted exclusively on the training partition (`X_train`)**. The validation and test sets are transformed using the fitted training bounds without refitting.

### 3.2 Handling Class Imbalance
In real crime spatial distributions, high-risk road segments constitute a minority of total road networks. XGBoost handles this natively via `scale_pos_weight`:
$$\text{scale\_pos\_weight} = \frac{N_{\text{negative}}}{N_{\text{positive}}}$$
In `XGBoostTrainer`, this ratio is computed dynamically from the training partition or can be overridden via `XGBoostConfig(scale_pos_weight=...)`.

---

## 4. Fair Baseline Comparison

To ensure objective and scientifically rigorous evaluation, `XGBoostTrainer.compare_with_baselines()` evaluates **XGBoost**, **Random Forest**, and a **Naive Baseline** on the exact same data partitions and target definitions.

### 4.1 Classification Comparison Results (Synthetic Fixture Check)

> **Note:** Software pipeline verification only; not empirical real-world accuracy.

| Metric | XGBoost (`XGBClassifier`) | Random Forest (`RandomForestClassifier`) | Naive Baseline (`DummyClassifier`) |
| :--- | :--- | :--- | :--- |
| **Accuracy** | **0.8333** | 0.7778 | 0.3333 (most frequent) |
| **Precision** | **1.0000** | 1.0000 | N/A |
| **Recall** | **0.7500** | 0.6667 | N/A |
| **F1 Score** | **0.8571** | 0.8000 | N/A |
| **ROC-AUC** | 0.9583 | **0.9722** | 0.5000 |
| **Test Matrix** | `[[6, 0], [3, 9]]` | `[[6, 0], [4, 8]]` | N/A |

### 4.2 Regression Comparison Results (Continuous Target Risk)

| Metric | XGBoost (`XGBRegressor`) | Random Forest (`RandomForestRegressor`) | Naive Baseline (`DummyRegressor`) |
| :--- | :--- | :--- | :--- |
| **MAE** | **0.0774** | 0.0866 | 0.1553 (mean) |
| **RMSE** | **0.1002** | 0.1160 | N/A |
| **$R^2$ Score** | **0.6689** | 0.5557 | 0.0000 |

### 4.3 Feature Importance Comparison
Both tree-based models capture the primary crime density and environmental indicators:
* **XGBoost Top Features:** `crime_density_1km` (0.301), `night_crime_density` (0.120), `severity_weighted_density` (0.101), `lighting_score` (0.068), `crime_density_500m` (0.064).
* **Random Forest Top Features:** `crime_density_1km` (0.248), `severity_weighted_density` (0.222), `night_crime_density` (0.120), `hour_of_day` (0.073), `lighting_score` (0.063).

---

## 5. Artifact Serialization & ML Service Integration

Artifacts are persisted in `models/xgboost/`:
- `models/xgboost/model.joblib`: Serialized `XGBClassifier` or `XGBRegressor` binary.
- `models/xgboost/scaler.joblib`: Fitted `MinMaxScaler` on training set.
- `models/xgboost/feature_names.json`: JSON list of exact 15 feature names.
- `models/xgboost/metadata.json`: Complete audit metadata including training timestamp, data provenance, metrics, confusion matrix, baseline comparison, and synthetic evaluation warning.

### ML Service Hierarchy:
FastAPI `ml-service` (`ModelService.load()`) checks models in priority order:
1. `models/xgboost/` (Primary model)
2. `models/random_forest/` (Baseline fallback)

If `models/xgboost/` is present, it is loaded automatically. Prediction endpoints output standardized risk scores, mapped safety scores (`1.0 - risk_score`), and risk levels (`VERY_LOW`, `LOW`, `MODERATE`, `HIGH`, `VERY_HIGH`).

---

## 6. How to Reproduce & Verify

### Running the CLI
```powershell
# 1. Fair 3-way comparison dry run (in-memory)
ml-service\venv\Scripts\python.exe scripts/train_xgboost.py --compare --dry-run

# 2. Train and persist XGBoost artifacts
ml-service\venv\Scripts\python.exe scripts/train_xgboost.py --compare --output-dir models/xgboost

# 3. Train continuous regression model
ml-service\venv\Scripts\python.exe scripts/train_xgboost.py --task regressor --compare --dry-run
```

### Running Test Suite
```powershell
# Run XGBoost unit tests
ml-service\venv\Scripts\python.exe -m pytest scripts/tests/test_xgboost_training.py -v

# Run entire repository test suite (38 tests)
ml-service\venv\Scripts\python.exe -m pytest -v
```

---

## 7. Remaining Limitations & Real-World Prerequisites

1. **Lack of Empirical Training Labels:** A production deployment requires ingest of genuine crime records (e.g., from Open Crime Data portals or city police registries) spatially joined to road segment geometries.
2. **Spatial Correlation / Autoregressive Effects:** Road segments connected directly to high-crime segments may share latent risk. Graph neural networks or spatial lag features could improve on tabular gradient boosting once road network topology is ingested.
3. **Hyperparameter Optimization:** Currently default and conservative tree parameters are used (`n_estimators=100`, `max_depth=6`, `learning_rate=0.1`). Bayesian optimization / Optuna tuning should be performed once genuine datasets are available.
