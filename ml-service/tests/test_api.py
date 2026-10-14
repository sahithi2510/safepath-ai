"""SafePath AI ML Service — Comprehensive API Test Suite.

Verifies:
- Health check endpoints (/health) in ready and unready states.
- Risk prediction (/predict-risk and /predict/single).
- Batch prediction (/predict-risk/batch and /predict/batch) with limits.
- SHAP explanations (/explain and /explain/) with additive verification and disclaimers.
- Input validation: types, bounds, missing features, imputed fields, extra fields.
- Non-finite numbers (NaN, Inf) strict rejection.
- Structured error handling: no stack traces, no internal paths leaked.
- Model unready (503) status handling.
"""
import math
import pytest
from fastapi.testclient import TestClient
from app.main import app
from app.services.model_service import ModelService


@pytest.fixture
def client():
    """Test client running within the FastAPI application lifespan."""
    with TestClient(app) as test_client:
        yield test_client


# ==============================================================================
# 1. Health Endpoint Tests
# ==============================================================================

def test_health_check_loaded(client):
    """Verify /health returns UP with loaded models, version, and synthetic flag."""
    response = client.get("/health")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "UP"
    assert data["models_loaded"] is True
    assert data["active_model"] in ["xgboost", "random_forest"]
    assert data["model_version"] is not None
    assert data["is_synthetic_demonstration"] is True
    assert "normally" in data["detail"]


def test_health_check_unready_degraded():
    """Verify /health returns DEGRADED and predictions return 503 when models are not loaded."""
    with TestClient(app) as test_client:
        # Create an unready dummy model service with an empty directory
        empty_service = ModelService(model_dir="./nonexistent_model_dir")
        # Do not load any models
        test_client.app.state.model_service = empty_service

        response = test_client.get("/health")
        assert response.status_code == 200
        data = response.json()
        assert data["status"] == "DEGRADED"
        assert data["models_loaded"] is False
        assert data["active_model"] is None
        assert "no trained models" in data["detail"]

        # Verify 503 Service Unavailable on predict endpoints
        pred_resp = test_client.post(
            "/predict-risk",
            json={"segment_id": "seg-1", "features": {}}
        )
        assert pred_resp.status_code == 503
        assert "not yet trained or loaded" in pred_resp.json()["detail"]

        # Verify 503 on explain endpoint
        exp_resp = test_client.post(
            "/explain",
            json={"segment_id": "seg-1", "features": {}}
        )
        assert exp_resp.status_code == 503


# ==============================================================================
# 2. Single Prediction Endpoint Tests (/predict-risk & /predict/single)
# ==============================================================================

def test_predict_risk_single_valid(client):
    """Verify single segment risk prediction with complete features."""
    payload = {
        "segment_id": "seg-valid-001",
        "features": {
            "crime_density_500m": 0.5,
            "crime_density_1km": 0.8,
            "severity_weighted_density": 0.4,
            "recent_crime_density": 0.2,
            "night_crime_density": 0.3,
            "hour_of_day": 22,
            "day_of_week": 5,
            "lighting_score": 0.3,
            "activity_proxy": 0.4,
            "nearest_police_m": 1200.0,
            "nearest_hospital_m": 800.0,
            "nearest_transport_m": 250.0,
            "weather_precipitation": 0.0,
            "weather_visibility": 10.0,
            "verified_report_count": 1,
        }
    }
    for endpoint in ["/predict-risk", "/predict/single"]:
        response = client.post(endpoint, json=payload)
        assert response.status_code == 200
        data = response.json()

        assert data["segment_id"] == "seg-valid-001"
        assert 0.0 <= data["risk_score"] <= 1.0
        assert 0.0 <= data["safety_score"] <= 1.0
        # Mathematical relationship: safety_score = 1.0 - risk_score
        assert abs((data["safety_score"] + data["risk_score"]) - 1.0) < 1e-3
        assert data["risk_level"] in ["VERY_LOW", "LOW", "MODERATE", "HIGH", "VERY_HIGH"]
        assert data["model_type"] in ["xgboost", "random_forest"]
        assert data["model_version"] is not None
        assert data["feature_completeness"] == 1.0
        assert data["imputed_features"] == []
        assert data["is_synthetic_demonstration"] is True
        assert data["demo"] is False
        assert "Uncalibrated score" in data["notes"]


def test_predict_risk_missing_features_imputation(client):
    """Verify partial features are imputed with documented defaults and tracked."""
    payload = {
        "segment_id": "seg-partial-002",
        "features": {
            "crime_density_500m": 0.75,
            "hour_of_day": 14,
            "lighting_score": 0.6,
        }
    }
    response = client.post("/predict-risk", json=payload)
    assert response.status_code == 200
    data = response.json()

    assert data["segment_id"] == "seg-partial-002"
    # 3 features provided out of 15 -> 0.2 completeness
    assert abs(data["feature_completeness"] - 0.2) < 0.01
    assert len(data["imputed_features"]) == 12
    assert "crime_density_500m" not in data["imputed_features"]
    assert "hour_of_day" not in data["imputed_features"]
    assert "lighting_score" not in data["imputed_features"]
    assert "nearest_police_m" in data["imputed_features"]


def test_predict_risk_empty_features_all_imputed(client):
    """Verify empty features payload results in complete default imputation."""
    payload = {
        "segment_id": "seg-empty-003",
        "features": {}
    }
    response = client.post("/predict-risk", json=payload)
    assert response.status_code == 200
    data = response.json()

    assert data["feature_completeness"] == 0.0
    assert len(data["imputed_features"]) == 15


# ==============================================================================
# 3. Batch Prediction Tests (/predict-risk/batch & /predict/batch)
# ==============================================================================

def test_predict_risk_batch_valid(client):
    """Verify batch prediction with multiple segments."""
    payload = {
        "segments": [
            {
                "segment_id": f"seg-batch-{i}",
                "features": {
                    "crime_density_500m": 0.1 * i,
                    "hour_of_day": 10 + i,
                    "lighting_score": 0.8 - (0.1 * i),
                }
            }
            for i in range(5)
        ]
    }
    for endpoint in ["/predict-risk/batch", "/predict/batch"]:
        response = client.post(endpoint, json=payload)
        assert response.status_code == 200
        data = response.json()

        assert data["total_segments"] == 5
        assert len(data["predictions"]) == 5
        assert data["predictions"][0]["segment_id"] == "seg-batch-0"
        assert data["predictions"][4]["segment_id"] == "seg-batch-4"
        assert data["is_synthetic_demonstration"] is True


def test_predict_risk_batch_empty_rejected(client):
    """Verify empty batch request returns 400 Bad Request."""
    response = client.post("/predict-risk/batch", json={"segments": []})
    assert response.status_code == 400
    data = response.json()
    assert "at least 1 segment" in data["detail"]


def test_predict_risk_batch_limit_exceeded(client):
    """Verify batches exceeding 500 segments are rejected with 400 Bad Request."""
    payload = {
        "segments": [
            {
                "segment_id": f"seg-{i}",
                "features": {"hour_of_day": 12}
            }
            for i in range(505)
        ]
    }
    response = client.post("/predict-risk/batch", json=payload)
    assert response.status_code == 400
    data = response.json()
    assert "exceeds maximum limit" in data["detail"]


# ==============================================================================
# 4. Input Validation & Strict Error Handling Tests
# ==============================================================================

def test_validation_non_finite_values_rejected(client):
    """Verify NaN and Infinity in feature inputs are rejected with 422."""
    # Test NaN rejection
    payload_nan = {
        "segment_id": "seg-nan",
        "features": {"crime_density_500m": float("nan")}
    }
    resp_nan = client.post("/predict-risk", json=payload_nan)
    assert resp_nan.status_code == 422
    data_nan = resp_nan.json()
    assert data_nan["error"] == "Validation Error"

    # Test +Inf rejection
    payload_inf = {
        "segment_id": "seg-inf",
        "features": {"crime_density_500m": float("inf")}
    }
    resp_inf = client.post("/predict-risk", json=payload_inf)
    assert resp_inf.status_code == 422

    # Test -Inf rejection
    payload_ninf = {
        "segment_id": "seg-ninf",
        "features": {"crime_density_500m": float("-inf")}
    }
    resp_ninf = client.post("/predict-risk", json=payload_ninf)
    assert resp_ninf.status_code == 422


def test_validation_out_of_range_values_rejected(client):
    """Verify values exceeding domain bounds are rejected with 422."""
    test_cases = [
        {"hour_of_day": 25},            # hour > 23
        {"hour_of_day": -1},            # hour < 0
        {"day_of_week": 7},             # day > 6
        {"lighting_score": 1.5},        # lighting > 1.0
        {"lighting_score": -0.1},       # lighting < 0.0
        {"activity_proxy": 1.2},        # activity > 1.0
        {"crime_density_500m": -0.5},   # density < 0.0
        {"nearest_police_m": -10.0},    # distance < 0.0
        {"verified_report_count": -2},  # count < 0
        {"weather_precipitation": -1.0}, # precipitation < 0.0
        {"weather_visibility": -5.0},   # visibility < 0.0
    ]
    for invalid_features in test_cases:
        response = client.post(
            "/predict-risk",
            json={"segment_id": "seg-oor", "features": invalid_features}
        )
        assert response.status_code == 422, f"Failed for {invalid_features}"
        data = response.json()
        assert data["error"] == "Validation Error"


def test_validation_unexpected_extra_features_rejected(client):
    """Verify that unknown/extra feature names are rejected by extra='forbid'."""
    payload = {
        "segment_id": "seg-extra",
        "features": {
            "crime_density_500m": 0.5,
            "unsupported_random_feature": 999.0
        }
    }
    response = client.post("/predict-risk", json=payload)
    assert response.status_code == 422
    data = response.json()
    assert data["error"] == "Validation Error"
    assert any("unsupported_random_feature" in str(err) for err in data["validation_errors"])


def test_validation_empty_segment_id_rejected(client):
    """Verify that empty segment_id string is rejected."""
    payload = {
        "segment_id": "",
        "features": {"hour_of_day": 12}
    }
    response = client.post("/predict-risk", json=payload)
    assert response.status_code == 422


def test_validation_malformed_types_rejected(client):
    """Verify that incompatible data types are rejected."""
    payload = {
        "segment_id": "seg-types",
        "features": {"hour_of_day": "midnight_rush"}
    }
    response = client.post("/predict-risk", json=payload)
    assert response.status_code == 422


# ==============================================================================
# 5. SHAP Explainability Endpoint Tests (/explain & /explain/)
# ==============================================================================

def test_explain_endpoint_valid(client):
    """Verify /explain endpoint produces additive-valid SHAP contributions with disclaimers."""
    payload = {
        "segment_id": "seg-explain-101",
        "features": {
            "crime_density_500m": 0.6,
            "crime_density_1km": 1.1,
            "lighting_score": 0.2,
            "activity_proxy": 0.3,
            "hour_of_day": 23,
        }
    }
    for endpoint in ["/explain", "/explain/"]:
        response = client.post(endpoint, json=payload)
        assert response.status_code == 200
        data = response.json()

        assert data["segment_id"] == "seg-explain-101"
        assert "risk_score" in data
        assert "safety_score" in data
        assert "base_risk" in data
        assert data["reconstruction_valid"] is True
        assert data["is_synthetic_demonstration"] is True
        assert "disclaimer" in data
        assert "causality_warning" in data
        assert "statistical feature attributions" in data["disclaimer"]
        assert "not physical real-world causation" in data["causality_warning"]

        factors = data["factors"]
        assert len(factors) > 0
        for factor in factors:
            assert "name" in factor
            assert "value" in factor
            assert "contribution" in factor
            assert factor["direction"] in ["increases_risk", "decreases_risk", "neutral"]
            assert "associate" in factor["description"].lower() or "correlate" in factor["description"].lower()
            assert isinstance(factor["is_imputed"], bool)


# ==============================================================================
# 6. Security & Information Leakage Guards
# ==============================================================================

def test_errors_do_not_leak_stack_traces_or_paths(client):
    """Verify error responses never expose Python stack traces or local filesystem paths."""
    # Send an invalid payload that triggers 422
    resp_422 = client.post("/predict-risk", json={"segment_id": 123, "features": "invalid"})
    assert resp_422.status_code == 422
    content_str = resp_422.text
    assert "Traceback (most recent call last)" not in content_str
    assert "C:\\Users\\" not in content_str
    assert "ml-service\\app\\" not in content_str

    # Send an invalid batch that triggers 400
    resp_400 = client.post("/predict-risk/batch", json={"segments": []})
    assert resp_400.status_code == 400
    content_400 = resp_400.text
    assert "Traceback" not in content_400
    assert "C:\\Users\\" not in content_400


def test_weather_features_imputation_and_inference(client):
    """Verify weather features 13 & 14 integration, imputation tracking, and valid units."""
    # 1. Prediction with explicit weather observations (heavy rain, low visibility)
    payload_observed = {
        "segment_id": "seg-storm-01",
        "features": {
            "weather_precipitation": 18.5,  # 18.5 mm rain
            "weather_visibility": 1.2,       # 1.2 km visibility
            "hour_of_day": 20,
            "day_of_week": 4
        }
    }
    resp_obs = client.post("/predict-risk", json=payload_observed)
    assert resp_obs.status_code == 200
    data_obs = resp_obs.json()
    assert 0.0 <= data_obs["risk_score"] <= 1.0
    assert 0.0 <= data_obs["safety_score"] <= 1.0
    # weather features were observed, so they must not be in imputed_features
    assert "weather_precipitation" not in data_obs["imputed_features"]
    assert "weather_visibility" not in data_obs["imputed_features"]

    # 2. Prediction with omitted weather observations (unobserved)
    payload_unobserved = {
        "segment_id": "seg-clear-01",
        "features": {
            "hour_of_day": 14,
            "day_of_week": 2
        }
    }
    resp_unobs = client.post("/predict-risk", json=payload_unobserved)
    assert resp_unobs.status_code == 200
    data_unobs = resp_unobs.json()
    # Missing weather features must be explicitly recorded in imputed_features
    assert "weather_precipitation" in data_unobs["imputed_features"]
    assert "weather_visibility" in data_unobs["imputed_features"]
    assert data_unobs["feature_completeness"] < 1.0

