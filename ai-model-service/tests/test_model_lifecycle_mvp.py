from __future__ import annotations

import sys
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[2]
sys.path.append(str(ROOT))
sys.path.append(str(ROOT / "ai-model-service"))

from app.mlflow_registry import metric_difference
from app.pure_premium_runtime import LoadedModel, PurePremiumRuntime
from ml.scripts import train_frequency_model, train_severity_model


class ConstantModel:
    def __init__(self, value: float) -> None:
        self.value = value

    def predict(self, frame):
        return np.full(len(frame), self.value)


def test_frequency_and_severity_training_features_exclude_gender() -> None:
    assert "gender" not in train_frequency_model.MODEL_FEATURES
    assert "gender" not in train_frequency_model.NUMERIC_FEATURES
    assert "gender" not in train_frequency_model.CATEGORICAL_FEATURES
    assert "gender" not in train_severity_model.MODEL_FEATURES
    assert "gender" not in train_severity_model.NUMERIC_FEATURES
    assert "gender" not in train_severity_model.CATEGORICAL_FEATURES


def test_metric_difference_uses_metric_direction() -> None:
    diff = metric_difference(
        {"PoissonDeviance": 10.0, "NormalizedGini": 0.20},
        {"PoissonDeviance": 8.0, "NormalizedGini": 0.25},
    )

    assert diff["PoissonDeviance"]["improved"] is True
    assert diff["NormalizedGini"]["improved"] is True


def test_pure_premium_annualizes_frequency_once() -> None:
    runtime = PurePremiumRuntime.__new__(PurePremiumRuntime)
    runtime.frequency = LoadedModel(
        ConstantModel(2.0),
        {"featureList": ["exposure_time"], "featureDefaults": {}},
        "1",
    )
    runtime.severity = LoadedModel(
        ConstantModel(100.0),
        {"featureList": ["type_product"], "featureDefaults": {"type_product": "HEALTH"}},
        "1",
    )

    values = runtime.predict_values({"exposure_time": 0.5, "type_product": "HEALTH"})

    assert values["frequencyPrediction"] == 2.0
    assert values["predictedFrequencyAnnual"] == 4.0
    assert values["predictedSeverity"] == 100.0
    assert values["predictedPurePremium"] == 400.0
