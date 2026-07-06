"""Database-polling model trainer worker."""

from __future__ import annotations

import json
import os
import subprocess
import sys
import time
from pathlib import Path

from sqlalchemy import select, update

from .config import dataset_version, mlflow_tracking_uri
from .db import engine, init_db, training_jobs, utcnow
from .lifecycle import update_training_job_failed, update_training_job_result


def claim_job() -> dict | None:
    with engine().begin() as conn:
        row = (
            conn.execute(
                select(training_jobs)
                .where(training_jobs.c.status == "QUEUED")
                .order_by(training_jobs.c.requested_at)
                .limit(1)
                .with_for_update(skip_locked=True)
            )
            .mappings()
            .first()
        )
        if not row:
            return None
        conn.execute(
            update(training_jobs)
            .where(training_jobs.c.id == row["id"])
            .values(status="RUNNING", current_stage="TRAINING", started_at=utcnow())
        )
        return dict(row)


def parse_training_result(output: str) -> dict:
    for line in reversed(output.splitlines()):
        if line.startswith("TRAINING_RESULT_JSON="):
            return json.loads(line.split("=", 1)[1])
    raise RuntimeError("Training process did not emit TRAINING_RESULT_JSON.")


def run_training(job: dict) -> None:
    command = [
        sys.executable,
        "-m",
        "ml.train",
        "--model-type",
        job["model_type"],
        "--training-job-id",
        str(job["id"]),
        "--dataset-version",
        job.get("dataset_version") or dataset_version(),
        "--mlflow-tracking-uri",
        mlflow_tracking_uri(),
    ]
    max_rows = os.getenv("TRAINER_MAX_ROWS")
    if max_rows:
        command.extend(["--max-rows", max_rows])
    if os.getenv("TRAINER_SKIP_XGBOOST", "false").lower() == "true":
        command.append("--skip-xgboost")

    completed = subprocess.run(
        command,
        cwd=Path(__file__).resolve().parents[1],
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        check=False,
    )
    if completed.returncode != 0:
        raise RuntimeError(completed.stdout)
    update_training_job_result(int(job["id"]), parse_training_result(completed.stdout))


def main() -> int:
    init_db()
    poll_seconds = float(os.getenv("TRAINER_POLL_SECONDS", "5"))
    while True:
        job = claim_job()
        if not job:
            time.sleep(poll_seconds)
            continue
        try:
            run_training(job)
        except Exception as exc:
            update_training_job_failed(int(job["id"]), str(exc))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
