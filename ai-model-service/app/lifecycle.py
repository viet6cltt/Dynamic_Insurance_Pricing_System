"""Training job and promotion lifecycle operations."""

from __future__ import annotations

from typing import Any

from sqlalchemy import desc, func, select, update

from .config import dataset_version, model_alias
from .db import engine, model_promotion_history, training_jobs, utcnow
from .mlflow_registry import MlflowRegistry, metric_difference, registry_name


def user_id(headers: dict[str, str] | None) -> str:
    if not headers:
        return "unknown"
    return headers.get("x-user-id") or headers.get("X-USER-ID") or "unknown"


def create_training_job(model_type: str, requested_by: str) -> dict[str, Any]:
    model_type = model_type.upper()
    now = utcnow()
    with engine().begin() as conn:
        row = conn.execute(
            training_jobs.insert()
            .values(
                model_type=model_type,
                status="QUEUED",
                current_stage="PREPARING_DATA",
                requested_by=requested_by,
                requested_at=now,
                dataset_version=dataset_version(),
            )
            .returning(training_jobs)
        ).mappings().one()
    return dict(row)


def get_training_job(job_id: int) -> dict[str, Any] | None:
    with engine().connect() as conn:
        row = conn.execute(select(training_jobs).where(training_jobs.c.id == job_id)).mappings().first()
    return dict(row) if row else None


def latest_completed_job(model_type: str) -> dict[str, Any] | None:
    with engine().connect() as conn:
        row = (
            conn.execute(
                select(training_jobs)
                .where(training_jobs.c.model_type == model_type.upper())
                .where(training_jobs.c.status == "COMPLETED")
                .where(training_jobs.c.registered_model_version.is_not(None))
                .order_by(desc(training_jobs.c.completed_at), desc(training_jobs.c.id))
                .limit(1)
            )
            .mappings()
            .first()
        )
    return dict(row) if row else None


def model_status(model_type: str) -> dict[str, Any]:
    model_type = model_type.upper()
    registry = MlflowRegistry()
    production_version = registry.alias_version(model_type, model_alias()) or registry.alias_version(model_type, "Production")
    job = latest_completed_job(model_type)
    return {
        "modelType": model_type,
        "registeredModelName": registry_name(model_type),
        "production": registry.model_payload(model_type, production_version),
        "candidate": registry.latest_candidate_from_job(model_type, job.get("registered_model_version") if job else None),
        "latestTrainingJob": job,
    }


def model_comparison(model_type: str) -> dict[str, Any]:
    status = model_status(model_type)
    production = status["production"]
    candidate = status["candidate"]
    differences = {}
    if production and candidate:
        differences = metric_difference(production.get("metrics", {}), candidate.get("metrics", {}))
    improved_count = sum(1 for item in differences.values() if item["improved"] is True)
    worsened_count = sum(1 for item in differences.values() if item["improved"] is False)
    return {
        "modelType": model_type.upper(),
        "production": production,
        "candidate": candidate,
        "differences": differences,
        "preferredVersion": candidate["version"] if candidate and improved_count > worsened_count else (production or {}).get("version"),
        "improved": bool(candidate and differences and improved_count > worsened_count),
    }


def update_training_job_result(job_id: int, result: dict[str, Any]) -> None:
    with engine().begin() as conn:
        conn.execute(
            update(training_jobs)
            .where(training_jobs.c.id == job_id)
            .values(
                status="COMPLETED",
                current_stage="FINISHED",
                mlflow_run_id=result.get("mlflowRunId"),
                registered_model_name=result.get("registeredModelName"),
                registered_model_version=str(result.get("registeredModelVersion") or ""),
                selected_algorithm=result.get("selectedAlgorithm"),
                metrics=result.get("metrics") or {},
                dataset_version=result.get("datasetVersion") or dataset_version(),
                completed_at=utcnow(),
            )
        )


def update_training_job_failed(job_id: int, reason: str) -> None:
    with engine().begin() as conn:
        conn.execute(
            update(training_jobs)
            .where(training_jobs.c.id == job_id)
            .values(status="FAILED", completed_at=utcnow(), failure_reason=reason[:4000])
        )


def record_action(model_type: str, from_version: str | None, to_version: str, action: str, reason: str | None, performed_by: str) -> None:
    with engine().begin() as conn:
        conn.execute(
            model_promotion_history.insert().values(
                model_type=model_type.upper(),
                from_version=from_version,
                to_version=str(to_version),
                action=action,
                reason=reason,
                performed_by=performed_by,
                performed_at=utcnow(),
            )
        )


def reject_candidate(model_type: str, candidate_version: str, reason: str | None, performed_by: str) -> dict[str, Any]:
    registry = MlflowRegistry()
    candidate = registry.get_version(model_type, candidate_version)
    registry.set_version_tag(model_type, candidate_version, "deployment_status", "REJECTED")
    record_action(model_type, None, str(candidate.version), "REJECT", reason, performed_by)
    return {"modelType": model_type.upper(), "version": int(candidate.version), "status": "REJECTED"}


def promote_candidate(
    model_type: str,
    candidate_version: str,
    reason: str | None,
    performed_by: str,
    reload_callback: Any | None = None,
    pre_promote_callback: Any | None = None,
) -> dict[str, Any]:
    model_type = model_type.upper()
    registry = MlflowRegistry()
    candidate = registry.get_version(model_type, candidate_version)
    if candidate.tags.get("deployment_status") not in {"CANDIDATE", ""}:
        raise ValueError(f"Model version {candidate_version} is not a CANDIDATE.")

    model = registry.load_model_by_version(model_type, candidate_version)
    input_example = None
    run = registry.client.get_run(candidate.run_id)
    if run.data.params.get("features"):
        features = run.data.params["features"].split(",")
        input_example = {feature: 1.0 for feature in features if feature != "gender"}
    if input_example:
        import pandas as pd

        model.predict(pd.DataFrame([input_example]))

    current = registry.alias_version(model_type, model_alias()) or registry.alias_version(model_type, "Production")
    from_version = str(current.version) if current else None
    if pre_promote_callback:
        pre_promote_callback(model_type, str(candidate_version))
    registry.set_aliases(model_type, candidate_version)
    registry.set_version_tag(model_type, candidate_version, "deployment_status", "PRODUCTION")
    if from_version and from_version != str(candidate_version):
        registry.set_version_tag(model_type, from_version, "deployment_status", "ARCHIVED")
    record_action(model_type, from_version, str(candidate_version), "PROMOTE", reason, performed_by)
    if reload_callback:
        reload_callback()
    return {
        "modelType": model_type,
        "fromVersion": int(from_version) if from_version else None,
        "toVersion": int(candidate_version),
        "status": "PROMOTED",
    }
