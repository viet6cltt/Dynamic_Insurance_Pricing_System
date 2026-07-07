"""Unified CLI for Frequency and Severity training."""

from __future__ import annotations

import argparse
import sys


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model-type", choices=["FREQUENCY", "SEVERITY"], required=True)
    parser.add_argument("--training-job-id", required=True)
    parser.add_argument("--dataset-version", default="generated-current")
    parser.add_argument("--mlflow-tracking-uri", default=None)
    parser.add_argument("--skip-xgboost", action="store_true")
    parser.add_argument("--max-rows", type=int, default=None)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    forwarded = [
        "--mlflow",
        "--training-job-id",
        args.training_job_id,
        "--dataset-version",
        args.dataset_version,
        "--deployment-mode",
        "candidate",
    ]
    if args.mlflow_tracking_uri:
        forwarded.extend(["--mlflow-tracking-uri", args.mlflow_tracking_uri])
    if args.skip_xgboost:
        forwarded.append("--skip-xgboost")
    if args.max_rows is not None:
        forwarded.extend(["--max-rows", str(args.max_rows)])

    if args.model_type == "FREQUENCY":
        from ml.scripts import train_frequency_model

        sys.argv = ["train_frequency_model.py", *forwarded]
        return train_frequency_model.main()

    from ml.scripts import train_severity_model

    sys.argv = ["train_severity_model.py", *forwarded]
    return train_severity_model.main()


if __name__ == "__main__":
    raise SystemExit(main())
