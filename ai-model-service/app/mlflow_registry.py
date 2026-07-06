"""Thin MLflow Registry/Tracking helpers."""

from __future__ import annotations

from typing import Any

from .config import mlflow_tracking_uri


MODEL_REGISTRY_NAMES = {
    "FREQUENCY": "FrequencyModel",
    "SEVERITY": "SeverityModel",
}

LOWER_IS_BETTER = {
    "CV_PoissonDeviance",
    "PoissonDeviance",
    "CV_GammaDeviance",
    "GammaDeviance",
    "CV_MAE",
    "MAE",
}

HIGHER_IS_BETTER = {
    "CV_NormalizedGini",
    "NormalizedGini",
    "CV_Top_10_pct_Lift",
    "Top_10_pct_Lift",
    "CV_PearsonCorrelation",
    "PearsonCorrelation",
}


def mlflow_modules() -> tuple[Any, Any, Any]:
    import mlflow
    import mlflow.sklearn
    from mlflow.tracking import MlflowClient

    mlflow.set_tracking_uri(mlflow_tracking_uri())
    return mlflow, mlflow.sklearn, MlflowClient()


def registry_name(model_type: str) -> str:
    return MODEL_REGISTRY_NAMES[model_type.upper()]


class MlflowRegistry:
    def __init__(self) -> None:
        self.mlflow, self.mlflow_sklearn, self.client = mlflow_modules()

    def alias_version(self, model_type: str, alias: str) -> Any | None:
        try:
            return self.client.get_model_version_by_alias(registry_name(model_type), alias)
        except Exception:
            return None

    def get_version(self, model_type: str, version: str | int) -> Any:
        return self.client.get_model_version(registry_name(model_type), str(version))

    def run_metrics(self, run_id: str | None) -> dict[str, float]:
        if not run_id:
            return {}
        run = self.client.get_run(run_id)
        return {key: float(value) for key, value in run.data.metrics.items()}

    def run_params(self, run_id: str | None) -> dict[str, str]:
        if not run_id:
            return {}
        run = self.client.get_run(run_id)
        return dict(run.data.params)

    def model_payload(self, model_type: str, version: Any | None) -> dict[str, Any] | None:
        if version is None:
            return None
        params = self.run_params(version.run_id)
        return {
            "modelName": registry_name(model_type),
            "version": int(version.version),
            "runId": version.run_id,
            "algorithm": version.tags.get("candidate_model") or params.get("candidate_model") or params.get("selected_model"),
            "tags": dict(version.tags),
            "metrics": self.run_metrics(version.run_id),
        }

    def latest_candidate_from_job(self, model_type: str, version: str | None) -> dict[str, Any] | None:
        if not version:
            return None
        model_version = self.get_version(model_type, version)
        if model_version.tags.get("deployment_status") not in {"CANDIDATE", ""}:
            return None
        return self.model_payload(model_type, model_version)

    def load_model_by_version(self, model_type: str, version: str | int) -> Any:
        return self.mlflow_sklearn.load_model(f"models:/{registry_name(model_type)}/{version}")

    def load_model_by_alias(self, model_type: str, alias: str) -> Any:
        return self.mlflow_sklearn.load_model(f"models:/{registry_name(model_type)}@{alias}")

    def set_aliases(self, model_type: str, version: str | int) -> None:
        name = registry_name(model_type)
        self.client.set_registered_model_alias(name, "champion", str(version))
        self.client.set_registered_model_alias(name, "Production", str(version))

    def set_version_tag(self, model_type: str, version: str | int, key: str, value: str) -> None:
        self.client.set_model_version_tag(registry_name(model_type), str(version), key, value)


def metric_difference(production: dict[str, float], candidate: dict[str, float]) -> dict[str, dict[str, Any]]:
    differences: dict[str, dict[str, Any]] = {}
    for key in sorted(set(production) & set(candidate)):
        delta = float(candidate[key]) - float(production[key])
        if key in LOWER_IS_BETTER:
            improved = delta < 0
        elif key in HIGHER_IS_BETTER:
            improved = delta > 0
        else:
            improved = None
        differences[key] = {
            "production": float(production[key]),
            "candidate": float(candidate[key]),
            "difference": delta,
            "improved": improved,
        }
    return differences
