"""
SafePath AI ML Service — Pydantic schemas for API request/response.
"""
from __future__ import annotations

from typing import Any, Dict, List, Optional
from pydantic import BaseModel, ConfigDict, Field, field_validator




class RiskFeatures(BaseModel):
    """
    Feature vector for a single road segment risk prediction.
    All 15 features are optional — missing features are imputed with documented safe defaults
    during preprocessing. See app/services/preprocessor.py for imputation rules.
    Non-finite values (NaN, Inf) and unexpected feature names are strictly forbidden.
    """
    model_config = ConfigDict(extra="forbid", allow_inf_nan=False)

    # Spatial crime density features
    # NOTE: Only valid when sufficient EXACT_COORDINATE or ADDRESS_GEOCODED crime data exists.
    crime_density_500m: Optional[float] = Field(None, ge=0.0, description="KDE crime density within 500m")
    crime_density_1km: Optional[float] = Field(None, ge=0.0, description="KDE crime density within 1km")
    severity_weighted_density: Optional[float] = Field(None, ge=0.0, description="Severity-weighted KDE density")
    recent_crime_density: Optional[float] = Field(None, ge=0.0, description="KDE density for last 30 days")
    night_crime_density: Optional[float] = Field(None, ge=0.0, description="KDE density for night-time crimes (20:00-05:00)")

    # Temporal features
    hour_of_day: Optional[int] = Field(None, ge=0, le=23, description="Hour of day (0-23)")
    day_of_week: Optional[int] = Field(None, ge=0, le=6, description="Day of week (0=Monday, 6=Sunday)")

    # Lighting (proxy from OSM lit tag)
    # ASSUMPTION: 0.5 used as default when OSM tag is absent. See docs/limitations.md.
    lighting_score: Optional[float] = Field(None, ge=0.0, le=1.0, description="Lighting score proxy (0=dark, 1=well-lit)")

    # Activity proxy (estimated pedestrian density)
    # ASSUMPTION: Estimated from road type and time. See docs/limitations.md.
    activity_proxy: Optional[float] = Field(None, ge=0.0, le=1.0, description="Estimated pedestrian activity proxy (0=deserted, 1=busy)")

    # Emergency service proximity (meters)
    nearest_police_m: Optional[float] = Field(None, ge=0.0, description="Distance to nearest police station (meters)")
    nearest_hospital_m: Optional[float] = Field(None, ge=0.0, description="Distance to nearest hospital (meters)")
    nearest_transport_m: Optional[float] = Field(None, ge=0.0, description="Distance to nearest public transport (meters)")

    # Weather
    weather_precipitation: Optional[float] = Field(None, ge=0.0, description="Precipitation in mm")
    weather_visibility: Optional[float] = Field(None, ge=0.0, description="Visibility in km")

    # Verified community reports only
    verified_report_count: Optional[int] = Field(None, ge=0, description="Count of VERIFIED reports within 500m")


class PredictRiskRequest(BaseModel):
    """Single segment risk prediction request."""
    model_config = ConfigDict(extra="forbid")

    segment_id: str = Field(..., min_length=1, description="Road segment UUID or identifier")
    features: RiskFeatures


class PredictRiskResponse(BaseModel):
    """Risk prediction response for a single segment."""
    model_config = ConfigDict(protected_namespaces=())

    segment_id: str
    raw_model_output: float = Field(..., description="Raw output from underlying model before probability calibration")
    risk_score: float = Field(..., ge=0.0, le=1.0, description="Predicted relative risk in [0, 1] (0=low, 1=high)")
    safety_score: float = Field(..., ge=0.0, le=1.0, description="Derived safety score = round(1 - risk_score, 4)")
    risk_level: str = Field(..., description="Application-defined risk category (IMPLEMENTATION CHOICE)")
    model_type: str = Field(..., description="Active model architecture, e.g. xgboost or random_forest")
    model_version: str
    model_trained_at: Optional[str] = None
    feature_completeness: float = Field(..., ge=0.0, le=1.0, description="Fraction of features that were present (not imputed)")
    imputed_features: List[str] = Field(default_factory=list, description="List of feature names imputed with documented defaults")
    is_synthetic_demonstration: bool = Field(True, description="True if underlying model was trained on synthetic data")
    demo: bool = Field(False, description="True only if this is demo/mock data")
    notes: str = Field(
        default="Relative risk score within model training distribution. Uncalibrated score, not a validated real-world probability.",
        description="Operational interpretation disclosure"
    )


class BatchPredictRequest(BaseModel):
    """Batch prediction for multiple segments (used by A* routing)."""
    model_config = ConfigDict(extra="forbid")

    segments: List[PredictRiskRequest] = Field(
        ...,
        description="List of road segments with features to evaluate"
    )


class BatchPredictResponse(BaseModel):
    """Batch prediction response."""
    model_config = ConfigDict(protected_namespaces=())

    predictions: List[PredictRiskResponse]
    model_version: str
    model_type: str = "xgboost"
    total_segments: int = Field(..., description="Number of segments evaluated in this batch")
    is_synthetic_demonstration: bool = True
    demo: bool = False


class FactorContribution(BaseModel):
    """A single feature's SHAP-based contribution to the prediction."""
    name: str
    value: float = Field(..., description="Feature value consumed by the model")
    raw_value: Optional[float] = Field(None, description="Raw feature value before transformation/imputation")
    contribution: float = Field(..., description="SHAP value (positive = increases risk, negative = decreases risk)")
    direction: str = Field("neutral", description="increases_risk, decreases_risk, or neutral")
    description: str = Field(..., description="Non-causal associative phrasing")
    is_imputed: bool = Field(False, description="True if this feature value was imputed with default")


class ExplainRequest(BaseModel):
    """Request for SHAP-based explanation."""
    model_config = ConfigDict(extra="forbid")

    segment_id: str = Field(..., min_length=1, description="Road segment UUID or identifier")
    features: RiskFeatures


class ExplainResponse(BaseModel):
    """SHAP explanation response."""
    model_config = ConfigDict(protected_namespaces=())

    segment_id: str
    raw_model_output: Optional[float] = Field(None, description="Raw model prediction margin / probability")
    risk_score: float
    safety_score: float = Field(0.0, description="Mapped safety score: round(1.0 - risk_score, 4)")
    base_risk: float = Field(..., description="Model's average prediction (SHAP base value E[f(X)])")
    model_type: str = "xgboost"
    model_version: str
    factors: List[FactorContribution]
    reconstruction_valid: bool = Field(True, description="Additive reconstruction check status (|E[f(X)] + sum(phi) - f(x)| <= 1e-4)")
    reconstruction_error: Optional[float] = Field(None, description="Numerical deviation between base + contributions and model output")
    is_synthetic_demonstration: bool = Field(True, description="Flag for synthetic training provenance")
    disclaimer: str = (
        "These explanations reflect the model's statistical feature attributions, "
        "not direct measurements of safety. All risk categories are "
        "application-defined. See project documentation for limitations."
    )
    causality_warning: str = (
        "SHAP values describe statistical feature attributions in the model, "
        "not physical real-world causation. A feature attribution does not imply "
        "that modifying the feature directly changes real-world safety."
    )


class HealthResponse(BaseModel):
    """Health check response."""
    model_config = ConfigDict(protected_namespaces=())

    status: str
    models_loaded: bool
    active_model: Optional[str] = None
    model_version: Optional[str] = None
    model_trained_at: Optional[str] = None
    is_synthetic_demonstration: bool = True
    detail: str

