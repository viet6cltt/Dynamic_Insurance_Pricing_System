#!/usr/bin/env python3
"""Train Model: Severity Model using Gamma GLM vs. XGBoost Lognormal."""

from __future__ import annotations

import argparse
import json
import platform
import sys
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import numpy as np
import pandas as pd

# Add ai-model-service to sys.path so we can import the custom model wrappers
sys.path.append(str(Path(__file__).resolve().parents[2] / "ai-model-service"))
from app.custom_models import (
    GammaGLMPipeline,
    XGBoostLognormalPipeline
)

TARGET_COLUMN = "average_claim_severity"

NUMERIC_FEATURES = [
    "age",
    "seniority_insured",
    "bmi",
    "blood_pressure",
    "prev_claim_cost",
    "prev_claim_count",
    "prev_average_claim_severity",
]

CATEGORICAL_FEATURES = [
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

MODEL_FEATURES = NUMERIC_FEATURES + CATEGORICAL_FEATURES

EXCLUDED_COLUMNS = [
    "claim_count",
    "had_claim",
    "annual_claim_cost",
    "average_claim_severity",
    "is_synthetic_health_profile",
    "is_synthetic_claim_count",
    "is_synthetic_severity",
    "generation_version",
    "random_seed",
    "synthetic_record_id",
    "source_record_id",
]


@dataclass
class TrainingResult:
    mlflowRunId: str | None
    registeredModelName: str
    registeredModelVersion: str | None
    selectedAlgorithm: str
    metrics: dict[str, float]
    datasetVersion: str
    modelType: str = "SEVERITY"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--input",
        default="data/generated/severity_training_dataset.csv",
        help="Path to the severity training dataset.",
    )
    parser.add_argument("--artifacts-dir", default="ml/artifacts")
    parser.add_argument("--reports-dir", default="ml/reports")
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--test-size", type=float, default=0.2)
    parser.add_argument(
        "--max-rows",
        type=int,
        default=None,
        help="Optional deterministic row cap for training. Omit or use 0 to train on all rows.",
    )
    parser.add_argument(
        "--skip-xgboost",
        action="store_true",
        help="Skip optional XGBoost model comparison.",
    )
    parser.add_argument("--mlflow", action="store_true", help="Log and register model in MLflow.")
    parser.add_argument(
        "--mlflow-tracking-uri",
        default="http://127.0.0.1:15000",
        help="MLflow tracking server URI.",
    )
    parser.add_argument(
        "--mlflow-experiment",
        default="health-insurance-severity",
        help="MLflow experiment name.",
    )
    parser.add_argument(
        "--mlflow-registered-model-name",
        default="SeverityModel",
        help="MLflow Model Registry name.",
    )
    parser.add_argument("--training-job-id", default=None)
    parser.add_argument("--dataset-version", default="generated-current")
    parser.add_argument(
        "--deployment-mode",
        choices=["candidate", "legacy-production"],
        default="candidate",
        help="candidate registers review candidates without changing aliases.",
    )
    return parser.parse_args()


def import_sklearn() -> dict[str, Any]:
    try:
        from joblib import dump
        from sklearn.compose import ColumnTransformer
        from sklearn.impute import SimpleImputer
        from sklearn.model_selection import KFold, train_test_split
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


def load_severity_frame(path: Path) -> pd.DataFrame:
    if not path.exists():
        raise FileNotFoundError(f"Severity training dataset not found at: {path}")
    df = pd.read_csv(path)
    
    required = {TARGET_COLUMN} | set(NUMERIC_FEATURES) | set(CATEGORICAL_FEATURES)
    missing = sorted(required - set(df.columns))
    if missing:
        raise ValueError(f"Severity training dataset is missing required columns: {missing}")
    return df


def clean_training_frame(df: pd.DataFrame) -> pd.DataFrame:
    out = df.copy()
    for column in NUMERIC_FEATURES + [TARGET_COLUMN]:
        if column in out.columns:
            out[column] = pd.to_numeric(out[column], errors="coerce")
    for column in CATEGORICAL_FEATURES:
        if column in out.columns:
            out[column] = out[column].astype(str).str.strip()
    out = out.dropna(subset=[TARGET_COLUMN])
    out = out[out[TARGET_COLUMN] > 0].copy()
    return out


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


def weighted_mean_absolute_error(y_true: np.ndarray, y_pred: np.ndarray, sample_weight: np.ndarray | None = None) -> float:
    if sample_weight is None:
        sample_weight = np.ones_like(y_true)
    return float(np.average(np.abs(y_true - y_pred), weights=sample_weight))


def weighted_root_mean_squared_error(y_true: np.ndarray, y_pred: np.ndarray, sample_weight: np.ndarray | None = None) -> float:
    if sample_weight is None:
        sample_weight = np.ones_like(y_true)
    return float(np.sqrt(np.average((y_true - y_pred) ** 2, weights=sample_weight)))


def mean_gamma_deviance(y_true: np.ndarray, y_pred: np.ndarray, sample_weight: np.ndarray | None = None) -> float:
    y_true = np.maximum(y_true, 1e-9)
    y_pred = np.maximum(y_pred, 1e-9)
    if sample_weight is None:
        sample_weight = np.ones_like(y_true)
    
    deviance_elements = 2.0 * (-np.log(y_true / y_pred) + (y_true - y_pred) / y_pred)
    return float(np.average(deviance_elements, weights=sample_weight))


def weighted_gini(y_true: np.ndarray, y_pred: np.ndarray, sample_weight: np.ndarray | None = None) -> float:
    if sample_weight is None:
        sample_weight = np.ones_like(y_true)
    else:
        sample_weight = np.asarray(sample_weight, dtype=float)
    
    order = np.argsort(y_pred)
    y_true_sorted = y_true[order]
    w_sorted = sample_weight[order]
    
    cumulative_w = np.cumsum(w_sorted)
    if cumulative_w[-1] == 0:
        return 0.0
    cumulative_y = np.cumsum(y_true_sorted * w_sorted)
    
    if cumulative_y[-1] == 0:
        return 0.0
        
    lorenz = cumulative_y / cumulative_y[-1]
    
    # Simple trapezoidal integration for Gini coefficient
    gini_val = float(np.sum(w_sorted * (lorenz + np.roll(lorenz, 1, axis=0))) / cumulative_w[-1] - 1.0)
    
    # Perfect Gini
    perfect_order = np.argsort(y_true)
    y_true_perf = y_true[perfect_order]
    w_perf = sample_weight[perfect_order]
    cumulative_w_perf = np.cumsum(w_perf)
    cumulative_y_perf = np.cumsum(y_true_perf * w_perf)
    lorenz_perf = cumulative_y_perf / cumulative_y_perf[-1]
    perfect_gini_val = float(np.sum(w_perf * (lorenz_perf + np.roll(lorenz_perf, 1, axis=0))) / cumulative_w_perf[-1] - 1.0)
    
    if perfect_gini_val == 0:
        return 0.0
    return float(gini_val / perfect_gini_val)


def pearson_correlation(y_true: np.ndarray, y_pred: np.ndarray) -> float:
    if len(y_true) < 2:
        return 0.0
    std_true = np.std(y_true)
    std_pred = np.std(y_pred)
    if std_true == 0 or std_pred == 0:
        return 0.0
    corr = np.corrcoef(y_true, y_pred)[0, 1]
    return float(corr) if not np.isnan(corr) else 0.0


def evaluate_severity(y_true: pd.Series, y_pred: np.ndarray, sample_weight: np.ndarray | None = None) -> dict[str, float]:
    y_true = np.asarray(y_true, dtype=float)
    clipped = np.maximum(np.asarray(y_pred, dtype=float), 0.0)
    return {
        "MAE": weighted_mean_absolute_error(y_true, clipped, sample_weight),
        "GammaDeviance": mean_gamma_deviance(y_true, clipped, sample_weight),
        "PearsonCorrelation": pearson_correlation(y_true, clipped),
    }


def cross_validate_severity_model(
    pipeline_class: Any,
    df_train: pd.DataFrame,
    sklearn: dict[str, Any],
    seed: int,
) -> dict[str, float]:
    KFold = sklearn["KFold"]

    if len(df_train) > 15000:
        df_cv = df_train.sample(n=15000, random_state=seed).copy()
    else:
        df_cv = df_train.copy()

    cv = KFold(n_splits=3, shuffle=True, random_state=seed)
    mae_scores = []
    dev_scores = []
    corr_scores = []

    for train_idx, val_idx in cv.split(df_cv):
        df_cv_train = df_cv.iloc[train_idx]
        df_cv_val = df_cv.iloc[val_idx]

        preprocessor = build_preprocessor(sklearn)

        if pipeline_class == GammaGLMPipeline:
            fold_model = GammaGLMPipeline(preprocessor)
        else:
            fold_model = XGBoostLognormalPipeline(preprocessor, random_state=seed)

        fold_model.fit(df_cv_train[MODEL_FEATURES], df_cv_train[TARGET_COLUMN])
        preds = fold_model.predict(df_cv_val[MODEL_FEATURES])

        evals = evaluate_severity(df_cv_val[TARGET_COLUMN], preds)
        mae_scores.append(evals["MAE"])
        dev_scores.append(evals["GammaDeviance"])
        corr_scores.append(evals["PearsonCorrelation"])

    return {
        "CV_MAE": float(np.mean(mae_scores)),
        "CV_GammaDeviance": float(np.mean(dev_scores)),
        "CV_PearsonCorrelation": float(np.mean(corr_scores)),
    }


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
        "# Severity Claim Model Training Report",
        "",
        "## Purpose",
        "",
        "- Predict average claim severity for positive claims.",
        "- Target is `average_claim_severity`.",
        "- Models compared: Gamma GLM vs. XGBoost Lognormal.",
        "",
        "## Features",
        "",
        f"- Numeric features: `{', '.join(NUMERIC_FEATURES)}`",
        f"- Categorical features: `{', '.join(CATEGORICAL_FEATURES)}`",
        "",
        "## Selected Model",
        "",
        f"- Model version: `{metadata['modelVersion']}`",
        f"- Selected model: `{selected}`",
        f"- Holdout MAE: `{selected_metrics['MAE']:.4f}`",
        f"- CV MAE: `{selected_metrics['CV_MAE']:.4f}`",
        f"- Holdout Gamma Deviance: `{selected_metrics['GammaDeviance']:.4f}`",
        f"- CV Gamma Deviance: `{selected_metrics['CV_GammaDeviance']:.4f}`",
        f"- Holdout Pearson Correlation: `{selected_metrics['PearsonCorrelation']:.4f}`",
        f"- CV Pearson Correlation: `{selected_metrics['CV_PearsonCorrelation']:.4f}`",
        "",
        "## Model Comparison",
        "",
        markdown_table(comparison.round(4)),
        "",
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
) -> TrainingResult:
    try:
        import mlflow
        import mlflow.sklearn
        from mlflow.models import infer_signature
        from mlflow.tracking import MlflowClient
    except ModuleNotFoundError as exc:
        raise SystemExit(
            "MLflow logging requested but mlflow is not installed."
        ) from exc

    mlflow.set_tracking_uri(args.mlflow_tracking_uri)
    mlflow.set_experiment(args.mlflow_experiment)
    client = MlflowClient()
    selected_model = metadata["selectedModel"]
    selected_metrics = metadata["modelMetrics"][selected_model]
    selected_version = None
    selected_run_id = None

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
                "model_role": "severity",
                "dataset_role": metadata["datasetRole"],
                "target": metadata["targetColumn"],
                "selected_model": selected_model,
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
            }
        )
        mlflow.log_metrics({key: float(value) for key, value in selected_metrics.items()})
        mlflow.log_artifact(str(metadata_path), artifact_path="metadata")
        mlflow.log_artifact(str(comparison_path), artifact_path="reports")
        mlflow.log_artifact(str(report_path), artifact_path="reports")

        for candidate_model, pipeline in fitted_pipelines.items():
            candidate_metrics = metadata["modelMetrics"][candidate_model]
            is_best = candidate_model == selected_model
            with mlflow.start_run(
                run_name=f"{metadata['modelVersion']}-{candidate_model}",
                nested=True,
            ):
                mlflow.set_tags(
                    {
                        "model_role": "severity",
                        "dataset_role": metadata["datasetRole"],
                        "target": metadata["targetColumn"],
                        "candidate_model": candidate_model,
                        "selected_model": selected_model,
                        "is_best": str(is_best).lower(),
                        "stage": "Candidate" if is_best else "ExperimentOnly",
                        "deployment_status": "CANDIDATE" if is_best else "EXPERIMENT_ONLY",
                        "training_job_id": str(args.training_job_id or ""),
                        "dataset_version": str(args.dataset_version),
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
                    }
                )
                mlflow.log_metrics({key: float(value) for key, value in candidate_metrics.items()})
                input_example = metadata["inputExample"]
                input_frame = pd.DataFrame([input_example])[metadata["featureList"]]
                try:
                    signature = infer_signature(input_frame, pipeline.predict(input_frame))
                except Exception:
                    signature = None
                result = mlflow.sklearn.log_model(
                    sk_model=pipeline,
                    name="model",
                    registered_model_name=args.mlflow_registered_model_name,
                    serialization_format=mlflow.sklearn.SERIALIZATION_FORMAT_CLOUDPICKLE,
                    signature=signature,
                    input_example=input_frame,
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
                        "Candidate" if is_best else "ExperimentOnly",
                    )
                    client.set_model_version_tag(
                        args.mlflow_registered_model_name,
                        version,
                        "deployment_status",
                        "CANDIDATE" if is_best else "EXPERIMENT_ONLY",
                    )
                    client.set_model_version_tag(
                        args.mlflow_registered_model_name,
                        version,
                        "training_job_id",
                        str(args.training_job_id or ""),
                    )
                    client.set_model_version_tag(
                        args.mlflow_registered_model_name,
                        version,
                        "dataset_version",
                        str(args.dataset_version),
                    )
                    if is_best and args.deployment_mode == "legacy-production":
                        client.set_registered_model_alias(
                            args.mlflow_registered_model_name,
                            "Production",
                            version,
                        )
                    if is_best:
                        selected_version = str(version)
                        selected_run_id = child_run_id
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

    return TrainingResult(
        mlflowRunId=selected_run_id,
        registeredModelName=args.mlflow_registered_model_name,
        registeredModelVersion=selected_version,
        selectedAlgorithm=selected_model,
        metrics={key: float(value) for key, value in selected_metrics.items()},
        datasetVersion=str(args.dataset_version),
    )


def first_input_example(df: pd.DataFrame, feature_list: list[str]) -> dict[str, Any]:
    row = df[feature_list].iloc[0].to_dict()
    cleaned = {}
    for key, value in row.items():
        if key == "gender":
            continue
        if pd.isna(value):
            cleaned[key] = None
        elif isinstance(value, np.generic):
            cleaned[key] = value.item()
        else:
            cleaned[key] = value
    return cleaned


def main() -> int:
    args = parse_args()
    sklearn = import_sklearn()
    artifacts_dir = Path(args.artifacts_dir)
    reports_dir = Path(args.reports_dir)
    artifacts_dir.mkdir(parents=True, exist_ok=True)
    reports_dir.mkdir(parents=True, exist_ok=True)

    raw_df = load_severity_frame(Path(args.input))
    df = clean_training_frame(raw_df)
    if args.max_rows and len(df) > args.max_rows:
        df = df.sample(n=args.max_rows, random_state=args.seed).copy()

    train_test_split = sklearn["train_test_split"]
    dump = sklearn["dump"]

    df_train, df_test = train_test_split(
        df,
        test_size=args.test_size,
        random_state=args.seed,
    )

    metrics: dict[str, dict[str, float]] = {}
    fitted_pipelines: dict[str, Any] = {}
    candidates = {}

    # Candidate 1: Gamma GLM
    print("Fitting Candidate 1: Gamma GLM...")
    preprocessor_1 = build_preprocessor(sklearn)
    model_1 = GammaGLMPipeline(preprocessor_1)
    model_1.fit(df_train[MODEL_FEATURES], df_train[TARGET_COLUMN])
    candidates["gamma_glm"] = model_1
    metrics["gamma_glm"] = cross_validate_severity_model(
        GammaGLMPipeline, df_train, sklearn, args.seed
    )

    # Candidate 2: XGBoost Severity
    if not args.skip_xgboost:
        print("Fitting Candidate 2: XGBoost Lognormal...")
        preprocessor_2 = build_preprocessor(sklearn)
        model_2 = XGBoostLognormalPipeline(preprocessor_2, random_state=args.seed)
        model_2.fit(df_train[MODEL_FEATURES], df_train[TARGET_COLUMN])
        candidates["xgboost_lognormal"] = model_2
        metrics["xgboost_lognormal"] = cross_validate_severity_model(
            XGBoostLognormalPipeline, df_train, sklearn, args.seed
        )

    # Evaluate holdout metrics
    for name, model in candidates.items():
        predictions = model.predict(df_test[MODEL_FEATURES])
        holdout_metrics = evaluate_severity(df_test[TARGET_COLUMN], predictions)
        metrics[name].update(holdout_metrics)
        fitted_pipelines[name] = model

    # Select best model based on CV Gamma Deviance (lower is better)
    selected_model = min(metrics, key=lambda model_name: metrics[model_name]["CV_GammaDeviance"])
    best_pipeline = fitted_pipelines[selected_model]

    comparison = pd.DataFrame(metrics).T.reset_index().rename(columns={"index": "model"})
    comparison = comparison.sort_values(["CV_GammaDeviance", "GammaDeviance"]).reset_index(drop=True)

    training_date = datetime.now(timezone.utc).replace(microsecond=0).isoformat()
    model_version = f"severity-v1-{selected_model}"

    # Calculate defaults
    defaults = {}
    for column in NUMERIC_FEATURES:
        value = df[column].median()
        defaults[column] = None if pd.isna(value) else float(value)
    for column in CATEGORICAL_FEATURES:
        mode = df[column].mode(dropna=True)
        defaults[column] = str(mode.iloc[0]) if not mode.empty else ""

    feature_list = list(MODEL_FEATURES)

    metadata = {
        "modelVersion": model_version,
        "modelPurpose": "severity prediction (average claim severity)",
        "datasetRole": "severity training dataset",
        "selectedModel": selected_model,
        "trainingDate": training_date,
        "inputDataset": args.input,
        "datasetVersion": args.dataset_version,
        "trainingJobId": args.training_job_id,
        "rowCount": int(len(df)),
        "targetColumn": TARGET_COLUMN,
        "featureList": feature_list,
        "numericFeatures": NUMERIC_FEATURES,
        "categoricalFeatures": CATEGORICAL_FEATURES,
        "featureDefaults": defaults,
        "inputExample": first_input_example(df, feature_list),
        "modelMetrics": metrics,
        "evaluationDesign": {
            "testSplit": "80/20",
            "testSize": args.test_size,
            "randomState": args.seed,
            "crossValidation": "3-fold KFold on a deterministic training sample up to 15,000 rows",
            "selectionMetric": "lowest CV Gamma Deviance",
        },
        "pythonVersion": platform.python_version(),
    }

    model_path = artifacts_dir / "severity_model.joblib"
    metadata_path = artifacts_dir / "severity_metadata.json"
    comparison_path = reports_dir / "severity_comparison.csv"
    report_path = reports_dir / "severity_model_report.md"

    dump(best_pipeline, model_path)
    write_json(metadata_path, metadata)
    comparison.to_csv(comparison_path, index=False)
    write_report(report_path, metadata, comparison)

    if args.mlflow:
        result = log_to_mlflow(
            args=args,
            fitted_pipelines=fitted_pipelines,
            metadata=metadata,
            comparison_path=comparison_path,
            report_path=report_path,
            metadata_path=metadata_path,
        )
    else:
        result = TrainingResult(
            mlflowRunId=None,
            registeredModelName=args.mlflow_registered_model_name,
            registeredModelVersion=None,
            selectedAlgorithm=selected_model,
            metrics={key: float(value) for key, value in metrics[selected_model].items()},
            datasetVersion=str(args.dataset_version),
        )

    print(f"Selected model: {selected_model}")
    print(json.dumps(metrics[selected_model], indent=2))
    print(f"Wrote {model_path}")
    print("TRAINING_RESULT_JSON=" + json.dumps(asdict(result), ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
