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
        # MLflow may still serve older champion/Production artifacts trained with
        # gender. Keep the pipeline-declared input contract so those artifacts
        # remain usable until they are replaced by promoted no-gender versions.
        return features
    else:
        features = [str(feature) for feature in model.metadata.get("featureList", default_features)]
    if exclude_gender:
        return [feature for feature in features if feature != "gender"]
    return features


DISPLAY_NAMES = {
    "age": "Tuổi",
    "sex": "Giới tính",
    "gender": "Giới tính",
    "seniority_insured": "Số năm tham gia bảo hiểm",
    "seniority_policy": "Thời gian hiệu lực hợp đồng",
    "bmi": "Chỉ số khối cơ thể (BMI)",
    "blood_pressure": "Chỉ số huyết áp",
    "prev_claim_count": "Số lần bồi thường trước đây",
    "prev_claim_cost": "Chi phí bồi thường trước đây",
    "claim_free_years": "Số năm liên tục không claim",
    "years_with_history": "Số năm có lịch sử bảo hiểm",
    "type_policy": "Loại hợp đồng bảo hiểm",
    "type_policy_dg": "Loại hợp đồng bảo hiểm",
    "type_product": "Loại gói sản phẩm",
    "reimbursement": "Điều khoản đồng chi trả",
    "new_business": "Loại hình hợp đồng mới",
    "distribution_channel": "Kênh phân phối",
    "smoker": "Tình trạng hút thuốc",
    "pre_existing_condition": "Tiền sử bệnh nền",
    "exercise_frequency": "Tần suất tập thể dục",
    "occupation_risk": "Mức độ rủi ro nghề nghiệp",
    "prev_had_claim": "Có yêu cầu bồi thường kỳ trước",
    "claim_free_previous_year": "Không claim năm trước",
    "exposure_time": "Thời gian tiếp xúc rủi ro",
    "prev_average_claim_severity": "Mức bồi thường trung bình trước đây"
}


def _format_value(feature: str, val: Any) -> str:
    if val is True or str(val).lower() == "true" or str(val).lower() == "yes":
        return "Có"
    if val is False or str(val).lower() == "false" or str(val).lower() == "no":
        return "Không"
    if feature == "exercise_frequency":
        mapping = {"daily": "Hàng ngày", "weekly": "Hàng tuần", "rarely": "Hiếm khi", "never": "Không bao giờ"}
        return mapping.get(str(val).strip().lower(), str(val))
    if feature == "occupation_risk":
        mapping = {"low": "Thấp", "medium": "Trung bình", "high": "Cao"}
        return mapping.get(str(val).strip().lower(), str(val))
    if isinstance(val, (int, float)):
        if float(val).is_integer():
            return str(int(val))
        return f"{val:.2f}"
    return str(val)


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
        metadata = _read_json(artifact_dir / local_metadata)
        version = metadata.get("modelVersion")
        return LoadedModel(load(artifact_dir / local_model), metadata, version)

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
            return request.model_dump(by_alias=False)

        risk = request.riskProfile
        portfolio = request.portfolioProfile
        history = request.historicalExperienceFeatures

        claim_free_years = getattr(history, "claimFreeYears", 0) if history else 0
        gender = getattr(portfolio, "gender", None) if portfolio else None
        if gender is None:
            gender = {"male": "M", "female": "F"}.get(risk.sex, risk.sex)

        seniority_insured = getattr(portfolio, "seniorityInsured", 0.0) if portfolio else 0.0
        prev_n_med = getattr(portfolio, "prevNMedicalServices", 0.0) if portfolio else 0.0
        prev_cost = getattr(portfolio, "prevCostClaimsYear", 0.0) if portfolio else 0.0

        prev_avg_sev = 0.0
        if prev_n_med > 0:
            prev_avg_sev = prev_cost / prev_n_med

        row = {
            "age": risk.age,
            "gender": gender,
            "seniority_insured": seniority_insured,
            "seniority_policy": seniority_insured,
            "bmi": risk.bmi,
            "blood_pressure": risk.bloodPressure,
            "prev_claim_count": prev_n_med,
            "prev_claim_cost": prev_cost,
            "claim_free_years": claim_free_years,
            "years_with_history": seniority_insured,
            "type_policy": getattr(portfolio, "typePolicy", "I") if portfolio else "I",
            "type_policy_dg": getattr(portfolio, "typePolicy", "I") if portfolio else "I",
            "type_product": getattr(portfolio, "typeProduct", "S") if portfolio else "S",
            "reimbursement": getattr(portfolio, "reimbursement", "No") if portfolio else "No",
            "new_business": getattr(portfolio, "newBusiness", "Yes") if portfolio else "Yes",
            "distribution_channel": getattr(portfolio, "distributionChannel", "D") if portfolio else "D",
            "smoker": risk.smoker,
            "pre_existing_condition": str(risk.preExistingCondition).lower(),
            "exercise_frequency": risk.exerciseFrequency.strip().lower(),
            "occupation_risk": risk.occupationRisk.strip().lower(),
            "prev_had_claim": getattr(portfolio, "prevHadClaimOrService", False) if portfolio else False,
            "claim_free_previous_year": getattr(portfolio, "claimFreePreviousYear", True) if portfolio else True,
            "exposure_time": getattr(portfolio, "exposureTime", 1.0) if portfolio else 1.0,
            "prev_average_claim_severity": prev_avg_sev,
        }
        return self._fill_defaults(row)

    def _fill_defaults(self, row: dict[str, Any]) -> dict[str, Any]:
        defaults = {**self.frequency.metadata.get("featureDefaults", {}), **self.severity.metadata.get("featureDefaults", {})}
        for feature in set(self.frequency_features + self.severity_features):
            if row.get(feature) is None or row.get(feature) == "":
                row[feature] = defaults.get(feature, 0.0)
        row["exposure_time"] = max(_safe_float(row.get("exposure_time"), 1.0), 1e-6)
        return row

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
            explanations = self.calculate_shap_explanations(row)
            freq_explanation = explanations["frequencyExplanation"]
            sev_explanation = explanations["severityExplanation"]
            top_risk_factors = explanations["topRiskFactors"]
        except Exception as exc:
            import traceback
            traceback.print_exc()
            explanation_status = "unavailable"
            freq_explanation = {
                "topFactors": [],
                "baseValue": 0.0,
                "predictedValue": values["predictedFrequencyAnnual"],
                "outputScale": "prediction",
                "method": "counterfactual",
                "status": explanation_status,
            }
            sev_explanation = {
                "topFactors": [],
                "baseValue": 0.0,
                "predictedValue": values["predictedSeverity"],
                "outputScale": "prediction",
                "method": "counterfactual",
                "status": explanation_status,
            }
            top_risk_factors = []

        return {
            "predictedAnnualFrequency": values["predictedFrequencyAnnual"],
            "predictedAverageSeverity": values["predictedSeverity"],
            "purePremium": values["predictedPurePremium"],
            "riskLevel": self.risk_level(relative_cost),
            "frequencyModelVersion": self.frequency.version,
            "severityModelVersion": self.severity.version,
            "frequencyExplanation": freq_explanation,
            "severityExplanation": sev_explanation,
            "topRiskFactors": top_risk_factors,
        }

    def risk_level(self, relative_cost: float) -> str:
        if relative_cost < 0.9:
            return "LOW"
        if relative_cost < 1.3:
            return "MEDIUM"
        return "HIGH"

    def calculate_shap_explanations(self, row: dict[str, Any], limit: int = 5) -> dict[str, Any]:
        defaults = {**self.frequency.metadata.get("featureDefaults", {}), **self.severity.metadata.get("featureDefaults", {})}
        freq_factors = []
        sev_factors = []
        
        # Predict baseline values (where all features are defaults)
        baseline_row = dict(row)
        for feat in defaults:
            baseline_row[feat] = defaults[feat]
        baseline_preds = self.predict_values(baseline_row)
        base_freq = baseline_preds["predictedFrequencyAnnual"]
        base_sev = baseline_preds["predictedSeverity"]
        
        # Predict actual values
        actual_preds = self.predict_values(row)
        actual_freq = actual_preds["predictedFrequencyAnnual"]
        actual_sev = actual_preds["predictedSeverity"]
        
        for feature in EXPLANATION_FEATURES:
            if feature == "gender" or feature not in row or feature not in defaults:
                continue
            
            baseline_feature_value = defaults[feature]
            actual_val = row.get(feature)
            
            if actual_val == baseline_feature_value:
                continue
                
            changed = dict(row)
            changed[feature] = baseline_feature_value
            changed_preds = self.predict_values(changed)
            
            shap_freq = actual_freq - changed_preds["predictedFrequencyAnnual"]
            shap_sev = actual_sev - changed_preds["predictedSeverity"]
            
            display_name = DISPLAY_NAMES.get(feature, feature.replace("_", " ").title())
            formatted_val = _format_value(feature, actual_val)
            
            if abs(shap_freq) >= 0.05:
                freq_factors.append({
                    "feature": feature,
                    "displayName": display_name,
                    "currentValue": formatted_val,
                    "shapValue": float(shap_freq),
                    "impact": _impact(shap_freq),
                    "readableReason": f"{display_name} ({formatted_val}) làm " + ("tăng" if shap_freq > 0 else "giảm") + " tần suất bồi thường dự đoán."
                })
                
            if abs(shap_sev) >= 0.2:
                sev_factors.append({
                    "feature": feature,
                    "displayName": display_name,
                    "currentValue": formatted_val,
                    "shapValue": float(shap_sev),
                    "impact": _impact(shap_sev),
                    "readableReason": f"{display_name} ({formatted_val}) làm " + ("tăng" if shap_sev > 0 else "giảm") + " chi phí bồi thường trung bình mỗi lần."
                })
                
        # Sort and rank frequency factors
        freq_factors.sort(key=lambda x: abs(x["shapValue"]), reverse=True)
        sum_abs_freq = sum(abs(x["shapValue"]) for x in freq_factors)
        for r, item in enumerate(freq_factors, 1):
            item["rank"] = r
            item["contributionPct"] = round(100.0 * abs(item["shapValue"]) / sum_abs_freq, 2) if sum_abs_freq > 0 else 0.0
            
        # Sort and rank severity factors
        sev_factors.sort(key=lambda x: abs(x["shapValue"]), reverse=True)
        sum_abs_sev = sum(abs(x["shapValue"]) for x in sev_factors)
        for r, item in enumerate(sev_factors, 1):
            item["rank"] = r
            item["contributionPct"] = round(100.0 * abs(item["shapValue"]) / sum_abs_sev, 2) if sum_abs_sev > 0 else 0.0
        
        combined_scores = {}
        for feature in EXPLANATION_FEATURES:
            freq_item = next((x for x in freq_factors if x["feature"] == feature), None)
            sev_item = next((x for x in sev_factors if x["feature"] == feature), None)
            
            if not freq_item and not sev_item:
                continue
                
            score_freq = abs(freq_item["shapValue"]) / sum_abs_freq if freq_item and sum_abs_freq > 0 else 0.0
            score_sev = abs(sev_item["shapValue"]) / sum_abs_sev if sev_item and sum_abs_sev > 0 else 0.0
            combined_score = score_freq + score_sev
            
            affected_models = []
            if freq_item:
                affected_models.append("frequency")
            if sev_item:
                affected_models.append("severity")
                
            freq_impact = freq_item["impact"] if freq_item else "neutral"
            sev_impact = sev_item["impact"] if sev_item else "neutral"
            
            display_name = DISPLAY_NAMES.get(feature, feature.replace("_", " ").title())
            formatted_val = _format_value(feature, row.get(feature))
            
            if freq_impact == "increase" and sev_impact == "increase":
                readable_reason = f"{display_name} ({formatted_val}) làm tăng cả khả năng phát sinh và chi phí bồi thường."
            elif freq_impact == "decrease" and sev_impact == "decrease":
                readable_reason = f"{display_name} ({formatted_val}) giúp giảm cả khả năng phát sinh và chi phí bồi thường."
            elif freq_impact == "increase" and sev_impact == "neutral":
                readable_reason = f"{display_name} ({formatted_val}) làm tăng khả năng phát sinh bồi thường."
            elif freq_impact == "neutral" and sev_impact == "increase":
                readable_reason = f"{display_name} ({formatted_val}) làm tăng chi phí bồi thường dự kiến mỗi lần."
            elif freq_impact == "decrease" and sev_impact == "neutral":
                readable_reason = f"{display_name} ({formatted_val}) giúp giảm khả năng phát sinh bồi thường."
            elif freq_impact == "neutral" and sev_impact == "decrease":
                readable_reason = f"{display_name} ({formatted_val}) giúp giảm chi phí bồi thường dự kiến mỗi lần."
            else:
                readable_reason = f"{display_name} ({formatted_val}) ảnh hưởng đến đánh giá rủi ro."
                
            combined_scores[feature] = {
                "feature": feature,
                "displayName": display_name,
                "currentValue": formatted_val,
                "affectedModels": affected_models,
                "frequencyImpact": freq_impact,
                "severityImpact": sev_impact,
                "combinedScore": round(combined_score, 2),
                "readableReason": readable_reason
            }
            
        top_risk_factors = list(combined_scores.values())
        top_risk_factors.sort(key=lambda x: x["combinedScore"], reverse=True)
        
        return {
            "frequencyExplanation": {
                "topFactors": freq_factors[:limit],
                "baseValue": float(base_freq),
                "predictedValue": float(actual_freq),
                "outputScale": "prediction",
                "method": "counterfactual",
                "status": "available",
            },
            "severityExplanation": {
                "topFactors": sev_factors[:limit],
                "baseValue": float(base_sev),
                "predictedValue": float(actual_sev),
                "outputScale": "prediction",
                "method": "counterfactual",
                "status": "available",
            },
            "topRiskFactors": top_risk_factors[:limit]
        }

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
