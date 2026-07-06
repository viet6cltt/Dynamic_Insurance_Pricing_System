"""PostgreSQL persistence for model lifecycle state."""

from __future__ import annotations

from datetime import datetime, timezone

from sqlalchemy import (
    CheckConstraint,
    Column,
    DateTime,
    Float,
    Index,
    Integer,
    MetaData,
    String,
    Table,
    Text,
    UniqueConstraint,
    create_engine,
    text,
)
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.engine import Engine

from .config import database_url


metadata = MetaData()

training_jobs = Table(
    "training_jobs",
    metadata,
    Column("id", Integer, primary_key=True, autoincrement=True),
    Column("model_type", String(32), nullable=False),
    Column("status", String(32), nullable=False),
    Column("current_stage", String(64), nullable=True),
    Column("mlflow_run_id", String(128), nullable=True),
    Column("registered_model_name", String(128), nullable=True),
    Column("registered_model_version", String(32), nullable=True),
    Column("selected_algorithm", String(128), nullable=True),
    Column("metrics", JSONB, nullable=True),
    Column("dataset_version", String(128), nullable=True),
    Column("requested_by", String(128), nullable=True),
    Column("requested_at", DateTime(timezone=True), nullable=False),
    Column("started_at", DateTime(timezone=True), nullable=True),
    Column("completed_at", DateTime(timezone=True), nullable=True),
    Column("failure_reason", Text, nullable=True),
    CheckConstraint("model_type in ('FREQUENCY', 'SEVERITY')", name="ck_training_jobs_model_type"),
    CheckConstraint("status in ('QUEUED', 'RUNNING', 'COMPLETED', 'FAILED')", name="ck_training_jobs_status"),
)

model_promotion_history = Table(
    "model_promotion_history",
    metadata,
    Column("id", Integer, primary_key=True, autoincrement=True),
    Column("model_type", String(32), nullable=False),
    Column("from_version", String(32), nullable=True),
    Column("to_version", String(32), nullable=False),
    Column("action", String(32), nullable=False),
    Column("reason", Text, nullable=True),
    Column("performed_by", String(128), nullable=True),
    Column("performed_at", DateTime(timezone=True), nullable=False),
    CheckConstraint("action in ('PROMOTE', 'REJECT')", name="ck_model_promotion_history_action"),
)

pure_premium_baselines = Table(
    "pure_premium_baselines",
    metadata,
    Column("id", Integer, primary_key=True, autoincrement=True),
    Column("type_product", String(128), nullable=False),
    Column("frequency_model_version", String(32), nullable=False),
    Column("severity_model_version", String(32), nullable=False),
    Column("dataset_version", String(128), nullable=False),
    Column("baseline_value", Float, nullable=False),
    Column("record_count", Integer, nullable=False),
    Column("total_exposure", Float, nullable=False),
    Column("status", String(32), nullable=False),
    Column("calculated_at", DateTime(timezone=True), nullable=False),
    UniqueConstraint(
        "type_product",
        "frequency_model_version",
        "severity_model_version",
        "dataset_version",
        name="uq_pure_premium_baseline_version",
    ),
    CheckConstraint("status in ('ACTIVE', 'INACTIVE')", name="ck_pure_premium_baselines_status"),
)

Index(
    "ix_training_jobs_one_active_per_model_type",
    training_jobs.c.model_type,
    unique=True,
    postgresql_where=training_jobs.c.status.in_(["QUEUED", "RUNNING"]),
)
Index(
    "ix_pure_premium_baselines_active_lookup",
    pure_premium_baselines.c.type_product,
    pure_premium_baselines.c.frequency_model_version,
    pure_premium_baselines.c.severity_model_version,
    postgresql_where=pure_premium_baselines.c.status == "ACTIVE",
)

_engine: Engine | None = None


def utcnow() -> datetime:
    return datetime.now(timezone.utc)


def engine() -> Engine:
    global _engine
    if _engine is None:
        _engine = create_engine(database_url(), pool_pre_ping=True, future=True)
    return _engine


def init_db() -> None:
    metadata.create_all(engine())


def healthcheck_db() -> bool:
    with engine().connect() as conn:
        conn.execute(text("select 1"))
    return True
