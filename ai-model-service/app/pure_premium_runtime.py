"""Frequency-Severity pure premium runtime."""

from __future__ import annotations

import json
import os
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import numpy as np
import pandas as pd
from joblib import load
from sqlalchemy import select, update
from sqlalchemy.dialects.postgresql import insert

from .config import dataset_version, model_alias, reference_dataset_path
from .db import engine, pure_premium_baselines, utcnow
from .mlflow_registry import MlflowRegistry
from .schemas import HealthPricingPredictionRequest, PurePremiumPredictionRequest


FREQUENCY_DEFAULT_FEATURES = [
    "age",
    "seniority_insured",
    "seniority_policy",
    "bmi",
    "blood_pressure",
    "prev_claim_count",
    "prev_claim_cost",
    "claim_free_years",
    "years_with_history",
    "type_policy",
    "type_policy_dg",
    "type_product",
    "reimbursement",
    "new_business",
    "distribution_channel",
    "smoker",
    "pre_existing_condition",
    "exercise_frequency",
    "occupation_risk",
    "prev_had_claim",
    "claim_free_previous_year",
    "exposure_time",
]

SEVERITY_DEFAULT_FEATURES = [
    "age",
    "seniority_insured",
    "bmi",
    "blood_pressure",
    "prev_claim_cost",
    "prev_claim_count",
    "prev_average_claim_severity",
    "new_business",
    "type_policy",
    "type_policy_dg",
    "type_product",
    "reimbursement",
    "smoker",
    "pre_existing_condition",
    "exercise_frequency",
    "occupation_risk",
]

EXPLANATION_FEATURES = [
    "smoker",
    "bmi",
    "blood_pressure",
    "pre_existing_condition",
    "exercise_frequency",
    "occupation_risk",
    "prev_claim_count",
    "prev_claim_cost",
    "claim_free_years",
    "type_product",
    "type_policy",
    "reimbursement",
    "new_business",
    "distribution_channel",
]


@dataclass
class LoadedModel:
    pipeline: Any
    metadata: dict[str, Any]
    version: str | None


def _local_artifact_dir() -> Path:
    return Path(os.getenv("MODEL_ARTIFACT_DIR", Path(__file__).resolve().parents[2] / "ml" / "artifacts"))


def _read_json(path: Path) -> dict[str, Any]:
    if not path.exists():
        return {}
    return json.loads(path.read_text(encoding="utf-8"))


def _safe_float(value: Any, default: float = 0.0) -> float:
    if value is None or pd.isna(value):
        return default
    return float(value)


def _impact(value: float) -> str:
    if value > 0:
        return "increase"
    if value < 0:
        return "decrease"
    return "neutral"


def _model_feature_names(model: LoadedModel, default_features: list[str], *, exclude_gender: bool = True) -> list[str]:
    preprocessor = getattr(model.pipeline, "preprocessor", None)
    feature_names = getattr(preprocessor, "feature_names_in_", None)
    if feature_names is not None:
        features = [str(feature) for feature in list(feature_names)]
    else:
        features = [str(feature) for feature in model.metadata.get("featureList", default_features)]
    if exclude_gender:
        return [feature for feature in features if feature != "gender"]
    return features


class PurePremiumRuntime:
    def __init__(self) -> None:
        self.registry = MlflowRegistry()
        self.frequency = self._load_component("FREQUENCY", "frequency_model.joblib", "frequency_metadata.json")
        self.severity = self._load_component("SEVERITY", "severity_model.joblib", "severity_metadata.json")

    def reload(self) -> None:
        self.frequency = self._load_component("FREQUENCY", "frequency_model.joblib", "frequency_metadata.json")
        self.severity = self._load_component("SEVERITY", "severity_model.joblib", "severity_metadata.json")

    def _load_component(self, model_type: str, local_model: str, local_metadata: str) -> LoadedModel:
        source = os.getenv("MODEL_SOURCE", "auto").lower()
        if source in {"auto", "mlflow"}:
            try:
                version = self.registry.alias_version(model_type, model_alias()) or self.registry.alias_version(model_type, "Production")
                if version:
                    pipeline = self.registry.load_model_by_version(model_type, version.version)
                    metadata = self._download_metadata(version.run_id, local_metadata)
                    metadata.update(
                        {
                            "modelSource": "mlflow",
                            "registryVersion": str(version.version),
                            "registryRunId": version.run_id,
                            "selectedModel": version.tags.get("candidate_model") or metadata.get("selectedModel"),
                        }
                    )
                    return LoadedModel(pipeline, metadata, str(version.version))
            except Exception as exc:
                if source == "mlflow":
                    raise RuntimeError(f"Failed to load {model_type} from MLflow: {exc}") from exc

        artifact_dir = _local_artifact_dir()
        return LoadedModel(load(artifact_dir / local_model), _read_json(artifact_dir / local_metadata), None)

    def _download_metadata(self, run_id: str, metadata_filename: str) -> dict[str, Any]:
        for candidate_run_id in [self.registry.client.get_run(run_id).data.tags.get("mlflow.parentRunId"), run_id]:
            if not candidate_run_id:
                continue
            try:
                path = self.registry.mlflow.artifacts.download_artifacts(
                    run_id=candidate_run_id,
                    artifact_path=f"metadata/{metadata_filename}",
                )
                return _read_json(Path(path))
            except Exception:
                continue
        return {}

    @property
    def frequency_features(self) -> list[str]:
        return _model_feature_names(self.frequency, FREQUENCY_DEFAULT_FEATURES)

    @property
    def severity_features(self) -> list[str]:
        return _model_feature_names(self.severity, SEVERITY_DEFAULT_FEATURES)

    def request_to_row(self, request: PurePremiumPredictionRequest | HealthPricingPredictionRequest) -> dict[str, Any]:
        if isinstance(request, PurePremiumPredictionRequest):
            return {key: value for key, value in request.model_dump(by_alias=False).items() if key != "gender"}

        risk = request.riskProfile
        portfolio = request.portfolioProfile
        history = request.historicalExperienceFeatures
        claim_free_years = getattr(history, "claimFreeYears", None) if history else None
        row = {
            "age": risk.age,
            "seniority_insured": getattr(portfolio, "seniorityInsured", None) if portfolio else None,
            "seniority_policy": 1.0,
            "bmi": risk.bmi,
            "blood_pressure": risk.bloodPressure,
            "prev_claim_count": getattr(history, "pastClaimCount", None) if history else None,
            "prev_claim_cost": getattr(portfolio, "prevCostClaimsYear", None) if portfolio else None,
            "claim_free_years": claim_free_years,
            "years_with_history": claim_free_years,
            "type_policy": getattr(portfolio, "typePolicy", None) if portfolio else None,
            "type_policy_dg": getattr(portfolio, "typePolicy", None) if portfolio else None,
            "type_product": getattr(portfolio, "typeProduct", None) if portfolio else request.productType,
            "reimbursement": getattr(portfolio, "reimbursement", None) if portfolio else None,
            "new_business": getattr(portfolio, "newBusiness", None) if portfolio else None,
            "distribution_channel": getattr(portfolio, "distributionChannel", None) if portfolio else None,
            "smoker": risk.smoker,
            "pre_existing_condition": str(risk.preExistingCondition).lower(),
            "exercise_frequency": risk.exerciseFrequency.strip().lower(),
            "occupation_risk": risk.occupationRisk.strip().lower(),
            "prev_had_claim": getattr(portfolio, "prevHadClaimOrService", None) if portfolio else None,
            "claim_free_previous_year": getattr(portfolio, "claimFreePreviousYear", None) if portfolio else None,
            "exposure_time": getattr(portfolio, "exposureTime", None) if portfolio else 1.0,
            "prev_average_claim_severity": _safe_float(getattr(portfolio, "prevCostClaimsYear", None) if portfolio else None, 0.0),
        }
        return self._fill_defaults(row)

    def _fill_defaults(self, row: dict[str, Any]) -> dict[str, Any]:
        defaults = {**self.frequency.metadata.get("featureDefaults", {}), **self.severity.metadata.get("featureDefaults", {})}
        for feature in set(self.frequency_features + self.severity_features):
            if row.get(feature) is None or row.get(feature) == "":
                row[feature] = defaults.get(feature, 0.0)
        row["exposure_time"] = max(_safe_float(row.get("exposure_time"), 1.0), 1e-6)
        return {key: value for key, value in row.items() if key != "gender"}

    def _frames(self, row: dict[str, Any]) -> tuple[pd.DataFrame, pd.DataFrame]:
        clean = self._fill_defaults(dict(row))
        return pd.DataFrame([clean])[self.frequency_features], pd.DataFrame([clean])[self.severity_features]

    def predict_values(self, row: dict[str, Any]) -> dict[str, float]:
        freq_frame, sev_frame = self._frames(row)
        frequency_prediction = float(max(self.frequency.pipeline.predict(freq_frame)[0], 0.0))
        exposure = max(float(freq_frame.iloc[0]["exposure_time"]), 1e-6)
        predicted_frequency_annual = frequency_prediction / exposure
        predicted_severity = float(max(self.severity.pipeline.predict(sev_frame)[0], 0.0))
        predicted_pure_premium = predicted_frequency_annual * predicted_severity
        return {
            "frequencyPrediction": frequency_prediction,
            "predictedFrequencyAnnual": predicted_frequency_annual,
            "predictedSeverity": predicted_severity,
            "predictedPurePremium": predicted_pure_premium,
        }

    def active_baseline(self, type_product: str) -> dict[str, Any] | None:
        with engine().connect() as conn:
            row = (
                conn.execute(
                    select(pure_premium_baselines)
                    .where(pure_premium_baselines.c.type_product == type_product)
                    .where(pure_premium_baselines.c.frequency_model_version == str(self.frequency.version))
                    .where(pure_premium_baselines.c.severity_model_version == str(self.severity.version))
                    .where(pure_premium_baselines.c.status == "ACTIVE")
                    .limit(1)
                )
                .mappings()
                .first()
            )
        return dict(row) if row else None

    def predict(self, request: PurePremiumPredictionRequest | HealthPricingPredictionRequest) -> dict[str, Any]:
        row = self.request_to_row(request)
        values = self.predict_values(row)
        type_product = str(row.get("type_product") or "HEALTH")
        baseline = self.active_baseline(type_product)
        baseline_value = float(baseline["baseline_value"]) if baseline else max(values["predictedPurePremium"], 1e-6)
        relative_cost = values["predictedPurePremium"] / max(baseline_value, 1e-6)
        explanation_status = "available"
        try:
            top_factors = self.pure_premium_explanation(row, relative_cost)
        except Exception:
            top_factors = []
            explanation_status = "unavailable"

        return {
            "predictedAnnualFrequency": values["predictedFrequencyAnnual"],
            "predictedAverageSeverity": values["predictedSeverity"],
            "purePremium": values["predictedPurePremium"],
            "riskLevel": self.risk_level(relative_cost),
            "frequencyModelVersion": self.frequency.version,
            "severityModelVersion": self.severity.version,
            "frequencyExplanation": {
                "topFactors": top_factors,
                "method": "counterfactual_frequency_delta",
                "status": explanation_status,
            },
            "severityExplanation": {
                "topFactors": top_factors,
                "method": "counterfactual_severity_delta",
                "status": explanation_status,
            },
        }

    def risk_level(self, relative_cost: float) -> str:
        if relative_cost < 0.9:
            return "LOW"
        if relative_cost < 1.3:
            return "MEDIUM"
        return "HIGH"

    def pure_premium_explanation(self, row: dict[str, Any], relative_cost: float, limit: int = 5) -> list[dict[str, Any]]:
        defaults = {**self.frequency.metadata.get("featureDefaults", {}), **self.severity.metadata.get("featureDefaults", {})}
        factors = []
        base_values = self.predict_values(row)
        baseline_value = max(abs(base_values["predictedPurePremium"]), 1e-6)
        for feature in EXPLANATION_FEATURES:
            if feature == "gender" or feature not in row or feature not in defaults:
                continue
            baseline_feature_value = defaults[feature]
            if row.get(feature) == baseline_feature_value:
                continue
            changed = dict(row)
            changed[feature] = baseline_feature_value
            changed_values = self.predict_values(changed)
            contribution = base_values["predictedPurePremium"] - changed_values["predictedPurePremium"]
            factors.append(
                {
                    "feature": feature,
                    "currentValue": row.get(feature),
                    "baselineValue": baseline_feature_value,
                    "impact": _impact(contribution),
                    "contribution": contribution,
                    "contributionPct": 100.0 * contribution / max(abs(baseline_value), 1e-6),
                    "frequencyDelta": base_values["predictedFrequencyAnnual"] - changed_values["predictedFrequencyAnnual"],
                    "severityDelta": base_values["predictedSeverity"] - changed_values["predictedSeverity"],
                    "readableReason": f"{feature} changed the estimated pure premium relative to the training default.",
                }
            )
        return sorted(factors, key=lambda item: abs(float(item["contribution"])), reverse=True)[:limit]

    def recompute_baselines(self, model_type: str | None = None, candidate_version: str | None = None) -> None:
        freq = self.frequency
        sev = self.severity
        if model_type == "FREQUENCY" and candidate_version:
            freq = LoadedModel(self.registry.load_model_by_version("FREQUENCY", candidate_version), self.frequency.metadata, str(candidate_version))
        if model_type == "SEVERITY" and candidate_version:
            sev = LoadedModel(self.registry.load_model_by_version("SEVERITY", candidate_version), self.severity.metadata, str(candidate_version))

        df = pd.read_csv(reference_dataset_path())
        df = df[df["exposure_time"] >= 0.1].copy()
        # Baseline recompute may run while one side is still an old Production
        # artifact. Use the actual model input columns here so old artifacts that
        # still require gender can participate in the transition baseline.
        freq_features = _model_feature_names(freq, FREQUENCY_DEFAULT_FEATURES, exclude_gender=False)
        sev_features = _model_feature_names(sev, SEVERITY_DEFAULT_FEATURES, exclude_gender=False)
        pred_count = np.maximum(freq.pipeline.predict(df[freq_features]), 0.0)
        pred_freq_annual = pred_count / np.maximum(df["exposure_time"].values, 1e-6)
        pred_severity = np.maximum(sev.pipeline.predict(df[sev_features]), 0.0)
        df["predicted_pure_premium"] = pred_freq_annual * pred_severity

        now = utcnow()
        with engine().begin() as conn:
            conn.execute(
                update(pure_premium_baselines)
                .where(pure_premium_baselines.c.status == "ACTIVE")
                .values(status="INACTIVE")
            )
            for type_product, group in df.groupby("type_product"):
                total_exposure = float(group["exposure_time"].sum())
                if total_exposure <= 0:
                    continue
                baseline = float((group["exposure_time"] * group["predicted_pure_premium"]).sum() / total_exposure)
                stmt = insert(pure_premium_baselines).values(
                        type_product=str(type_product),
                        frequency_model_version=str(freq.version),
                        severity_model_version=str(sev.version),
                        dataset_version=dataset_version(),
                        baseline_value=baseline,
                        record_count=int(len(group)),
                        total_exposure=total_exposure,
                        status="ACTIVE",
                        calculated_at=now,
                    )
                conn.execute(
                    stmt.on_conflict_do_update(
                        constraint="uq_pure_premium_baseline_version",
                        set_={
                            "baseline_value": baseline,
                            "record_count": int(len(group)),
                            "total_exposure": total_exposure,
                            "status": "ACTIVE",
                            "calculated_at": now,
                        },
                    )
                )
