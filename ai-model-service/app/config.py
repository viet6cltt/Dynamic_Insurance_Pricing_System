"""Configuration helpers for AI Model Service."""

from __future__ import annotations

import os
from pathlib import Path


def database_url() -> str:
    return os.getenv(
        "AI_MODEL_DATABASE_URL",
        "postgresql+psycopg://postgres:postgres@localhost:5440/ai_model_db",
    )


def mlflow_tracking_uri() -> str:
    return os.getenv("MLFLOW_TRACKING_URI", "http://127.0.0.1:15000")


def model_alias() -> str:
    return os.getenv("MODEL_CHAMPION_ALIAS", "champion")


def reference_dataset_path() -> Path:
    return Path(os.getenv("REFERENCE_DATASET_PATH", "data/generated/synthetic_insurance_claims.csv"))


def dataset_version() -> str:
    return os.getenv("DATASET_VERSION", "generated-current")


def risk_factor_min() -> float:
    return float(os.getenv("PURE_PREMIUM_RISK_FACTOR_MIN", "0.5"))


def risk_factor_max() -> float:
    return float(os.getenv("PURE_PREMIUM_RISK_FACTOR_MAX", "3.0"))
