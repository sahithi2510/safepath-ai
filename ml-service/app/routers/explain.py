from fastapi import APIRouter, Request, HTTPException

from app.models.schemas import ExplainRequest, ExplainResponse

router = APIRouter(prefix="/explain", tags=["Explainability"])


@router.post("", response_model=ExplainResponse)
@router.post("/", response_model=ExplainResponse, include_in_schema=False)
async def explain_risk(payload: ExplainRequest, request: Request):
    """
    Provide SHAP-based feature attributions for a given segment's risk score.
    Helps understand why a road segment received a specific model prediction.
    Features describe statistical correlations in training distributions, not physical causation.
    """
    model_service = getattr(request.app.state, "model_service", None)
    
    if not model_service or not model_service.is_ready():
        raise HTTPException(
            status_code=503, 
            detail="ML Models are not yet trained or loaded."
        )
        
    if not model_service.is_explainer_ready():
        raise HTTPException(
            status_code=501, 
            detail="SHAP explainer is not available for the currently loaded model."
        )

    return model_service.explain(payload.segment_id, payload.features)
