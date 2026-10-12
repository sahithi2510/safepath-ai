"""
SafePath AI ML Service — Configuration
All settings read from environment variables with safe defaults.
"""
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    ml_host: str = "0.0.0.0"
    ml_port: int = 8001
    ml_model_dir: str = "./models"
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
