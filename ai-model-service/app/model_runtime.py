"""Runtime adapter for the hybrid Health pricing AI signals."""

from __future__ import annotations

import json
import os
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import numpy as np
import pandas as pd
from joblib import load

from .schemas import HealthPricingPredictionRequest


HEALTH_RISK_FEATURES = [
    "age",
    "sex",
    "bmi",
    "children",
    "smoker",
    "blood_pressure",
    "exercise_frequency",
    "pre_existing_condition",
    "occupation_risk",
]
CATEGORICAL_SOURCE_FEATURES = [
    "sex",
    "smoker",
    "exercise_frequency",
    "pre_existing_condition",
    "occupation_risk",
]
PORTFOLIO_FEATURES = [
    "age",
    "exposure_time",
    "seniority_insured",
    "prev_cost_claims_year",
    "prev_n_medical_services",
    "gender",
    "type_product",
    "type_policy",
    "reimbursement",
    "new_business",
    "distribution_channel",
    "prev_had_claim_or_service",
    "claim_free_previous_year",
]
PORTFOLIO_CATEGORICAL_SOURCE_FEATURES = [
    "gender",
    "type_product",
    "type_policy",
    "reimbursement",
    "new_business",
    "distribution_channel",
    "prev_had_claim_or_service",
    "claim_free_previous_year",
]
BASELINE_BMI = 22.0
BASELINE_SMOKER = "no"
BASELINE_BLOOD_PRESSURE = 120.0
BASELINE_EXERCISE_FREQUENCY = "daily"
BASELINE_PRE_EXISTING_CONDITION = "false"
BASELINE_OCCUPATION_RISK = "low"
HEALTH_RISK_FACTOR_MIN = 0.75
HEALTH_RISK_FACTOR_MAX = 2.5
PORTFOLIO_RISK_FACTOR_MIN = 0.7
PORTFOLIO_RISK_FACTOR_MAX = 2.5
DEFAULT_MLFLOW_TRACKING_URI = "http://127.0.0.1:15000"
DEFAULT_MLFLOW_ALIAS = "Production"
DEFAULT_HEALTH_RISK_REGISTERED_MODEL = "HealthRiskModifierModel"
DEFAULT_PORTFOLIO_REGISTERED_MODEL = "PortfolioExpectedCostModel"


def artifact_dir() -> Path:
    configured = os.getenv("MODEL_ARTIFACT_DIR")
    if configured:
        return Path(configured)
    return Path(__file__).resolve().parents[2] / "ml" / "artifacts"


def model_source_mode() -> str:
    return os.getenv("MODEL_SOURCE", "auto").strip().lower()


def mlflow_tracking_uri() -> str:
    return os.getenv("MLFLOW_TRACKING_URI", DEFAULT_MLFLOW_TRACKING_URI)


def risk_level(risk_factor: float) -> str:
    if risk_factor < 0.9:
        return "LOW"
    if risk_factor >= 1.3:
        return "HIGH"
    return "MODERATE"


def direction(value: float) -> str:
    if value > 0:
        return "increase"
    if value < 0:
        return "decrease"
    return "neutral"


def source_feature_name(transformed_name: str, categorical_sources: list[str] | None = None) -> str:
    categorical_sources = categorical_sources or CATEGORICAL_SOURCE_FEATURES
    cleaned = transformed_name.replace("numeric__", "").replace("categorical__", "")
    for source in categorical_sources:
        if cleaned.startswith(source + "_"):
            return source
    return cleaned


def readable_reason(source_feature: str, value: Any, method: str, model: str = "health") -> str:
    if model == "portfolio":
        labels = {
            "age": "Age contributes to expected annual portfolio claim cost.",
            "exposure_time": "Exposure time scales the observed claim-cost opportunity.",
            "seniority_insured": "Seniority captures tenure effects in the portfolio.",
            "type_product": "Product type changes expected claim cost in the portfolio model.",
            "type_policy": "Policy type changes expected claim cost in the portfolio model.",
            "reimbursement": "Reimbursement design affects expected claim cost.",
            "distribution_channel": "Distribution channel captures portfolio mix effects.",
            "prev_cost_claims_year": "Previous-year claim cost approximates claim history.",
            "prev_n_medical_services": "Previous-year utilization approximates medical service history.",
            "claim_free_previous_year": "Claim-free previous year changes portfolio risk signal.",
        }
        base = labels.get(source_feature, "Feature contributes to the portfolio expected cost model.")
        return f"{base} Method: {method}. Profile value: {value}."

    labels = {
        "smoker": "Smoking status changes expected medical cost in the health risk model.",
        "bmi": "BMI changes expected medical cost relative to the standard BMI baseline.",
        "age": "Age is retained in the health model but compared against the same age baseline.",
        "children": "Children/dependent count is part of the old medical cost dataset profile.",
        "sex": "Sex is retained in the health model and compared against the same sex baseline.",
        "blood_pressure": "Blood pressure changes expected medical cost relative to the standard 120.0 baseline.",
        "exercise_frequency": "Exercise frequency changes expected medical cost relative to the daily exercise baseline.",
        "pre_existing_condition": "Pre-existing condition status changes expected medical cost relative to the no pre-existing condition baseline.",
        "occupation_risk": "Occupation risk changes expected medical cost relative to the low risk baseline.",
    }
    base = labels.get(source_feature, "Feature contributes to the health risk model prediction.")
    return f"{base} Method: {method}. Profile value: {value}."


class HealthPricingRuntime:
    def __init__(self, artifacts_path: Path | None = None) -> None:
        self.artifacts_path = artifacts_path or artifact_dir()
        self.portfolio_pipeline = None
        self.portfolio_metadata: dict[str, Any] = {}
        self.health_risk_pipeline = None
        self.health_risk_metadata: dict[str, Any] = {}
        self._load_portfolio_model()
        self._load_health_risk_model()
        self._init_explainers()

    def _init_explainers(self) -> None:
        self.portfolio_explainer = None
        self.health_risk_explainer = None

        try:
            import shap
        except Exception as e:
            print(f"SHAP library not available for online explanations: {e}")
            return

        if self.portfolio_pipeline is not None:
            try:
                estimator = self.portfolio_pipeline.named_steps["model"]
                if hasattr(estimator, "feature_importances_"):
                    self.portfolio_explainer = shap.TreeExplainer(estimator)
            except Exception as e:
                print(f"Error initializing portfolio SHAP explainer: {e}")

        if self.health_risk_pipeline is not None:
            try:
                estimator = self.health_risk_pipeline.named_steps["model"]
                if hasattr(estimator, "feature_importances_"):
                    self.health_risk_explainer = shap.TreeExplainer(estimator)
            except Exception as e:
                print(f"Error initializing health risk SHAP explainer: {e}")

    def _read_local_metadata(self, filename: str) -> dict[str, Any]:
        metadata_path = self.artifacts_path / filename
        if not metadata_path.exists():
            return {}
        return json.loads(metadata_path.read_text(encoding="utf-8"))

    def _load_local_component(self, model_filename: str, metadata_filename: str) -> tuple[Any, dict[str, Any]]:
        model_path = self.artifacts_path / model_filename
        metadata_path = self.artifacts_path / metadata_filename
        if not model_path.exists() or not metadata_path.exists():
            return None, {}

        metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
        metadata["modelSource"] = "local"
        metadata["artifactPath"] = str(model_path)
        return load(model_path), metadata

    def _download_mlflow_metadata(
        self,
        *,
        mlflow: Any,
        client: Any,
        child_run_id: str,
        metadata_filename: str,
    ) -> dict[str, Any]:
        child_run = client.get_run(child_run_id)
        candidate_run_ids = [child_run.data.tags.get("mlflow.parentRunId"), child_run_id]
        for run_id in candidate_run_ids:
            if not run_id:
                continue
            try:
                path = mlflow.artifacts.download_artifacts(
                    run_id=run_id,
                    artifact_path=f"metadata/{metadata_filename}",
                )
            except Exception:
                continue
            return json.loads(Path(path).read_text(encoding="utf-8"))
        return {}

    def _load_mlflow_component(
        self,
        *,
        registered_model_name: str,
        alias: str,
        metadata_filename: str,
    ) -> tuple[Any, dict[str, Any]]:
        try:
            import mlflow
            import mlflow.sklearn
            from mlflow.tracking import MlflowClient
        except ModuleNotFoundError:
            return None, {}

        mlflow.set_tracking_uri(mlflow_tracking_uri())
        client = MlflowClient()
        model_version = client.get_model_version_by_alias(registered_model_name, alias)
        pipeline = mlflow.sklearn.load_model(f"models:/{registered_model_name}@{alias}")
        metadata = self._download_mlflow_metadata(
            mlflow=mlflow,
            client=client,
            child_run_id=model_version.run_id,
            metadata_filename=metadata_filename,
        )
        local_metadata = self._read_local_metadata(metadata_filename)
        merged_metadata = {**local_metadata, **metadata}
        merged_metadata.update(
            {
                "modelSource": "mlflow",
                "mlflowTrackingUri": mlflow_tracking_uri(),
                "registryModelName": registered_model_name,
                "registryAlias": alias,
                "registryVersion": str(model_version.version),
                "registryRunId": model_version.run_id,
                "selectedModel": (
                    metadata.get("selectedModel")
                    or model_version.tags.get("candidate_model")
                    or local_metadata.get("selectedModel")
                ),
            }
        )
        return pipeline, merged_metadata

    def _load_component(
        self,
        *,
        local_model_filename: str,
        metadata_filename: str,
        registered_model_name: str,
        alias: str,
    ) -> tuple[Any, dict[str, Any]]:
        source = model_source_mode()
        if source not in {"auto", "local", "mlflow"}:
            raise RuntimeError("MODEL_SOURCE must be one of: auto, local, mlflow.")

        if source in {"auto", "mlflow"}:
            try:
                pipeline, metadata = self._load_mlflow_component(
                    registered_model_name=registered_model_name,
                    alias=alias,
                    metadata_filename=metadata_filename,
                )
                if pipeline is not None:
                    return pipeline, metadata
            except Exception as exc:
                if source == "mlflow":
                    raise RuntimeError(
                        "Failed to load model from MLflow Registry "
                        f"{registered_model_name}@{alias}: {exc}"
                    ) from exc

        if source in {"auto", "local"}:
            return self._load_local_component(local_model_filename, metadata_filename)

        return None, {}

    def _load_portfolio_model(self) -> None:
        self.portfolio_pipeline, self.portfolio_metadata = self._load_component(
            local_model_filename="portfolio_expected_cost_model.joblib",
            metadata_filename="portfolio_expected_cost_metadata.json",
            registered_model_name=os.getenv(
                "PORTFOLIO_MODEL_REGISTRY_NAME",
                DEFAULT_PORTFOLIO_REGISTERED_MODEL,
            ),
            alias=os.getenv("PORTFOLIO_MODEL_ALIAS", DEFAULT_MLFLOW_ALIAS),
        )

    def _load_health_risk_model(self) -> None:
        self.health_risk_pipeline, self.health_risk_metadata = self._load_component(
            local_model_filename="health_risk_modifier_model.joblib",
            metadata_filename="health_risk_modifier_metadata.json",
            registered_model_name=os.getenv(
                "HEALTH_RISK_MODEL_REGISTRY_NAME",
                DEFAULT_HEALTH_RISK_REGISTERED_MODEL,
            ),
            alias=os.getenv("HEALTH_RISK_MODEL_ALIAS", DEFAULT_MLFLOW_ALIAS),
        )

    @property
    def health_risk_configured(self) -> bool:
        return self.health_risk_pipeline is not None

    @property
    def portfolio_configured(self) -> bool:
        return self.portfolio_pipeline is not None

    @property
    def portfolio_feature_list(self) -> list[str]:
        return self.portfolio_metadata.get("featureList", PORTFOLIO_FEATURES)

    @property
    def health_risk_feature_list(self) -> list[str]:
        return self.health_risk_metadata.get("featureList", HEALTH_RISK_FEATURES)

    def _component_metadata_response(
        self,
        metadata: dict[str, Any],
        configured: bool,
        default_features: list[str],
        default_target: str,
        default_purpose: str,
    ) -> dict[str, Any]:
        return {
            "status": "configured" if configured else "not_configured",
            "modelVersion": metadata.get("modelVersion"),
            "selectedModel": metadata.get("selectedModel"),
            "modelSource": metadata.get("modelSource"),
            "registryModelName": metadata.get("registryModelName"),
            "registryAlias": metadata.get("registryAlias"),
            "registryVersion": metadata.get("registryVersion"),
            "trainingDate": metadata.get("trainingDate"),
            "featureList": metadata.get("featureList", default_features),
            "targetColumn": metadata.get("targetColumn", default_target),
            "purpose": metadata.get("modelPurpose", default_purpose),
        }

    def metadata_response(self) -> dict[str, Any]:
        return {
            "serviceVersion": "hybrid-health-pricing-v1",
            "portfolioModel": self._component_metadata_response(
                self.portfolio_metadata,
                self.portfolio_configured,
                PORTFOLIO_FEATURES,
                "cost_claims_year",
                "Portfolio Expected Cost Model.",
            ),
            "healthRiskModel": self._component_metadata_response(
                self.health_risk_metadata,
                self.health_risk_configured,
                HEALTH_RISK_FEATURES,
                "charges",
                "Health risk modifier, not final premium model.",
            ),
            "noFinalPremiumInModel": True,
            "finalPremiumFormula": (
                "basePremium * portfolioRiskFactor * healthRiskFactor * "
                "underwritingRules * businessRules"
            ),
        }

    def request_to_health_frame(self, request: HealthPricingPredictionRequest) -> pd.DataFrame:
        risk = request.riskProfile
        row = {
            "age": risk.age,
            "sex": risk.sex,
            "bmi": risk.bmi,
            "children": risk.children,
            "smoker": risk.smoker,
            "blood_pressure": risk.bloodPressure,
            "exercise_frequency": risk.exerciseFrequency.strip().lower() if risk.exerciseFrequency else "",
            "pre_existing_condition": str(risk.preExistingCondition).strip().lower() if risk.preExistingCondition is not None else "false",
            "occupation_risk": risk.occupationRisk.strip().lower() if risk.occupationRisk else "",
        }
        return pd.DataFrame([row])[self.health_risk_feature_list]

    def request_to_portfolio_frame(self, request: HealthPricingPredictionRequest) -> pd.DataFrame:
        risk = request.riskProfile
        portfolio = request.portfolioProfile
        defaults = self.portfolio_metadata.get("featureDefaults", {})
        gender = getattr(portfolio, "gender", None) if portfolio else None
        if gender == "male":
            gender = "M"
        elif gender == "female":
            gender = "F"

        row = {
            "age": risk.age,
            "exposure_time": getattr(portfolio, "exposureTime", None) if portfolio else None,
            "seniority_insured": getattr(portfolio, "seniorityInsured", None) if portfolio else None,
            "prev_cost_claims_year": (
                getattr(portfolio, "prevCostClaimsYear", None) if portfolio else None
            ),
            "prev_n_medical_services": (
                getattr(portfolio, "prevNMedicalServices", None) if portfolio else None
            ),
            "gender": gender,
            "type_product": getattr(portfolio, "typeProduct", None) if portfolio else None,
            "type_policy": getattr(portfolio, "typePolicy", None) if portfolio else None,
            "reimbursement": getattr(portfolio, "reimbursement", None) if portfolio else None,
            "new_business": getattr(portfolio, "newBusiness", None) if portfolio else None,
            "distribution_channel": (
                getattr(portfolio, "distributionChannel", None) if portfolio else None
            ),
            "prev_had_claim_or_service": (
                getattr(portfolio, "prevHadClaimOrService", None) if portfolio else None
            ),
            "claim_free_previous_year": (
                getattr(portfolio, "claimFreePreviousYear", None) if portfolio else None
            ),
        }
        for feature in self.portfolio_feature_list:
            if row.get(feature) is None:
                row[feature] = defaults.get(feature)
        return pd.DataFrame([row])[self.portfolio_feature_list]

    def baseline_health_frame(self, frame: pd.DataFrame) -> pd.DataFrame:
        baseline = frame.copy()
        baseline["bmi"] = BASELINE_BMI
        baseline["smoker"] = BASELINE_SMOKER
        baseline["blood_pressure"] = BASELINE_BLOOD_PRESSURE
        baseline["exercise_frequency"] = BASELINE_EXERCISE_FREQUENCY
        baseline["pre_existing_condition"] = BASELINE_PRE_EXISTING_CONDITION
        baseline["occupation_risk"] = BASELINE_OCCUPATION_RISK
        return baseline[self.health_risk_feature_list]

    def predict(self, request: HealthPricingPredictionRequest) -> dict[str, Any]:
        return {
            "modelVersion": "hybrid-health-pricing-v1",
            "portfolioModel": self.predict_portfolio(request),
            "healthRiskModel": self.predict_health_risk(request),
            "finalPremiumCalculatedBy": "Pricing Service / Rating Engine",
            "finalPremiumFormula": (
                "basePremium * portfolioRiskFactor * healthRiskFactor * "
                "underwritingRules * businessRules"
            ),
        }

    def predict_portfolio(self, request: HealthPricingPredictionRequest) -> dict[str, Any]:
        if not self.portfolio_configured:
            return {
                "status": "not_configured",
                "modelVersion": None,
                "modelSource": None,
                "registryModelName": None,
                "registryAlias": None,
                "registryVersion": None,
                "predictedAnnualClaimCost": None,
                "rawPortfolioRiskFactor": None,
                "portfolioRiskFactor": None,
                "portfolioModelExplanation": None,
                "message": "Portfolio Expected Cost model is not configured.",
            }

        frame = self.request_to_portfolio_frame(request)
        predicted_annual_claim_cost = float(max(self.portfolio_pipeline.predict(frame)[0], 0.0))
        average_expected_cost = float(
            max(self.portfolio_metadata.get("averageExpectedAnnualClaimCost", 1.0), 1e-6)
        )
        raw_factor = predicted_annual_claim_cost / average_expected_cost
        return {
            "status": "configured",
            "modelVersion": self.portfolio_metadata.get("modelVersion"),
            "modelSource": self.portfolio_metadata.get("modelSource"),
            "registryModelName": self.portfolio_metadata.get("registryModelName"),
            "registryAlias": self.portfolio_metadata.get("registryAlias"),
            "registryVersion": self.portfolio_metadata.get("registryVersion"),
            "predictedAnnualClaimCost": predicted_annual_claim_cost,
            "rawPortfolioRiskFactor": raw_factor,
            "portfolioRiskFactor": raw_factor,
            "portfolioModelExplanation": self.fallback_portfolio_explanation(frame),
            "message": None,
        }

    def predict_health_risk(self, request: HealthPricingPredictionRequest) -> dict[str, Any]:
        if not self.health_risk_configured:
            return {
                "status": "not_configured",
                "modelVersion": None,
                "modelSource": None,
                "registryModelName": None,
                "registryAlias": None,
                "registryVersion": None,
                "predictedHealthCost": None,
                "baselineHealthCost": None,
                "rawHealthRiskFactor": None,
                "healthRiskFactor": None,
                "riskLevel": None,
                "healthRiskExplanation": None,
                "message": "Health Risk Modifier model is not configured.",
            }

        frame = self.request_to_health_frame(request)
        baseline = self.baseline_health_frame(frame)
        predicted_health_cost = float(max(self.health_risk_pipeline.predict(frame)[0], 0.0))
        baseline_health_cost = float(max(self.health_risk_pipeline.predict(baseline)[0], 1e-6))
        raw_factor = predicted_health_cost / baseline_health_cost
        return {
            "status": "configured",
            "modelVersion": self.health_risk_metadata.get("modelVersion"),
            "modelSource": self.health_risk_metadata.get("modelSource"),
            "registryModelName": self.health_risk_metadata.get("registryModelName"),
            "registryAlias": self.health_risk_metadata.get("registryAlias"),
            "registryVersion": self.health_risk_metadata.get("registryVersion"),
            "predictedHealthCost": predicted_health_cost,
            "baselineHealthCost": baseline_health_cost,
            "rawHealthRiskFactor": raw_factor,
            "healthRiskFactor": raw_factor,
            "riskLevel": risk_level(raw_factor),
            "healthRiskExplanation": self.fallback_health_risk_explanation(frame, baseline),
            "message": None,
        }

    def fallback_health_risk_explanation(
        self,
        frame: pd.DataFrame,
        baseline: pd.DataFrame,
    ) -> dict[str, Any]:
        pipeline = self.health_risk_pipeline
        estimator = pipeline.named_steps["model"]
        preprocessor = pipeline.named_steps["preprocessor"]
        transformed = preprocessor.transform(frame)
        transformed_baseline = preprocessor.transform(baseline)
        if hasattr(transformed, "toarray"):
            transformed = transformed.toarray()
        if hasattr(transformed_baseline, "toarray"):
            transformed_baseline = transformed_baseline.toarray()
        transformed = np.asarray(transformed, dtype=float)
        transformed_baseline = np.asarray(transformed_baseline, dtype=float)

        try:
            feature_names = [str(name) for name in preprocessor.get_feature_names_out()]
        except Exception:
            feature_names = self.health_risk_metadata.get(
                "transformedFeatureList",
                self.health_risk_feature_list,
            )

        shap_values = None
        method = None
        reason = None
        approximate = True

        if self.health_risk_explainer is not None:
            try:
                raw_values = self.health_risk_explainer.shap_values(transformed)
                values = np.asarray(raw_values, dtype=float)
                if values.ndim == 2:
                    values = values[0]
                elif values.ndim == 3:
                    values = values[0][0]
                if len(values) == len(feature_names):
                    shap_values = values
                    method = "shap"
                    reason = "Calculated using SHAP TreeExplainer relative to training set expected value."
                    approximate = False
            except Exception as e:
                print(f"SHAP explanation failed for health risk model, falling back: {e}")

        if shap_values is None:
            centered = transformed[0] - transformed_baseline[0]
            if hasattr(estimator, "feature_importances_"):
                weights = np.asarray(estimator.feature_importances_, dtype=float)
                method = "fallback_model_feature_importance"
                reason = "Approximated from model feature importance relative to the standard-health baseline."
            elif hasattr(estimator, "coef_"):
                weights = np.asarray(estimator.coef_, dtype=float)
                method = "fallback_model_coefficients"
                reason = "Approximated from model coefficients relative to the standard-health baseline."
            else:
                weights = np.ones(len(feature_names), dtype=float)
                method = "fallback_input_deviation"
                reason = "Approximated from input deviation relative to the standard-health baseline."

            if len(weights) != len(feature_names):
                weights = np.zeros(len(feature_names), dtype=float)
                method = "fallback_unavailable"
                reason = "No compatible model importance, coefficients, or SHAP values available."

            shap_values = centered * weights

        return self._explanation_payload(
            frame=frame,
            feature_names=feature_names,
            values=shap_values,
            method=method,
            reason=reason,
            approximate=approximate,
            model="health",
            categorical_sources=CATEGORICAL_SOURCE_FEATURES,
        )

    def fallback_portfolio_explanation(self, frame: pd.DataFrame) -> dict[str, Any]:
        pipeline = self.portfolio_pipeline
        estimator = pipeline.named_steps["model"]
        preprocessor = pipeline.named_steps["preprocessor"]
        transformed = preprocessor.transform(frame)
        if hasattr(transformed, "toarray"):
            transformed = transformed.toarray()
        transformed = np.asarray(transformed, dtype=float)

        try:
            feature_names = [str(name) for name in preprocessor.get_feature_names_out()]
        except Exception:
            feature_names = self.portfolio_metadata.get(
                "transformedFeatureList",
                self.portfolio_feature_list,
            )

        shap_values = None
        method = None
        reason = None
        approximate = True

        if self.portfolio_explainer is not None:
            try:
                raw_values = self.portfolio_explainer.shap_values(transformed)
                values = np.asarray(raw_values, dtype=float)
                if values.ndim == 2:
                    values = values[0]
                elif values.ndim == 3:
                    values = values[0][0]
                if len(values) == len(feature_names):
                    shap_values = values
                    method = "shap"
                    reason = "Calculated using SHAP TreeExplainer relative to training set expected value."
                    approximate = False
            except Exception as e:
                print(f"SHAP explanation failed for portfolio model, falling back: {e}")

        if shap_values is None:
            if hasattr(estimator, "feature_importances_"):
                weights = np.asarray(estimator.feature_importances_, dtype=float)
                method = "fallback_model_feature_importance"
                reason = "Approximated from portfolio model feature importance."
            elif hasattr(estimator, "coef_"):
                weights = np.abs(np.asarray(estimator.coef_, dtype=float))
                method = "fallback_model_coefficients"
                reason = "Approximated from portfolio model coefficients."
            else:
                weights = np.ones(len(feature_names), dtype=float)
                method = "fallback_input_magnitude"
                reason = "Approximated from transformed input magnitude."

            if len(weights) != len(feature_names):
                weights = np.zeros(len(feature_names), dtype=float)
                method = "fallback_unavailable"
                reason = "No compatible model importance, coefficients, or SHAP values available."

            shap_values = transformed[0] * weights

        return self._explanation_payload(
            frame=frame,
            feature_names=feature_names,
            values=shap_values,
            method=method,
            reason=reason,
            approximate=approximate,
            model="portfolio",
            categorical_sources=PORTFOLIO_CATEGORICAL_SOURCE_FEATURES,
        )

    def _explanation_payload(
        self,
        *,
        frame: pd.DataFrame,
        feature_names: list[str],
        values: np.ndarray,
        method: str,
        reason: str,
        model: str,
        categorical_sources: list[str],
        approximate: bool = True,
    ) -> dict[str, Any]:
        ranked = np.argsort(np.abs(values))[::-1][:8]
        detailed = []
        for index in ranked:
            source = source_feature_name(feature_names[index], categorical_sources)
            contribution = float(values[index])
            detailed.append(
                {
                    "feature": feature_names[index],
                    "sourceFeature": source,
                    "value": contribution,
                    "contribution": contribution,
                    "impact": direction(contribution),
                    "readableReason": readable_reason(
                        source,
                        frame.iloc[0].get(source),
                        method,
                        model=model,
                    ),
                    "reason": reason,
                    "approximate": approximate,
                }
            )

        aggregate: dict[str, float] = {}
        for item in detailed:
            source = str(item["sourceFeature"])
            aggregate[source] = aggregate.get(source, 0.0) + float(item["contribution"])

        top_factors = []
        for feature, contribution in sorted(
            aggregate.items(),
            key=lambda pair: abs(pair[1]),
            reverse=True,
        )[:6]:
            top_factors.append(
                {
                    "feature": feature,
                    "sourceFeature": feature,
                    "value": contribution,
                    "contribution": contribution,
                    "impact": direction(contribution),
                    "readableReason": readable_reason(
                        feature,
                        frame.iloc[0].get(feature),
                        method,
                        model=model,
                    ),
                    "reason": reason,
                    "approximate": approximate,
                }
            )

        return {
            "topRiskFactors": top_factors,
            "featureContributions": detailed,
            "shapValues": detailed,
            "method": method,
            "generatedAt": datetime.now(timezone.utc).replace(microsecond=0).isoformat(),
        }
