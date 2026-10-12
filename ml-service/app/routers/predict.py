from fastapi import APIRouter, Request, HTTPException

from app.config import settings
from app.models.schemas import (
    PredictRiskRequest, 
    PredictRiskResponse, 
    BatchPredictRequest, 
    BatchPredictResponse
)

router = APIRouter(tags=["Prediction"])


def _check_models(request: Request):
    model_service = getattr(request.app.state, "model_service", None)
    if not model_service or not model_service.is_ready():
        raise HTTPException(
            status_code=503, 
            detail="ML Models are not yet trained or loaded. Please train models first."
        )


@router.post("/predict-risk", response_model=PredictRiskResponse)
@router.post("/predict/single", response_model=PredictRiskResponse, include_in_schema=False)
async def predict_single(payload: PredictRiskRequest, request: Request):
    """
    Predict relative risk for a single road segment based on its features.
    Missing features are imputed with documented safe defaults.
    """
    _check_models(request)
    return request.app.state.model_service.predict(payload.segment_id, payload.features)


@router.post("/predict-risk/batch", response_model=BatchPredictResponse)
@router.post("/predict/batch", response_model=BatchPredictResponse, include_in_schema=False)
async def predict_batch(payload: BatchPredictRequest, request: Request):
    """
    Predict relative risk for multiple road segments in a single batch.
    Sensible batch-size limits enforced (1 to max_batch_size segments).
    Used by the Spring Boot backend during A* route optimization.
    """
    _check_models(request)
    
    n_segments = len(payload.segments)
    if n_segments == 0:
        raise HTTPException(
            status_code=400,
            detail="Batch request must contain at least 1 segment."
        )
    if n_segments > settings.max_batch_size:
        raise HTTPException(
            status_code=400,
            detail=f"Batch size exceeds maximum limit of {settings.max_batch_size} segments. Received {n_segments} segments."
        )
    
    model_service = request.app.state.model_service
    predictions = model_service.predict_batch(payload.segments)
    
    return BatchPredictResponse(
        predictions=predictions,
        model_version=model_service.metadata.get("version", "unknown"),
        model_type=model_service.model_type or "unknown",
        total_segments=len(predictions),
        is_synthetic_demonstration=model_service.metadata.get("is_synthetic_evaluation", True),
        demo=False,
    )
