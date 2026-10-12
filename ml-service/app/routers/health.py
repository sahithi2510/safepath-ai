from fastapi import APIRouter, Request

from app.models.schemas import HealthResponse

router = APIRouter(tags=["Health"])

@router.get("/health", response_model=HealthResponse)
async def health_check(request: Request):
    """
    Check ML service health and model readiness.
    """
    model_service = getattr(request.app.state, "model_service", None)
    models_ready = model_service.is_ready() if model_service else False
    
    status = "UP" if models_ready else "DEGRADED"
    detail = (
        "Service is running normally with trained models loaded."
        if models_ready
        else "Service is running, but no trained models were found."
    )
    
    return HealthResponse(
        status=status,
        models_loaded=models_ready,
        active_model=model_service.model_type if models_ready else None,
        model_version=model_service.metadata.get("version") if models_ready else None,
        model_trained_at=model_service.metadata.get("trained_at") if models_ready else None,
        is_synthetic_demonstration=model_service.metadata.get("is_synthetic_evaluation", True) if models_ready else False,
        detail=detail,
    )
