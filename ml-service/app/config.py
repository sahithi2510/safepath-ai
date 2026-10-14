"""
SafePath AI ML Service — Configuration
All settings read from environment variables with safe defaults.
"""
import os
from pydantic_settings import BaseSettings, SettingsConfigDict


def _resolve_default_model_dir() -> str:
    """Resolve models directory in container (/app/models), repo root, or ml-service."""
    if os.path.isdir("./models") and (os.path.isdir("./models/xgboost") or os.path.isdir("./models/random_forest")):
        return "./models"
    if os.path.isdir("../models") and (os.path.isdir("../models/xgboost") or os.path.isdir("../models/random_forest")):
        return "../models"
    return "./models"


class Settings(BaseSettings):
    ml_host: str = "0.0.0.0"
    ml_port: int = 8001
    ml_model_dir: str = _resolve_default_model_dir()
    ml_log_level: str = "info"

    # Which model to use for inference (xgboost or random_forest)
    active_model: str = "xgboost"

    # Maximum batch size for predict-risk/batch endpoint
    max_batch_size: int = 500

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore"
    )


settings = Settings()
