#!/usr/bin/env python3
"""Generate demo explanations for the Health Risk Modifier model."""

from __future__ import annotations

import argparse
import json
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import numpy as np
import pandas as pd

from train_health_risk_modifier_model import (
    BASELINE_HEALTH_PROFILE,
    CATEGORICAL_FEATURES,
    HEALTH_RISK_FACTOR_CLAMP,
    MODEL_FEATURES,
    NUMERIC_FEATURES,
    TARGET_COLUMN,
)


DEMO_PROFILES: list[dict[str, Any]] = [
    {
        "profileName": "young healthy non-smoker",
        "age": 24,
        "sex": "female",
        "bmi": 22.0,
        "children": 0,
        "smoker": "no",
        "blood_pressure": 115.0,
        "exercise_frequency": "daily",
        "pre_existing_condition": "false",
        "occupation_risk": "low",
    },
    {
        "profileName": "middle-aged smoker with high BMI",
        "age": 45,
        "sex": "male",
        "bmi": 34.5,
        "children": 1,
        "smoker": "yes",
        "blood_pressure": 145.0,
        "exercise_frequency": "never",
        "pre_existing_condition": "true",
        "occupation_risk": "moderate",
    },
    {
        "profileName": "older customer with high BMI",
        "age": 61,
        "sex": "female",
        "bmi": 36.0,
        "children": 0,
        "smoker": "no",
        "blood_pressure": 135.0,
        "exercise_frequency": "rarely",
        "pre_existing_condition": "false",
        "occupation_risk": "high",
    },
]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", default="data/health_insurance_cost_and_risk_dataset.csv")
    parser.add_argument("--artifacts-dir", default="ml/artifacts")
    parser.add_argument("--reports-dir", default="ml/reports")
    return parser.parse_args()


def load_joblib() -> Any:
    try:
        from joblib import load
    except ModuleNotFoundError as exc:
        raise SystemExit(
            "Missing joblib. Install requirements first, for example:\n"
            "python3 -m pip install -r ai-model-service/requirements.txt\n"
            f"Original error: {exc}"
        ) from exc
    return load


def read_json(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def write_json(path: Path, payload: Any) -> None:
    path.write_text(json.dumps(payload, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def load_background(path: Path) -> tuple[pd.DataFrame, pd.Series]:
    df = pd.read_csv(path)
    missing = sorted(set(MODEL_FEATURES + [TARGET_COLUMN]) - set(df.columns))
    if missing:
        raise ValueError(f"Explanation dataset is missing columns: {missing}")

    out = df[MODEL_FEATURES + [TARGET_COLUMN]].copy()
    for column in NUMERIC_FEATURES + [TARGET_COLUMN]:
        out[column] = pd.to_numeric(out[column], errors="coerce")
    for column in CATEGORICAL_FEATURES:
        out[column] = out[column].astype(str).str.strip().str.lower()
    out = out.dropna(subset=[TARGET_COLUMN])
    out = out[out[TARGET_COLUMN] > 0].copy()
    return out[MODEL_FEATURES], out[TARGET_COLUMN]


def transformed_matrix(pipeline: Any, x: pd.DataFrame) -> np.ndarray:
    transformed = pipeline.named_steps["preprocessor"].transform(x)
    if hasattr(transformed, "toarray"):
        transformed = transformed.toarray()
    return np.asarray(transformed, dtype=float)


def transformed_feature_names(pipeline: Any, metadata: dict[str, Any]) -> list[str]:
    try:
        return [str(name) for name in pipeline.named_steps["preprocessor"].get_feature_names_out()]
    except Exception:
        return metadata.get("transformedFeatureList", MODEL_FEATURES)


def source_feature_name(transformed_name: str) -> str:
    cleaned = transformed_name.replace("numeric__", "").replace("categorical__", "")
    for source in CATEGORICAL_FEATURES:
        if cleaned.startswith(source + "_"):
            return source
    return cleaned


def direction(value: float) -> str:
    if value > 0:
        return "increase"
    if value < 0:
        return "decrease"
    return "neutral"


def readable_reason(source_feature: str, value: Any, method: str) -> str:
    labels = {
        "smoker": "Smoking status changes expected medical cost in the health risk model.",
        "bmi": "BMI changes expected medical cost relative to the standard BMI baseline.",
        "age": "Age is retained in the health model but the risk factor compares against the same age baseline.",
        "children": "Children/dependent count is part of the old medical cost dataset profile.",
        "sex": "Sex is retained in the health model and compared against the same sex baseline.",
        "blood_pressure": "Blood pressure changes expected medical cost relative to the standard 120.0 baseline.",
        "exercise_frequency": "Exercise frequency changes expected medical cost relative to the daily exercise baseline.",
        "pre_existing_condition": "Pre-existing condition status changes expected medical cost relative to the no pre-existing condition baseline.",
        "occupation_risk": "Occupation risk changes expected medical cost relative to the low risk baseline.",
    }
    base = labels.get(source_feature, "Feature contributes to the health risk model prediction.")
    return f"{base} Method: {method}. Profile value: {value}."


def aggregate_by_source(
    values: list[dict[str, Any]],
    profile: pd.Series,
    method: str,
    limit: int = 6,
) -> list[dict[str, Any]]:
    aggregate: dict[str, float] = {}
    for item in values:
        source = item["sourceFeature"]
        aggregate[source] = aggregate.get(source, 0.0) + float(item["contribution"])

    ranked = sorted(aggregate.items(), key=lambda pair: abs(pair[1]), reverse=True)[:limit]
    return [
        {
            "feature": feature,
            "contribution": float(impact),
            "impact": direction(float(impact)),
            "readableReason": readable_reason(feature, profile.get(feature), method),
        }
        for feature, impact in ranked
    ]


def shap_values(
    pipeline: Any,
    x_background: np.ndarray,
    x_sample: np.ndarray,
    feature_names: list[str],
) -> tuple[str, list[float]] | None:
    try:
        import shap
    except Exception:
        return None

    estimator = pipeline.named_steps["model"]
    try:
        if hasattr(estimator, "feature_importances_"):
            explainer = shap.TreeExplainer(estimator)
            raw_values = explainer.shap_values(x_sample)
        else:
            masker = shap.maskers.Independent(x_background[:100])
            explainer = shap.Explainer(estimator.predict, masker)
            raw_values = explainer(x_sample).values
    except Exception:
        return None

    values = np.asarray(raw_values, dtype=float)
    if values.ndim == 2:
        values = values[0]
    if len(values) != len(feature_names):
        return None
    return "shap", [float(value) for value in values]


def fallback_values(
    pipeline: Any,
    x_background: np.ndarray,
    x_sample: np.ndarray,
    x_baseline: np.ndarray,
    feature_names: list[str],
) -> tuple[str, list[float]]:
    estimator = pipeline.named_steps["model"]
    centered = x_sample[0] - x_baseline[0]

    if hasattr(estimator, "feature_importances_"):
        weights = np.asarray(estimator.feature_importances_, dtype=float)
        method = "fallback_model_feature_importance"
        values = centered * weights
    elif hasattr(estimator, "coef_"):
        weights = np.asarray(estimator.coef_, dtype=float)
        method = "fallback_model_coefficients"
        values = centered * weights
    else:
        method = "fallback_input_deviation"
        values = x_sample[0] - np.nanmean(x_background, axis=0)

    if len(values) != len(feature_names):
        values = np.zeros(len(feature_names), dtype=float)
        method = "fallback_unavailable"
    return method, [float(value) for value in values]


def baseline_profile(profile: pd.DataFrame) -> pd.DataFrame:
    baseline = profile.copy()
    baseline["bmi"] = BASELINE_HEALTH_PROFILE["bmi"]
    baseline["smoker"] = BASELINE_HEALTH_PROFILE["smoker"]
    baseline["blood_pressure"] = BASELINE_HEALTH_PROFILE["blood_pressure"]
    baseline["exercise_frequency"] = BASELINE_HEALTH_PROFILE["exercise_frequency"]
    baseline["pre_existing_condition"] = BASELINE_HEALTH_PROFILE["pre_existing_condition"]
    baseline["occupation_risk"] = BASELINE_HEALTH_PROFILE["occupation_risk"]
    return baseline[MODEL_FEATURES]


def health_risk_factor(pipeline: Any, profile: pd.DataFrame) -> dict[str, float]:
    predicted = float(max(pipeline.predict(profile[MODEL_FEATURES])[0], 0.0))
    baseline = float(max(pipeline.predict(baseline_profile(profile))[0], 1e-6))
    raw_factor = predicted / baseline
    return {
        "predictedHealthCost": predicted,
        "baselineHealthCost": baseline,
        "rawHealthRiskFactor": raw_factor,
        "healthRiskFactor": float(
            np.clip(
                raw_factor,
                HEALTH_RISK_FACTOR_CLAMP["min"],
                HEALTH_RISK_FACTOR_CLAMP["max"],
            )
        ),
    }


def explain_profile(
    pipeline: Any,
    metadata: dict[str, Any],
    x_background: pd.DataFrame,
    profile_payload: dict[str, Any],
) -> dict[str, Any]:
    profile_name = str(profile_payload["profileName"])
    profile = pd.DataFrame([{key: value for key, value in profile_payload.items() if key != "profileName"}])
    for column in CATEGORICAL_FEATURES:
        profile[column] = profile[column].astype(str).str.strip().str.lower()
    profile = profile[MODEL_FEATURES]

    x_transformed = transformed_matrix(pipeline, x_background)
    sample_transformed = transformed_matrix(pipeline, profile)
    baseline_transformed = transformed_matrix(pipeline, baseline_profile(profile))
    feature_names = transformed_feature_names(pipeline, metadata)

    result = shap_values(pipeline, x_transformed, sample_transformed, feature_names)
    if result:
        method, raw_contributions = result
    else:
        method, raw_contributions = fallback_values(
            pipeline,
            x_transformed,
            sample_transformed,
            baseline_transformed,
            feature_names,
        )

    ranked = np.argsort(np.abs(raw_contributions))[::-1][:10]
    detail = [
        {
            "feature": feature_names[index],
            "sourceFeature": source_feature_name(feature_names[index]),
            "contribution": float(raw_contributions[index]),
            "impact": direction(float(raw_contributions[index])),
            "readableReason": readable_reason(
                source_feature_name(feature_names[index]),
                profile.iloc[0].get(source_feature_name(feature_names[index])),
                method,
            ),
            "approximate": method != "shap",
        }
        for index in ranked
    ]
    top_factors = aggregate_by_source(detail, profile.iloc[0], method)

    return {
        "profileName": profile_name,
        "profile": profile.iloc[0].to_dict(),
        **health_risk_factor(pipeline, profile),
        "explanation": {
            "topRiskFactors": top_factors,
            "featureContributions": detail,
            "method": method,
            "generatedAt": datetime.now(timezone.utc).replace(microsecond=0).isoformat(),
        },
    }


def main() -> int:
    args = parse_args()
    artifacts_dir = Path(args.artifacts_dir)
    reports_dir = Path(args.reports_dir)
    reports_dir.mkdir(parents=True, exist_ok=True)

    load = load_joblib()
    model_path = artifacts_dir / "health_risk_modifier_model.joblib"
    metadata_path = artifacts_dir / "health_risk_modifier_metadata.json"
    if not model_path.exists() or not metadata_path.exists():
        raise SystemExit(
            "Missing Health Risk Modifier artifact. Run:\n"
            "python3 ml/scripts/train_health_risk_modifier_model.py"
        )

    pipeline = load(model_path)
    metadata = read_json(metadata_path)
    x_background, _ = load_background(Path(args.input))
    output = {
        "modelVersion": metadata["modelVersion"],
        "modelPurpose": metadata.get("modelPurpose", "health risk modifier"),
        "baselineHealthProfile": metadata.get("baselineHealthProfile", BASELINE_HEALTH_PROFILE),
        "healthRiskFactorClamp": HEALTH_RISK_FACTOR_CLAMP,
        "examples": [
            explain_profile(pipeline, metadata, x_background, demo_profile)
            for demo_profile in DEMO_PROFILES
        ],
    }
    output_path = reports_dir / "health_risk_modifier_shap_examples.json"
    write_json(output_path, output)
    print(f"Wrote {output_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
