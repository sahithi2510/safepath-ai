import pytest
from fastapi.testclient import TestClient
from app.main import app

def test_health_check():
    with TestClient(app) as client:
        response = client.get("/health")
        assert response.status_code == 200
        data = response.json()
        
        # Since we haven't trained models yet in this fresh project, it should return DEGRADED
        assert data["status"] in ["UP", "DEGRADED"]
        assert "models_loaded" in data

def test_predict_single_no_models():
    """Verify that prediction endpoint functions or returns 503 if models missing."""
    features = {
        "crime_density_500m": 0.5,
        "crime_density_1km": 0.3,
        "hour_of_day": 14,
        "lighting_score": 0.8
    }
    
    with TestClient(app) as client:
        response = client.post(
            "/predict/single",
            json={"segment_id": "test-uuid", "features": features}
        )
        assert response.status_code in [200, 503]


def test_explain_endpoint():
    """Verify that /explain endpoint returns valid SHAP explanation when models are loaded."""
    features = {
        "crime_density_500m": 0.6,
        "crime_density_1km": 1.1,
        "lighting_score": 0.3,
    }

    with TestClient(app) as client:
        response = client.post(
            "/explain/",
            json={"segment_id": "test-explain-seg", "features": features}
        )
        assert response.status_code in [200, 501, 503]
        if response.status_code == 200:
            data = response.json()
            assert data["segment_id"] == "test-explain-seg"
            assert "risk_score" in data
            assert "safety_score" in data
            assert "base_risk" in data
            assert "factors" in data
            assert data["reconstruction_valid"] is True
            assert data["is_synthetic_demonstration"] is True
            assert "disclaimer" in data

