#!/usr/bin/env python3
"""Train Model 1: Portfolio Expected Cost Model."""

from __future__ import annotations

import argparse
import json
import platform
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import numpy as np
import pandas as pd


TARGET_COLUMN = "cost_claims_year"
ID_COLUMN = "ID"
PERIOD_COLUMN = "period"

NUMERIC_FEATURES = [
    "age",
    "exposure_time",
    "seniority_insured",
    "prev_cost_claims_year",
    "prev_n_medical_services",
]
CATEGORICAL_FEATURES = [
    "gender",
    "type_product",
    "type_policy",
    "reimbursement",
    "new_business",
    "distribution_channel",
    "prev_had_claim_or_service",
    "claim_free_previous_year",
]
MODEL_FEATURES = NUMERIC_FEATURES + CATEGORICAL_FEATURES

EXCLUDED_COLUMNS = [
    "premium",
    "n_medical_services",
    "cost_claims_year",
    "ID",
    "ID_policy",
    "ID_insured",
    "period",
    "date_effect_insured",
    "date_lapse_insured",
    "date_effect_policy",
    "date_lapse_policy",
]

PORTFOLIO_RISK_FACTOR_CLAMP = {"min": 0.7, "max": 2.5}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--input",
        default="data/health_insurance_portfolio.csv",
        help="Portfolio CSV or Excel dataset path, relative to repo root by default.",
    )
    parser.add_argument("--artifacts-dir", default="ml/artifacts")
    parser.add_argument("--reports-dir", default="ml/reports")
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--test-size", type=float, default=0.2)
    parser.add_argument(
        "--max-rows",
        type=int,
        default=80000,
        help="Deterministic row cap for local training. Use 0 to train on all rows.",
    )
    parser.add_argument(
        "--skip-xgboost",
        action="store_true",
        help="Skip optional XGBoost model comparison for a faster training run.",
    )
    parser.add_argument("--mlflow", action="store_true", help="Log and register model in MLflow.")
    parser.add_argument(
        "--mlflow-tracking-uri",
        default="http://127.0.0.1:15000",
        help="MLflow tracking server URI.",
    )
    parser.add_argument(
        "--mlflow-experiment",
        default="health-insurance-hybrid-pricing",
        help="MLflow experiment name.",
    )
    parser.add_argument(
        "--mlflow-registered-model-name",
        default="PortfolioExpectedCostModel",
        help="MLflow Model Registry name.",
    )
    return parser.parse_args()


def import_sklearn() -> dict[str, Any]:
    try:
        from joblib import dump
        from sklearn.compose import ColumnTransformer
        from sklearn.ensemble import HistGradientBoostingRegressor
        from sklearn.impute import SimpleImputer
        from sklearn.linear_model import TweedieRegressor
        from sklearn.metrics import (
            mean_absolute_error,
            mean_absolute_percentage_error,
            mean_squared_error,
            r2_score,
        )
        from sklearn.model_selection import KFold, cross_validate, train_test_split
        from sklearn.pipeline import Pipeline
        from sklearn.preprocessing import OneHotEncoder, StandardScaler
    except ModuleNotFoundError as exc:
        raise SystemExit(
            "Missing training dependency. Install requirements first, for example:\n"
            "python3 -m pip install -r ai-model-service/requirements.txt\n"
            f"Original error: {exc}"
        ) from exc

    return locals()


def make_one_hot_encoder(one_hot_cls: Any) -> Any:
    try:
        return one_hot_cls(handle_unknown="ignore", sparse_output=False)
    except TypeError:
        return one_hot_cls(handle_unknown="ignore", sparse=False)


def load_portfolio_frame(path: Path) -> pd.DataFrame:
    if path.suffix.lower() == ".csv":
        df = pd.read_csv(path)
    elif path.suffix.lower() in {".xlsx", ".xls"}:
        df = pd.read_excel(path)
    else:
        raise ValueError(f"Unsupported portfolio dataset format: {path.suffix}")
    required = {
        ID_COLUMN,
        PERIOD_COLUMN,
        TARGET_COLUMN,
        "n_medical_services",
        "age",
        "gender",
        "type_product",
        "type_policy",
        "reimbursement",
        "exposure_time",
        "seniority_insured",
        "new_business",
        "distribution_channel",
    }
    missing = sorted(required - set(df.columns))
    if missing:
        raise ValueError(f"Portfolio dataset is missing required columns: {missing}")
    return df


def add_previous_year_features(df: pd.DataFrame) -> pd.DataFrame:
    out = df.copy()
    for column in [PERIOD_COLUMN, TARGET_COLUMN, "n_medical_services"]:
        out[column] = pd.to_numeric(out[column], errors="coerce")
    out = out.sort_values([ID_COLUMN, PERIOD_COLUMN])
    out["had_claim_or_service"] = (
        out[TARGET_COLUMN].fillna(0).gt(0) | out["n_medical_services"].fillna(0).gt(0)
    )
    grouped = out.groupby(ID_COLUMN, sort=False)
    out["prev_cost_claims_year"] = grouped[TARGET_COLUMN].shift(1)
    out["prev_n_medical_services"] = grouped["n_medical_services"].shift(1)
    out["prev_had_claim_or_service"] = grouped["had_claim_or_service"].shift(1)
    out["claim_free_previous_year"] = out["prev_had_claim_or_service"].eq(False)
    out["prev_had_claim_or_service"] = out["prev_had_claim_or_service"].fillna(False)
    out["claim_free_previous_year"] = out["claim_free_previous_year"].fillna(False)
    return out


def clean_training_frame(df: pd.DataFrame) -> pd.DataFrame:
    out = add_previous_year_features(df)
    for column in NUMERIC_FEATURES + [TARGET_COLUMN]:
        out[column] = pd.to_numeric(out[column], errors="coerce")
    for column in CATEGORICAL_FEATURES:
        out[column] = out[column].astype(str).str.strip()
    out = out.dropna(subset=[TARGET_COLUMN])
    out = out[out[TARGET_COLUMN] >= 0].copy()
    return out[MODEL_FEATURES + [TARGET_COLUMN]]


def build_preprocessor(sklearn: dict[str, Any]) -> Any:
    Pipeline = sklearn["Pipeline"]
    ColumnTransformer = sklearn["ColumnTransformer"]
    SimpleImputer = sklearn["SimpleImputer"]
    StandardScaler = sklearn["StandardScaler"]
    OneHotEncoder = sklearn["OneHotEncoder"]

    numeric_pipeline = Pipeline(
        steps=[
            ("imputer", SimpleImputer(strategy="median")),
            ("scaler", StandardScaler()),
        ]
    )
    categorical_pipeline = Pipeline(
        steps=[
            ("imputer", SimpleImputer(strategy="most_frequent")),
            ("onehot", make_one_hot_encoder(OneHotEncoder)),
        ]
    )
    return ColumnTransformer(
        transformers=[
            ("numeric", numeric_pipeline, NUMERIC_FEATURES),
            ("categorical", categorical_pipeline, CATEGORICAL_FEATURES),
        ],
        remainder="drop",
    )


def build_models(sklearn: dict[str, Any], seed: int, include_xgboost: bool = True) -> dict[str, Any]:
    TweedieRegressor = sklearn["TweedieRegressor"]
    HistGradientBoostingRegressor = sklearn["HistGradientBoostingRegressor"]
    models: dict[str, Any] = {
        "tweedie_regressor": TweedieRegressor(power=1.5, alpha=0.1, link="log", max_iter=300),
        "hist_gradient_boosting": HistGradientBoostingRegressor(
            loss="squared_error",
            max_iter=180,
            learning_rate=0.06,
            l2_regularization=0.05,
            random_state=seed,
        ),
    }

    if not include_xgboost:
        return models

    try:
        from xgboost import XGBRegressor
    except Exception as exc:
        print(f"XGBoost unavailable; continuing with GLM/HistGradientBoosting models only: {exc}")
    else:
        models["xgboost_regressor"] = XGBRegressor(
            n_estimators=350,
            max_depth=3,
            learning_rate=0.04,
            subsample=0.85,
            colsample_bytree=0.85,
            objective="reg:squarederror",
            random_state=seed,
        )

    return models


def normalized_gini(y_true: pd.Series | np.ndarray, y_pred: np.ndarray) -> float:
    actual = np.asarray(y_true, dtype=float)
    pred = np.asarray(y_pred, dtype=float)
    if len(actual) == 0 or np.all(actual == actual[0]):
        return 0.0

    def gini(values: np.ndarray, scores: np.ndarray) -> float:
        order = np.lexsort((np.arange(len(scores)), -scores))
        sorted_values = values[order]
        cumulative = np.cumsum(sorted_values)
        if cumulative[-1] == 0:
            return 0.0
        lorenz = cumulative / cumulative[-1]
        random = (np.arange(len(values)) + 1) / len(values)
        return float(np.sum(lorenz - random) / len(values))

    perfect = gini(actual, actual)
    return 0.0 if perfect == 0 else float(gini(actual, pred) / perfect)


def evaluate(y_true: pd.Series, y_pred: np.ndarray, sklearn: dict[str, Any]) -> dict[str, float]:
    mean_absolute_error = sklearn["mean_absolute_error"]
    mean_absolute_percentage_error = sklearn["mean_absolute_percentage_error"]
    mean_squared_error = sklearn["mean_squared_error"]
    r2_score = sklearn["r2_score"]
    clipped = np.maximum(np.asarray(y_pred, dtype=float), 0.0)
    return {
        "MAE": float(mean_absolute_error(y_true, clipped)),
        "RMSE": float(mean_squared_error(y_true, clipped) ** 0.5),
        "R2": float(r2_score(y_true, clipped)),
        "MAPE": float(mean_absolute_percentage_error(y_true + 1.0, clipped + 1.0)),
        "NormalizedGini": normalized_gini(y_true, clipped),
    }


def cross_validate_model(
    pipeline: Any,
    x_train: pd.DataFrame,
    y_train: pd.Series,
    sklearn: dict[str, Any],
    seed: int,
) -> dict[str, float]:
    KFold = sklearn["KFold"]
    cross_validate = sklearn["cross_validate"]
    if len(x_train) > 15000:
        x_cv = x_train.sample(n=15000, random_state=seed)
        y_cv = y_train.loc[x_cv.index]
    else:
        x_cv = x_train
        y_cv = y_train
    cv = KFold(n_splits=3, shuffle=True, random_state=seed)
    scores = cross_validate(
        pipeline,
        x_cv,
        y_cv,
        cv=cv,
        scoring={"mae": "neg_mean_absolute_error", "rmse": "neg_root_mean_squared_error", "r2": "r2"},
        n_jobs=None,
    )
    return {
        "CV_MAE": float(-scores["test_mae"].mean()),
        "CV_RMSE": float(-scores["test_rmse"].mean()),
        "CV_R2": float(scores["test_r2"].mean()),
    }


def transformed_feature_names(preprocessor: Any) -> list[str]:
    try:
        return [str(name) for name in preprocessor.get_feature_names_out()]
    except Exception:
        return MODEL_FEATURES


def feature_defaults(df: pd.DataFrame) -> dict[str, Any]:
    defaults: dict[str, Any] = {}
    for column in NUMERIC_FEATURES:
        value = df[column].median()
        defaults[column] = None if pd.isna(value) else float(value)
    for column in CATEGORICAL_FEATURES:
        mode = df[column].mode(dropna=True)
        defaults[column] = str(mode.iloc[0]) if not mode.empty else ""
    return defaults


def write_json(path: Path, payload: Any) -> None:
    path.write_text(json.dumps(payload, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def markdown_table(df: pd.DataFrame) -> str:
    table = df.copy()
    columns = [str(column) for column in table.columns]

    def clean_cell(value: Any) -> str:
        if pd.isna(value):
            return ""
        if isinstance(value, float):
            return f"{value:.4f}"
        return str(value).replace("|", "\\|")

    lines = [
        "| " + " | ".join(columns) + " |",
        "| " + " | ".join(["---"] * len(columns)) + " |",
    ]
    for _, row in table.iterrows():
        lines.append("| " + " | ".join(clean_cell(row[column]) for column in table.columns) + " |")
    return "\n".join(lines)


def write_report(path: Path, metadata: dict[str, Any], comparison: pd.DataFrame) -> None:
    selected = metadata["selectedModel"]
    selected_metrics = metadata["modelMetrics"][selected]
    lines = [
        "# Portfolio Expected Cost Model Report",
        "",
        "## Purpose",
        "",
        "- Model 1 predicts annual portfolio expected claim cost.",
        "- Target is `cost_claims_year`, not `premium`.",
        "- Current-year `n_medical_services` is excluded from quote-time features.",
        "",
        "## Features",
        "",
        f"- Numeric features: `{', '.join(NUMERIC_FEATURES)}`",
        f"- Categorical features: `{', '.join(CATEGORICAL_FEATURES)}`",
        f"- Explicit exclusions: `{', '.join(EXCLUDED_COLUMNS)}`",
        "",
        "## Selected Model",
        "",
        f"- Model version: `{metadata['modelVersion']}`",
        f"- Selected model: `{selected}`",
        f"- Holdout RMSE: `{selected_metrics['RMSE']:.4f}`",
        f"- CV RMSE: `{selected_metrics['CV_RMSE']:.4f}`",
        f"- Average expected annual claim cost baseline: `{metadata['averageExpectedAnnualClaimCost']:.4f}`",
        "",
        "## Model Comparison",
        "",
        markdown_table(comparison.round(4)),
        "",
        "## Guardrails",
        "",
        "- Do not merge this dataset row-by-row with the old medical cost dataset.",
        "- Do not directly add this prediction to Model 2 output.",
        "- Pricing Service calculates final premium outside AI Model Service.",
    ]
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def log_to_mlflow(
    *,
    args: argparse.Namespace,
    fitted_pipelines: dict[str, Any],
    metadata: dict[str, Any],
    comparison_path: Path,
    report_path: Path,
    metadata_path: Path,
) -> None:
    try:
        import mlflow
        import mlflow.sklearn
        from mlflow.tracking import MlflowClient
    except ModuleNotFoundError as exc:
        raise SystemExit(
            "MLflow logging requested but mlflow is not installed. Install requirements first:\n"
            "python3 -m pip install -r ai-model-service/requirements.txt"
        ) from exc

    mlflow.set_tracking_uri(args.mlflow_tracking_uri)
    mlflow.set_experiment(args.mlflow_experiment)
    client = MlflowClient()
    selected_model = metadata["selectedModel"]
    selected_metrics = metadata["modelMetrics"][selected_model]

    def registered_version_for_run(run_id: str) -> str | None:
        versions = client.search_model_versions(f"name='{args.mlflow_registered_model_name}'")
        matching = [version for version in versions if version.run_id == run_id]
        if not matching:
            return None
        latest = max(matching, key=lambda version: int(version.creation_timestamp or 0))
        return str(latest.version)

    with mlflow.start_run(run_name=metadata["modelVersion"]):
        mlflow.set_tags(
            {
                "model_role": "portfolio_expected_cost",
                "dataset_role": metadata["datasetRole"],
                "target": metadata["targetColumn"],
                "selected_model": selected_model,
                "final_premium_model": "false",
            }
        )
        mlflow.log_params(
            {
                "selected_model": selected_model,
                "features": ",".join(metadata["featureList"]),
                "target": metadata["targetColumn"],
                "test_size": metadata["evaluationDesign"]["testSize"],
                "random_state": metadata["evaluationDesign"]["randomState"],
                "max_rows": args.max_rows,
                "clamp_min": metadata["portfolioRiskFactorFormula"]["clamp"]["min"],
                "clamp_max": metadata["portfolioRiskFactorFormula"]["clamp"]["max"],
            }
        )
        mlflow.log_metrics({key: float(value) for key, value in selected_metrics.items()})
        mlflow.log_artifact(str(metadata_path), artifact_path="metadata")
        mlflow.log_artifact(str(comparison_path), artifact_path="reports")
        mlflow.log_artifact(str(report_path), artifact_path="reports")
        parent_run_id = mlflow.active_run().info.run_id
        print(f"Logged MLflow parent run: {parent_run_id}")

        for candidate_model, pipeline in fitted_pipelines.items():
            candidate_metrics = metadata["modelMetrics"][candidate_model]
            is_best = candidate_model == selected_model
            with mlflow.start_run(
                run_name=f"{metadata['modelVersion']}-{candidate_model}",
                nested=True,
            ):
                mlflow.set_tags(
                    {
                        "model_role": "portfolio_expected_cost",
                        "dataset_role": metadata["datasetRole"],
                        "target": metadata["targetColumn"],
                        "candidate_model": candidate_model,
                        "selected_model": selected_model,
                        "is_best": str(is_best).lower(),
                        "stage": "Production" if is_best else "Candidate",
                        "final_premium_model": "false",
                    }
                )
                mlflow.log_params(
                    {
                        "candidate_model": candidate_model,
                        "selected_model": selected_model,
                        "features": ",".join(metadata["featureList"]),
                        "target": metadata["targetColumn"],
                        "test_size": metadata["evaluationDesign"]["testSize"],
                        "random_state": metadata["evaluationDesign"]["randomState"],
                        "max_rows": args.max_rows,
                        "clamp_min": metadata["portfolioRiskFactorFormula"]["clamp"]["min"],
                        "clamp_max": metadata["portfolioRiskFactorFormula"]["clamp"]["max"],
                    }
                )
                mlflow.log_metrics({key: float(value) for key, value in candidate_metrics.items()})
                result = mlflow.sklearn.log_model(
                    sk_model=pipeline,
                    name="model",
                    registered_model_name=args.mlflow_registered_model_name,
                    serialization_format=mlflow.sklearn.SERIALIZATION_FORMAT_CLOUDPICKLE,
                )
                child_run_id = mlflow.active_run().info.run_id
                version = registered_version_for_run(child_run_id)
                if version:
                    client.set_model_version_tag(
                        args.mlflow_registered_model_name,
                        version,
                        "candidate_model",
                        candidate_model,
                    )
                    client.set_model_version_tag(
                        args.mlflow_registered_model_name,
                        version,
                        "is_best",
                        str(is_best).lower(),
                    )
                    client.set_model_version_tag(
                        args.mlflow_registered_model_name,
                        version,
                        "stage",
                        "Production" if is_best else "Candidate",
                    )
                    if is_best:
                        client.set_registered_model_alias(
                            args.mlflow_registered_model_name,
                            "Production",
                            version,
                        )
                    print(
                        "Registered "
                        f"{args.mlflow_registered_model_name} version {version} "
                        f"for {candidate_model}"
                    )
                else:
                    print(
                        "Registered model version was created but could not be resolved "
                        f"for run {child_run_id}."
                    )
                print(f"Model URI: {result.model_uri}")

        print(f"Production alias points to selected model: {selected_model}")


def main() -> int:
    args = parse_args()
    sklearn = import_sklearn()
    artifacts_dir = Path(args.artifacts_dir)
    reports_dir = Path(args.reports_dir)
    artifacts_dir.mkdir(parents=True, exist_ok=True)
    reports_dir.mkdir(parents=True, exist_ok=True)

    raw_df = load_portfolio_frame(Path(args.input))
    df = clean_training_frame(raw_df)
    if args.max_rows and len(df) > args.max_rows:
        df = df.sample(n=args.max_rows, random_state=args.seed).copy()
    x = df[MODEL_FEATURES].copy()
    y = df[TARGET_COLUMN].copy()

    train_test_split = sklearn["train_test_split"]
    Pipeline = sklearn["Pipeline"]
    dump = sklearn["dump"]
    x_train, x_test, y_train, y_test = train_test_split(
        x,
        y,
        test_size=args.test_size,
        random_state=args.seed,
    )

    metrics: dict[str, dict[str, float]] = {}
    fitted_pipelines: dict[str, Any] = {}
    for name, model in build_models(sklearn, args.seed, include_xgboost=not args.skip_xgboost).items():
        pipeline = Pipeline(
            steps=[
                ("preprocessor", build_preprocessor(sklearn)),
                ("model", model),
            ]
        )
        pipeline.fit(x_train, y_train)
        predictions = pipeline.predict(x_test)
        model_metrics = evaluate(y_test, predictions, sklearn)
        model_metrics.update(cross_validate_model(pipeline, x_train, y_train, sklearn, args.seed))
        metrics[name] = model_metrics
        fitted_pipelines[name] = pipeline

    selected_model = min(metrics, key=lambda model_name: metrics[model_name]["RMSE"])
    best_pipeline = fitted_pipelines[selected_model]
    transformed_names = transformed_feature_names(best_pipeline.named_steps["preprocessor"])
    comparison = pd.DataFrame(metrics).T.reset_index().rename(columns={"index": "model"})
    comparison = comparison.sort_values(["RMSE", "CV_RMSE"]).reset_index(drop=True)

    training_date = datetime.now(timezone.utc).replace(microsecond=0).isoformat()
    model_version = f"portfolio-expected-cost-v1-{selected_model}"
    metadata = {
        "modelVersion": model_version,
        "modelPurpose": "portfolio expected annual claim cost, not final premium model",
        "datasetRole": "portfolio health insurance dataset",
        "selectedModel": selected_model,
        "trainingDate": training_date,
        "inputDataset": args.input,
        "rowCount": int(len(df)),
        "targetColumn": TARGET_COLUMN,
        "targetMeaning": "annual claim cost from portfolio dataset",
        "featureList": MODEL_FEATURES,
        "numericFeatures": NUMERIC_FEATURES,
        "categoricalFeatures": CATEGORICAL_FEATURES,
        "transformedFeatureList": transformed_names,
        "excludedColumns": EXCLUDED_COLUMNS,
        "featureDefaults": feature_defaults(x),
        "averageExpectedAnnualClaimCost": float(y_train.mean()),
        "portfolioRiskFactorFormula": {
            "portfolioRiskFactor": "predictedAnnualClaimCost / averageExpectedAnnualClaimCost",
            "clamp": PORTFOLIO_RISK_FACTOR_CLAMP,
        },
        "previousYearFeaturePolicy": (
            "Lag features are created by sorting by ID and period. Current-year "
            "cost_claims_year and current-year n_medical_services are not model inputs."
        ),
        "modelMetrics": metrics,
        "evaluationDesign": {
            "testSplit": "80/20",
            "testSize": args.test_size,
            "randomState": args.seed,
            "crossValidation": "3-fold KFold on a deterministic training sample up to 15,000 rows",
            "selectionMetric": "lowest holdout RMSE",
        },
        "pythonVersion": platform.python_version(),
        "noFinalPremiumInModel": True,
        "noMedicalCostDatasetMerge": True,
    }

    model_path = artifacts_dir / "portfolio_expected_cost_model.joblib"
    metadata_path = artifacts_dir / "portfolio_expected_cost_metadata.json"
    comparison_path = reports_dir / "portfolio_expected_cost_comparison.csv"
    report_path = reports_dir / "portfolio_expected_cost_model_report.md"

    dump(best_pipeline, model_path)
    write_json(metadata_path, metadata)
    comparison.to_csv(comparison_path, index=False)
    write_report(report_path, metadata, comparison)

    if args.mlflow:
        log_to_mlflow(
            args=args,
            fitted_pipelines=fitted_pipelines,
            metadata=metadata,
            comparison_path=comparison_path,
            report_path=report_path,
            metadata_path=metadata_path,
        )

    print(f"Selected model: {selected_model}")
    print(json.dumps(metrics[selected_model], indent=2))
    print(f"Wrote {model_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
