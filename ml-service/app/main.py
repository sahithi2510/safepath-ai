"""
SafePath AI — FastAPI ML Service
Main application entry point.
"""
from contextlib import asynccontextmanager
import logging

from fastapi import FastAPI, Request, HTTPException, status
from fastapi.exceptions import RequestValidationError
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from app.routers import health, predict, explain
from app.services.model_service import ModelService
from app.config import settings

logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Load models at startup. If models not found, service continues but
    prediction endpoints return 503 (model unavailable)."""
    model_service = ModelService(
        model_dir=settings.ml_model_dir,
        default_model=settings.active_model
    )
    model_service.load()
    app.state.model_service = model_service

    if model_service.is_ready():
        logger.info(
            "ML models loaded successfully (%s). Service ready.",
            model_service.model_type
        )
    else:
        logger.warning(
            "No trained models found in '%s'. "
            "Prediction endpoints will return 503 until models are trained. "
            "Health endpoint remains available.",
            settings.ml_model_dir
        )

    yield

    logger.info("ML service shutting down.")


app = FastAPI(
    title="SafePath AI — ML Service",
    description="""
    SafePath AI Machine Learning Inference Service.

    Provides:
    - Road-segment risk prediction (XGBoost / Random Forest)
    - Batch prediction for A* routing
    - SHAP-based safety explanation

    **Important notes:**
    - Risk scores are RELATIVE (0.0 = lowest risk, 1.0 = highest risk in context)
    - Safety categories are application-defined, not universal scientific standards
    - Models must be trained separately (see scripts/) before predictions are available
    - Metrics are only reported after actual training and evaluation
    """,
    version="1.0.0",
    lifespan=lifespan,
)

# Exception Handlers — strictly prevent leakage of tracebacks, local paths, or secrets
@app.exception_handler(RequestValidationError)
async def validation_exception_handler(request: Request, exc: RequestValidationError):
    cleaned_errors = []
    for err in exc.errors():
        field = " -> ".join(str(loc) for loc in err.get("loc", []))
        cleaned_errors.append({
            "field": field,
            "message": err.get("msg", "Invalid value"),
            "type": err.get("type", "validation_error"),
        })
    return JSONResponse(
        status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
        content={
            "error": "Validation Error",
            "status_code": 422,
            "detail": "Input validation failed for road segment features.",
            "validation_errors": cleaned_errors,
        },
    )


@app.exception_handler(HTTPException)
async def http_exception_handler(request: Request, exc: HTTPException):
    return JSONResponse(
        status_code=exc.status_code,
        content={
            "error": "HTTP Error",
            "status_code": exc.status_code,
            "detail": exc.detail,
        },
    )


@app.exception_handler(Exception)
async def generic_exception_handler(request: Request, exc: Exception):
    logger.error("Unhandled internal server error: %s", exc, exc_info=True)
    return JSONResponse(
        status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
        content={
            "error": "Internal Server Error",
            "status_code": 500,
            "detail": "An internal error occurred during request processing.",
        },
    )


# Internal service — CORS is configured explicitly
app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:8080"],
    allow_methods=["GET", "POST"],
    allow_headers=["*"],
)

app.include_router(health.router)
app.include_router(predict.router)
app.include_router(explain.router)
