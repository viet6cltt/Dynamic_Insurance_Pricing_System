"""FastAPI entrypoint for AI Model Service."""

from __future__ import annotations

import time
from functools import lru_cache

from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import Response
from prometheus_client import CONTENT_TYPE_LATEST, Histogram, generate_latest
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

REQUEST_LATENCY = Histogram(
    "http_server_requests_seconds",
    "HTTP server request latency.",
    ("application", "method", "uri", "status", "outcome", "exception"),
    buckets=(0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1.0, 2.5, 5.0, 10.0),
)


def route_template(request: Request) -> str:
    route = request.scope.get("route")
    return getattr(route, "path", request.url.path)


def status_outcome(status_code: int) -> str:
    if 100 <= status_code < 200:
        return "INFORMATIONAL"
    if 200 <= status_code < 300:
        return "SUCCESS"
    if 300 <= status_code < 400:
        return "REDIRECTION"
    if 400 <= status_code < 500:
        return "CLIENT_ERROR"
    return "SERVER_ERROR"


@app.middleware("http")
async def prometheus_http_metrics(request: Request, call_next):
    if request.url.path == "/metrics":
        return await call_next(request)

    start_time = time.perf_counter()
    status_code = 500
    exception_name = "None"
    try:
        response = await call_next(request)
        status_code = response.status_code
        return response
    except Exception as exc:
        exception_name = exc.__class__.__name__
        raise
    finally:
        REQUEST_LATENCY.labels(
            application="ai-model-service",
            method=request.method,
            uri=route_template(request),
            status=str(status_code),
            outcome=status_outcome(status_code),
            exception=exception_name,
        ).observe(time.perf_counter() - start_time)


@app.get("/metrics", include_in_schema=False)
def metrics() -> Response:
    return Response(generate_latest(), media_type=CONTENT_TYPE_LATEST)


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
