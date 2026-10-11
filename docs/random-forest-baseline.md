# SafePath AI — Random Forest Baseline Model

> **Document Type:** Machine Learning Baseline Specification & Evaluation Guide  
> **Status:** Implemented & Verified (Milestone 13)  
> **Authoritative Rules:** Governed strictly by `AGENTS.md` §2 (Data Integrity), §3 (Architecture), and `docs/ml-pipeline.md`

---

## 1. Overview & Objectives

Milestone 13 establishes a reproducible **Random Forest baseline model** for relative road-segment risk prediction using scikit-learn.

### Primary Goals:
1. Establish a solid tabular baseline against which the primary model (XGBoost, Milestone 14) can be evaluated.
2. Verify end-to-end integration with the FastAPI `ml-service` model loading and inference engine.
3. Validate anti-leakage preprocessing, chronological splitting, and explicit class imbalance handling.

---

## 2. Training Data & Target Variable Formulation

### 2.1 Genuine Data Availability Status
In strict adherence to `AGENTS.md` §2.1 & §2.4:
* **No genuine labeled training dataset** is currently committed in `datasets/raw/` or `datasets/processed/` (as documented in `datasets/README.md`).
* The pipeline has been implemented and tested against a clearly labeled synthetic training fixture ([scripts/fixtures/mock_training_dataset.csv](file:///c:/Users/91967/OneDrive/Desktop/Safe%20path%20AI/scripts/fixtures/mock_training_dataset.csv) labeled `# DEMO DATA - SYNTHETIC TRAINING FIXTURE - NOT REAL CRIME DATA`).
* **Scientific Honesty Statement:** Any performance metrics measured on synthetic fixtures demonstrate pipeline correctness, feature alignment, and software stability — **not real-world predictive validity**. Real-world metrics will only be documented after training on genuine empirical crime records.

### 2.2 Target Variable Formulation
In SafePath AI, the ML model predicts **relative road-segment risk**:
* **Real-World Target Definition:** Ground-truth risk must be derived from forward historical crime occurrences observed within the segment's spatial catchment area during a future observation window $[T, T + \Delta t]$.
  * **Binary Classification:** $\text{risk\_label} = \mathbb{I}(\text{forward\_severity\_weighted\_incidents} > \text{threshold})$
  * **Continuous Regression:** $\text{target\_risk} = \min(1.0, \text{forward\_risk} / K)$
* **Non-Circular Target:** The target variable is never constructed from an arbitrary safety score or a circular combination of the segment features themselves.

---

## 3. Training Pipeline Architecture

```
scripts/
├── model_training/
│   ├── __init__.py
│   ├── schemas.py              # TrainingConfig, EvaluationMetrics, FEATURE_NAMES
│   └── trainer.py              # RandomForestTrainer, anti-leakage scaler, serialization
├── train_random_forest.py      # CLI entry point
└── tests/
    └── test_random_forest_training.py # Verification unit tests
```

### 3.1 Anti-Leakage Safeguards
1. **Chronological Splitting:** Dataset rows are partitioned chronologically using [TemporalBoundaryManager](file:///c:/Users/91967/OneDrive/Desktop/Safe%20path%20AI/scripts/feature_pipeline/leakage_guard.py) (70% train, 15% validation, 15% test). This prevents future crime observations from leaking into training folds.
2. **Preprocessing Isolation:** `MinMaxScaler` is **fitted strictly on the training partition (`X_train`) only**. Validation and test matrices (`X_val`, `X_test`) are transformed using the training scaler parameters without refitting.

### 3.2 Handling Class Imbalance
In crime datasets, high-risk segments are typically a minority class. The classification model handles this explicitly via:
```python
RandomForestClassifier(
    class_weight="balanced",  # Weights inversely proportional to class frequencies
    n_estimators=100,
    max_depth=8,
    random_state=42
)
```

### 3.3 Naive Baseline Comparison
To ensure the Random Forest provides genuine predictive signal beyond naive heuristics:
* **Classification Baseline:** `DummyClassifier(strategy="most_frequent")` (majority class predictor).
* **Regression Baseline:** `DummyRegressor(strategy="mean")`.

---

## 4. Artifact Serialization & `ml-service` Compatibility

Saved artifacts in `models/random_forest/` match the directory layout required by `ml-service`:

| Artifact | Format | Description |
|---|---|---|
| `model.joblib` | Joblib binary | Serialized scikit-learn `RandomForestClassifier` |
| `scaler.joblib` | Joblib binary | Fitted `MinMaxScaler` from training partition |
| `feature_names.json` | JSON | Ordered array of the 15 feature names |
| `metadata.json` | JSON | Version, provenance, hyperparameters, evaluation metrics, and `is_synthetic_evaluation` flag |

---

## 5. Execution Commands

```bash
# 1. Dry run on synthetic training fixture
python scripts/train_random_forest.py --data scripts/fixtures/mock_training_dataset.csv --dry-run

# 2. Train and save production model artifacts
python scripts/train_random_forest.py \
    --data scripts/fixtures/mock_training_dataset.csv \
    --output-dir models/random_forest

# 3. Train regression baseline
python scripts/train_random_forest.py \
    --data scripts/fixtures/mock_training_dataset.csv \
    --task regressor \
    --output-dir models/random_forest

# 4. Run full test suite verifying training, prediction, and ml-service compatibility
pytest scripts/tests/test_random_forest_training.py -v
```

---

## 6. Assumptions & Limitations

| Limitation | Type | Note |
|---|---|---|
| Synthetic data metrics | **LIMITATION** | Metrics on mock fixtures verify software mechanics only; they do not represent real-world crime prediction accuracy. |
| Feature ordering | **FACT** | Training features must exactly match the 15 features in `FEATURE_NAMES`. |
| Random Forest complexity | **IMPLEMENTATION CHOICE** | `n_estimators=100` and `max_depth=8` are baseline choices; hyperparameter tuning will follow in comparative analysis. |
