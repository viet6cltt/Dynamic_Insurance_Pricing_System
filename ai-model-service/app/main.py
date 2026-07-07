"""FastAPI entrypoint for AI Model Service."""

from __future__ import annotations

from functools import lru_cache

from fastapi import FastAPI, HTTPException, Request
from sqlalchemy.exc import IntegrityError

from .db import init_db
from .lifecycle import (
    create_training_job,
    get_training_job,
    model_comparison,
    model_status,
    promote_candidate,
    reject_candidate,
    user_id,
)
from .pure_premium_runtime import PurePremiumRuntime
from .schemas import (
    HealthPricingPredictionRequest,
    HealthPricingPredictionResponse,
    ModelMetadataResponse,
    PromoteRejectRequest,
    PurePremiumPredictionRequest,
    PurePremiumPredictionResponse,
    TrainingJobCreateRequest,
)


app = FastAPI(title="AI Model Service", version="1.0.0")


@app.on_event("startup")
def startup() -> None:
    init_db()


@lru_cache
def pure_runtime() -> PurePremiumRuntime:
    return PurePremiumRuntime()


def reload_pure_runtime() -> None:
    pure_runtime.cache_clear()


def require_admin(http_request: Request) -> None:
    role = http_request.headers.get("x-user-role") or http_request.headers.get("X-USER-ROLE") or ""
    if role not in {"ROLE_ADMIN", "ADMIN", "SYSTEM"}:
        raise HTTPException(status_code=403, detail="Admin privileges are required.")


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.get("/health/pricing/metadata", response_model=ModelMetadataResponse)
def model_metadata() -> dict[str, object]:
    return {
        "serviceVersion": "hybrid-health-pricing-v1",
        "portfolioModel": {
            "status": "not_configured",
            "featureList": [],
            "purpose": "Portfolio Expected Cost Model (disabled)",
        },
        "healthRiskModel": {
            "status": "not_configured",
            "featureList": [],
            "purpose": "Health risk modifier (disabled)",
        },
        "noFinalPremiumInModel": True,
        "finalPremiumFormula": (
            "purePremium * (1 + loadingRate)"
        ),
    }


@app.post("/health/pricing/predict", response_model=HealthPricingPredictionResponse)
def predict_health_pricing(request: HealthPricingPredictionRequest) -> dict[str, object]:
    try:
        return pure_runtime().predict(request)
    except Exception as exc:
        raise HTTPException(status_code=503, detail=str(exc)) from exc


@app.post("/pure-premium/predict", response_model=PurePremiumPredictionResponse)
def predict_pure_premium(request: PurePremiumPredictionRequest) -> dict[str, object]:
    try:
        return pure_runtime().predict(request)
    except Exception as exc:
        raise HTTPException(status_code=503, detail=str(exc)) from exc


@app.post("/api/admin/training-jobs")
def create_training_job_endpoint(request: TrainingJobCreateRequest, http_request: Request) -> dict[str, object]:
    require_admin(http_request)
    try:
        return create_training_job(request.modelType, user_id(dict(http_request.headers)))
    except IntegrityError as exc:
        raise HTTPException(
            status_code=409,
            detail=f"An active training job already exists for {request.modelType}.",
        ) from exc


@app.get("/api/admin/training-jobs/{job_id}")
def get_training_job_endpoint(job_id: int, http_request: Request) -> dict[str, object]:
    require_admin(http_request)
    job = get_training_job(job_id)
    if not job:
        raise HTTPException(status_code=404, detail="Training job not found.")
    return job


@app.get("/api/admin/models/{model_type}")
def get_model_endpoint(model_type: str, http_request: Request) -> dict[str, object]:
    require_admin(http_request)
    try:
        return model_status(model_type)
    except KeyError as exc:
        raise HTTPException(status_code=404, detail="Unknown model type.") from exc


@app.get("/api/admin/models/{model_type}/comparison")
def get_model_comparison_endpoint(model_type: str, http_request: Request) -> dict[str, object]:
    require_admin(http_request)
    try:
        return model_comparison(model_type)
    except KeyError as exc:
        raise HTTPException(status_code=404, detail="Unknown model type.") from exc


@app.post("/api/admin/models/{model_type}/promote")
def promote_model_endpoint(model_type: str, request: PromoteRejectRequest, http_request: Request) -> dict[str, object]:
    require_admin(http_request)
    try:
        return promote_candidate(
            model_type,
            str(request.candidateVersion),
            request.reason,
            user_id(dict(http_request.headers)),
            reload_callback=reload_pure_runtime,
            pre_promote_callback=lambda mt, version: pure_runtime().recompute_baselines(mt, version),
        )
    except Exception as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


@app.post("/api/admin/models/{model_type}/reject")
def reject_model_endpoint(model_type: str, request: PromoteRejectRequest, http_request: Request) -> dict[str, object]:
    require_admin(http_request)
    try:
        return reject_candidate(
            model_type,
            str(request.candidateVersion),
            request.reason,
            user_id(dict(http_request.headers)),
        )
    except Exception as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
