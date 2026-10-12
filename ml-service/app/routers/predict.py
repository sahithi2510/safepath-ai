from fastapi import APIRouter, Request, HTTPException

from app.models.schemas import (
    PredictRiskRequest, 
    PredictRiskResponse, 
    BatchPredictRequest, 
    BatchPredictResponse
)

router = APIRouter(prefix="/predict", tags=["Prediction"])

def _check_models(request: Request):
    if not request.app.state.model_service.is_ready():
        raise HTTPException(
            status_code=503, 
            detail="ML Models are not yet trained or loaded. Please train models first."
        )

@router.post("/single", response_model=PredictRiskResponse)
async def predict_single(payload: PredictRiskRequest, request: Request):
    """
    Predict risk for a single road segment based on its features.
    Missing features will be imputed with safe defaults.
    """
    _check_models(request)
    return request.app.state.model_service.predict(payload.segment_id, payload.features)

@router.post("/batch", response_model=BatchPredictResponse)
async def predict_batch(payload: BatchPredictRequest, request: Request):
    """
    Predict risk for multiple road segments in a single batch.
    Used by the Spring Boot backend during A* route optimization.
    """
    _check_models(request)
    
    predictions = request.app.state.model_service.predict_batch(payload.segments)
    
    return BatchPredictResponse(
        predictions=predictions,
        model_version=request.app.state.model_service.metadata.get("version", "unknown")
    )
