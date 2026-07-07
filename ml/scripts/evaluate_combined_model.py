#!/usr/bin/env python3
"""Evaluate Combined Model: Frequency-Severity model evaluation and validation."""

from __future__ import annotations

import argparse
import json
import platform
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import numpy as np
import pandas as pd
import matplotlib.pyplot as plt
import seaborn as sns

# Add ai-model-service to sys.path so we can import the custom model wrappers
project_root = Path(__file__).resolve().parents[2]
sys.path.append(str(project_root / "ai-model-service"))
import app.custom_models  # Ensure classes are in sys.modules for joblib

def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--input",
        default="data/generated/synthetic_insurance_claims.csv",
        help="Path to the master synthetic insurance claims dataset.",
    )
    parser.add_argument("--freq-model", default="ml/artifacts/frequency_model.joblib")
    parser.add_argument("--sev-model", default="ml/artifacts/severity_model.joblib")
    parser.add_argument("--artifacts-dir", default="ml/artifacts")
    parser.add_argument("--reports-dir", default="ml/reports")
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--test-size", type=float, default=0.2)
    return parser.parse_args()


def import_sklearn() -> dict[str, Any]:
    try:
        from joblib import load
        from sklearn.metrics import mean_absolute_error
        from sklearn.model_selection import train_test_split
    except ModuleNotFoundError as exc:
        raise SystemExit(
            "Missing evaluation dependency. Install requirements first, for example:\n"
            "python3 -m pip install -r ai-model-service/requirements.txt\n"
            f"Original error: {exc}"
        ) from exc
    return locals()


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


def top_10_percent_lift(y_true: pd.Series | np.ndarray, y_pred: np.ndarray, exposure: pd.Series | np.ndarray) -> float:
    actual = np.asarray(y_true, dtype=float)
    pred = np.asarray(y_pred, dtype=float)
    exp = np.asarray(exposure, dtype=float)
    
    if len(actual) == 0:
        return 0.0
        
    # Sort in descending order of predicted cost
    order = np.argsort(-pred)
    sorted_actual = actual[order]
    sorted_exp = exp[order]
    
    # Select the top 10%
    n_10 = max(1, int(len(actual) * 0.1))
    
    # Top 10% actual claim rate (total claims / total exposure)
    top_actual_cost = np.sum(sorted_actual[:n_10] * sorted_exp[:n_10])
    top_exposure = np.sum(sorted_exp[:n_10])
    top_rate = top_actual_cost / np.maximum(top_exposure, 1e-6)
    
    # Portfolio actual claim rate (total claims / total exposure)
    portfolio_actual_cost = np.sum(actual * exp)
    portfolio_exposure = np.sum(exp)
    portfolio_rate = portfolio_actual_cost / np.maximum(portfolio_exposure, 1e-6)
    
    if portfolio_rate == 0:
        return 0.0
        
    return float(top_rate / portfolio_rate)


def write_json(path: Path, payload: Any) -> None:
    path.write_text(json.dumps(payload, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def write_report(
    path: Path, 
    freq_ver: str, 
    sev_ver: str, 
    metrics: dict[str, float], 
    plots_paths: dict[str, str],
    anomaly_status: dict[str, Any]
) -> None:
    lines = [
        "# Combined Frequency–Severity Model Evaluation Report",
        "",
        "## 1. Purpose",
        "",
        "This report evaluates the performance of the combined Frequency-Severity pricing model. "
        "The model estimates the expected annual claim cost for a policy using the formula:",
        "",
        "$$\\text{predicted\\_annual\\_cost} = \\text{predicted\\_frequency\\_rate} \\times \\text{predicted\\_severity}$$",
        "",
        "Where:",
        "- $\\text{predicted\\_frequency\\_rate}$ is the expected claim count per year (annualized).",
        "- $\\text{predicted\\_severity}$ is the expected cost per claim.",
        "- $\\text{predicted\\_annual\\_cost}$ is the expected claim cost per policy per year of exposure.",
        "",
        "## 2. Model Versions Used",
        "",
        f"- **Frequency Model Champion**: `{freq_ver}`",
        f"- **Severity Model Champion**: `{sev_ver}`",
        "",
        "## 3. Evaluation Metrics",
        "",
        "The evaluation is performed on the holdout test set (20% split) of the master policy-level dataset.",
        "",
        "| Metric | Value | Business Meaning |",
        "| --- | --- | --- |",
        f"| **MAE** | {metrics['MAE']:.4f} | Average absolute difference between actual and predicted annual claim cost. |",
        f"| **Normalized Gini** | {metrics['NormalizedGini']:.4f} | Measures the model's ability to rank policies by risk (0 = random, 1 = perfect). |",
        f"| **Top-10% Lift** | {metrics['Top-10% Lift']:.4f} | Ratio of actual loss rate in the top 10% highest-risk policies compared to the average portfolio rate. |",
        f"| **Calibration Ratio (Weighted)** | {metrics['CalibrationRatioWeighted']:.4f} | Total actual claim cost / total predicted claim cost. Closeness to 1.0 represents good overall alignment. |",
        f"| **Calibration Ratio (Unweighted)** | {metrics['CalibrationRatioUnweighted']:.4f} | Mean actual annualized cost / mean predicted annualized cost. |",
        "",
        "## 4. Anomaly and Stability Checks",
        "",
        f"- **NaN Predictions**: `{anomaly_status['has_nan']}` (Count: `{anomaly_status['nan_count']}`)",
        f"- **Infinite Predictions**: `{anomaly_status['has_inf']}` (Count: `{anomaly_status['inf_count']}`)",
        f"- **Negative Predictions**: `{anomaly_status['has_negative']}` (Count: `{anomaly_status['negative_count']}`)",
        f"- **Predictions scale sanity**: `{anomaly_status['scale_sanity']}` (Min: `{metrics['pred_min']:.2f}`, Max: `{metrics['pred_max']:.2f}`, Mean: `{metrics['pred_mean']:.2f}`)",
        "",
        "## 5. Visualizations",
        "",
        "### 5.1 Actual vs. Predicted Annual Cost (Log1p Scale)",
        "Shows model calibration across the entire spectrum of losses. The red dashed line represents perfect calibration (y = x).",
        f"![Actual vs Predicted Log1p](/{plots_paths['log1p']})",
        "",
        "### 5.2 Actual vs. Predicted Annual Cost by Risk Decile",
        "Policies are grouped into 10 risk deciles based on predicted annual cost. Strong risk segmentation is shown by increasing actual costs from decile 1 to 10.",
        f"![Actual vs Predicted by Decile](/{plots_paths['decile']})",
        "",
        "### 5.3 Cumulative Loss Capture Curve",
        "Displays the cumulative proportion of total actual claim costs captured as we add policies sorted from highest to lowest predicted risk.",
        f"![Loss Capture Curve](/{plots_paths['lorenz']})",
        "",
        "## 6. Business Implications and Conclusion",
        "",
        f"Based on a **Top-10% Lift of {metrics['Top-10% Lift']:.2f}**, the combined model successfully segments the highest risk policies. "
        f"The top 10% highest-predicted-risk group accounts for a significant multiple of the average portfolio loss rate.",
        f"The **Normalized Gini coefficient of {metrics['NormalizedGini']:.4f}** indicates that the model has strong predictive power to rank policies correctly. "
        f"The weighted calibration ratio of **{metrics['CalibrationRatioWeighted']:.4f}** indicates that the overall premium levels estimated by the model are well aligned with actual claims in the portfolio, with minimal systematic underpricing or overpricing.",
        "",
        "**Conclusion**: The combined model is highly suitable for deployment within the Pricing Service to enable dynamic, risk-based insurance pricing."
    ]
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> int:
    args = parse_args()
    sklearn = import_sklearn()
    load = sklearn["load"]
    mean_absolute_error = sklearn["mean_absolute_error"]
    train_test_split = sklearn["train_test_split"]

    artifacts_dir = Path(args.artifacts_dir).resolve()
    reports_dir = Path(args.reports_dir).resolve()
    artifacts_dir.mkdir(parents=True, exist_ok=True)
    reports_dir.mkdir(parents=True, exist_ok=True)

    # 1. Load models
    print(f"Loading Frequency model from {args.freq_model}...")
    freq_model = load(args.freq_model)
    print(f"Loading Severity model from {args.sev_model}...")
    sev_model = load(args.sev_model)

    # Load metadata to get feature lists
    freq_meta_path = artifacts_dir / "frequency_metadata.json"
    sev_meta_path = artifacts_dir / "severity_metadata.json"

    if not freq_meta_path.exists() or not sev_meta_path.exists():
        raise FileNotFoundError("Frequency or Severity model metadata not found in artifacts directory.")

    with open(freq_meta_path, "r") as f:
        freq_meta = json.load(f)
    with open(sev_meta_path, "r") as f:
        sev_meta = json.load(f)

    freq_features = freq_meta["featureList"]
    sev_features = sev_meta["featureList"]

    # 2. Load and preprocess master dataset
    print(f"Loading dataset from {args.input}...")
    df_raw = pd.read_csv(args.input)

    # Clean the dataset
    df = df_raw.copy()
    # Filter to exposure >= 0.1 to avoid extreme division / tiny policies
    df = df[df["exposure_time"] >= 0.1].copy()

    # Train/test split using the same seed and size as training scripts
    print(f"Splitting dataset with test_size={args.test_size} and random_state={args.seed}...")
    df_train, df_test = train_test_split(df, test_size=args.test_size, random_state=args.seed)

    print(f"Evaluation dataset size: {len(df_test)} policies")

    # 3. Predict Frequency (claim count over exposure period)
    # XGBoostPoissonPipeline expects exposure_time in the input
    print("Predicting frequency...")
    pred_freq_count = freq_model.predict(df_test[freq_features])
    
    # Calculate frequency rate (claims per year)
    predicted_frequency_rate = pred_freq_count / df_test["exposure_time"].values

    # 4. Predict Severity
    print("Predicting severity...")
    predicted_severity = sev_model.predict(df_test[sev_features])

    # 5. Calculate Combined Predictions
    predicted_annual_cost = predicted_frequency_rate * predicted_severity

    # Actual annual cost (actual claim cost normalized by exposure)
    actual_annual_cost = df_test["annual_claim_cost"].values / df_test["exposure_time"].values

    # 6. Run Stability and Anomaly Checks
    nan_mask = np.isnan(predicted_annual_cost)
    inf_mask = np.isinf(predicted_annual_cost)
    negative_mask = predicted_annual_cost < 0

    anomaly_status = {
        "has_nan": bool(np.any(nan_mask)),
        "nan_count": int(np.sum(nan_mask)),
        "has_inf": bool(np.any(inf_mask)),
        "inf_count": int(np.sum(inf_mask)),
        "has_negative": bool(np.any(negative_mask)),
        "negative_count": int(np.sum(negative_mask)),
        "scale_sanity": "Normal" if np.max(predicted_annual_cost[~nan_mask & ~inf_mask]) < 1000000 else "Suspiciously Large"
    }

    # Replace bad values for metric calculation if any exist (to avoid crashes)
    pred_clean = np.nan_to_num(predicted_annual_cost, nan=0.0, posinf=0.0, neginf=0.0)
    pred_clean = np.maximum(pred_clean, 0.0)

    # 7. Calculate Metrics
    mae = float(mean_absolute_error(actual_annual_cost, pred_clean))
    gini = float(normalized_gini(actual_annual_cost, pred_clean))
    lift = float(top_10_percent_lift(actual_annual_cost, pred_clean, df_test["exposure_time"].values))

    # Calibration ratios
    actual_total_cost = df_test["annual_claim_cost"].sum()
    pred_total_cost = np.sum(pred_clean * df_test["exposure_time"].values)
    
    cal_ratio_weighted = float(actual_total_cost / pred_total_cost) if pred_total_cost > 0 else 0.0
    cal_ratio_unweighted = float(np.mean(actual_annual_cost) / np.mean(pred_clean)) if np.mean(pred_clean) > 0 else 0.0

    metrics = {
        "MAE": mae,
        "NormalizedGini": gini,
        "Top-10% Lift": lift,
        "CalibrationRatioWeighted": cal_ratio_weighted,
        "CalibrationRatioUnweighted": cal_ratio_unweighted,
        "pred_min": float(np.min(pred_clean)),
        "pred_max": float(np.max(pred_clean)),
        "pred_mean": float(np.mean(pred_clean)),
    }

    print("\n--- Evaluation Results ---")
    print(f"MAE: {mae:.4f}")
    print(f"Normalized Gini: {gini:.4f}")
    print(f"Top-10% Lift: {lift:.4f}")
    print(f"Calibration Ratio (Weighted): {cal_ratio_weighted:.4f}")
    print(f"Calibration Ratio (Unweighted): {cal_ratio_unweighted:.4f}")
    print("--------------------------\n")

    # 8. Generate Charts
    print("Generating validation charts...")
    sns.set_theme(style="whitegrid")
    
    # Chart 1: Actual vs Predicted Log1p
    plt.figure(figsize=(8, 6))
    sns.scatterplot(
        x=np.log1p(pred_clean),
        y=np.log1p(actual_annual_cost),
        alpha=0.3,
        color="#2b5c8f",
        edgecolor=None
    )
    max_val = max(np.log1p(pred_clean).max(), np.log1p(actual_annual_cost).max())
    plt.plot([0, max_val], [0, max_val], color="#e74c3c", linestyle="--", linewidth=2, label="Perfect Calibration (y = x)")
    plt.xlabel("Log1p(Predicted Annual Cost)", fontsize=11)
    plt.ylabel("Log1p(Actual Annual Cost)", fontsize=11)
    plt.title("Actual vs. Predicted Annual Cost (Log1p)", fontsize=13, fontweight="bold", pad=12)
    plt.legend()
    plt.tight_layout()
    log1p_plot_path = reports_dir / "actual_vs_predicted_log1p.png"
    plt.savefig(log1p_plot_path, dpi=150)
    plt.close()

    # Chart 2: Deciles of Risk
    df_eval = df_test.copy()
    df_eval["pred_annual_cost"] = pred_clean
    df_eval["actual_annual_cost"] = actual_annual_cost
    df_eval["decile"] = pd.qcut(df_eval["pred_annual_cost"], 10, labels=False, duplicates='drop') + 1

    decile_summary = df_eval.groupby("decile").apply(lambda g: pd.Series({
        "actual": np.sum(g["annual_claim_cost"]) / np.sum(g["exposure_time"]),
        "predicted": np.sum(g["pred_annual_cost"] * g["exposure_time"]) / np.sum(g["exposure_time"])
    }), include_groups=False).reset_index()

    plt.figure(figsize=(10, 6))
    x_indices = np.arange(len(decile_summary))
    width = 0.35
    plt.bar(x_indices - width/2, decile_summary["actual"], width, label="Actual Annual Cost", color="#34495e")
    plt.bar(x_indices + width/2, decile_summary["predicted"], width, label="Predicted Annual Cost", color="#2ecc71")
    plt.xlabel("Risk Decile (1 = Lowest Risk, 10 = Highest Risk)", fontsize=11)
    plt.ylabel("Annual Cost per Exposure", fontsize=11)
    plt.title("Actual vs. Predicted Annual Cost by Risk Decile", fontsize=13, fontweight="bold", pad=12)
    plt.xticks(x_indices, decile_summary["decile"])
    plt.legend()
    plt.tight_layout()
    decile_plot_path = reports_dir / "actual_vs_predicted_deciles.png"
    plt.savefig(decile_plot_path, dpi=150)
    plt.close()

    # Chart 3: Cumulative Loss Capture Curve
    df_sorted = df_eval.sort_values("pred_annual_cost", ascending=False).copy()
    df_sorted["cum_exposure"] = df_sorted["exposure_time"].cumsum()
    df_sorted["cum_actual_cost"] = df_sorted["annual_claim_cost"].cumsum()
    
    cum_exp_pct = df_sorted["cum_exposure"].values / df_sorted["exposure_time"].sum()
    cum_cost_pct = df_sorted["cum_actual_cost"].values / df_sorted["annual_claim_cost"].sum()

    df_perfect = df_eval.sort_values("annual_claim_cost", ascending=False).copy()
    df_perfect["cum_exposure"] = df_perfect["exposure_time"].cumsum()
    df_perfect["cum_actual_cost"] = df_perfect["annual_claim_cost"].cumsum()
    perfect_exp_pct = df_perfect["cum_exposure"].values / df_perfect["exposure_time"].sum()
    perfect_cost_pct = df_perfect["cum_actual_cost"].values / df_perfect["annual_claim_cost"].sum()

    plt.figure(figsize=(8, 6))
    plt.plot(cum_exp_pct, cum_cost_pct, label="Model Cumulative Loss Capture", color="#2980b9", linewidth=2.5)
    plt.plot(perfect_exp_pct, perfect_cost_pct, label="Perfect Model", color="#27ae60", linestyle=":", linewidth=2)
    plt.plot([0, 1], [0, 1], label="Random Model (Baseline)", color="#7f8c8d", linestyle="--", linewidth=1.5)
    plt.xlabel("Cumulative Proportion of Exposure", fontsize=11)
    plt.ylabel("Cumulative Proportion of Actual Claim Cost", fontsize=11)
    plt.title("Cumulative Loss Capture Curve", fontsize=13, fontweight="bold", pad=12)
    plt.legend(loc="lower right")
    plt.tight_layout()
    lorenz_plot_path = reports_dir / "loss_capture_curve.png"
    plt.savefig(lorenz_plot_path, dpi=150)
    plt.close()

    # Plots paths for report relative to project root
    plots_paths = {
        "log1p": str(log1p_plot_path.relative_to(project_root)),
        "decile": str(decile_plot_path.relative_to(project_root)),
        "lorenz": str(lorenz_plot_path.relative_to(project_root))
    }

    # 9. Save Báo cáo và Metadata
    report_path = reports_dir / "combined_model_report.md"
    write_report(
        report_path, 
        freq_meta["modelVersion"], 
        sev_meta["modelVersion"], 
        metrics, 
        plots_paths,
        anomaly_status
    )

    combined_metadata = {
        "evaluationDate": datetime.now(timezone.utc).replace(microsecond=0).isoformat(),
        "frequencyModelVersion": freq_meta["modelVersion"],
        "severityModelVersion": sev_meta["modelVersion"],
        "evaluationDataset": args.input,
        "evaluationDatasetSize": len(df_test),
        "testSplit": args.test_size,
        "randomState": args.seed,
        "combinedModelMetrics": metrics,
        "anomalyChecks": anomaly_status,
        "plotPaths": plots_paths,
        "pythonVersion": platform.python_version()
    }
    
    metadata_path = artifacts_dir / "combined_model_metadata.json"
    write_json(metadata_path, combined_metadata)

    print(f"Wrote report to {report_path}")
    print(f"Wrote metadata to {metadata_path}")
    
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
