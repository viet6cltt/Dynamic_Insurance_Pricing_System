"""FastAPI entrypoint for AI Model Service."""

from __future__ import annotations

from functools import lru_cache

from fastapi import FastAPI

from .model_runtime import HealthPricingRuntime
from .schemas import (
    HealthPricingPredictionRequest,
    HealthPricingPredictionResponse,
    ModelMetadataResponse,
)


app = FastAPI(title="AI Model Service", version="1.0.0")


@lru_cache
def runtime() -> HealthPricingRuntime:
    return HealthPricingRuntime()


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.get("/health/pricing/metadata", response_model=ModelMetadataResponse)
def model_metadata() -> dict[str, object]:
    return runtime().metadata_response()


@app.post("/health/pricing/predict", response_model=HealthPricingPredictionResponse)
def predict_health_pricing(request: HealthPricingPredictionRequest) -> dict[str, object]:
    return runtime().predict(request)
