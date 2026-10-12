"""
SafePath AI — FastAPI ML Service
Main application entry point.
"""
from contextlib import asynccontextmanager
import logging

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.routers import health, predict, explain
from app.services.model_service import ModelService
from app.config import settings

logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Load models at startup. If models not found, service continues but
    prediction endpoints return 503 (model unavailable)."""
    model_service = ModelService(settings.ml_model_dir)
    model_service.load()
    app.state.model_service = model_service

    if model_service.is_ready():
        logger.info("ML models loaded successfully. Service ready.")
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
    version="0.0.1",
    lifespan=lifespan,
)

# Internal service — CORS is permissive since this is not exposed externally
app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:8080"],
    allow_methods=["GET", "POST"],
    allow_headers=["*"],
)

app.include_router(health.router)
app.include_router(predict.router)
app.include_router(explain.router)
