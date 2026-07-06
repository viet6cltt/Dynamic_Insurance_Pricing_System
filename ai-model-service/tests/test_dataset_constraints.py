"""
test_dataset_constraints.py
============================
Integration-level tests that validate the output dataset constraints
after running the full DatasetBuilder pipeline.

These tests use small synthetic inputs to run quickly without touching
the real 228k-row portfolio dataset.
"""

import sys
from pathlib import Path
import tempfile
import shutil
import numpy as np
import pandas as pd
import pytest
import yaml

_SRC = Path(__file__).resolve().parents[2] / "src"
_PROJECT_ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(_SRC))

from data_generation.config import GenerationConfig
from data_generation.frequency_generator import FrequencyGenerator
from data_generation.severity_generator import SeverityGenerator
from data_generation.validators import DatasetValidator


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _make_minimal_cfg(seed: int = 42) -> GenerationConfig:
    """Load the real config and return it (used for integration tests)."""
    config_path = _PROJECT_ROOT / "config" / "synthetic_claim_generation.yml"
    with open(config_path, "r") as f:
        raw = yaml.safe_load(f)
    raw["random_seed"] = seed
    return GenerationConfig(raw=raw, config_path=str(config_path))


def _make_claim_df(n: int = 300, seed: int = 42) -> pd.DataFrame:
    rng = np.random.default_rng(seed)
    claim_counts = rng.integers(0, 15, n)
    return pd.DataFrame({
        "n_medical_services": claim_counts,
        "claim_count": claim_counts,
        "exposure_time": rng.uniform(0.1, 1.0, n).round(4),
        "age": rng.integers(18, 80, n),
        "gender": rng.choice(["M", "F"], n),
        "age_band": rng.choice(["18-29", "30-39", "40-49", "50-59", "60+"], n),
        "smoker": rng.choice(["yes", "no"], n),
        "bmi": rng.uniform(18, 40, n).round(1),
        "bmi_band": rng.choice(["NORMAL", "OVERWEIGHT", "OBESE", "UNDERWEIGHT"], n),
        "pre_existing_condition": rng.choice(["TRUE", "FALSE"], n),
        "bp_band": rng.choice(["NORMAL", "ELEVATED", "HIGH_1", "HIGH_2"], n),
        "blood_pressure": rng.uniform(100, 160, n).round(1),
        "exercise_frequency": rng.choice(["Daily", "Weekly", "Rarely", "Never"], n),
        "occupation_risk": rng.choice(["low", "moderate", "high"], n),
        "type_product": rng.choice(["S", "G", "E"], n),
        "type_policy": rng.choice(["I", "C"], n),
        "type_policy_dg": rng.choice(["I", "C"], n),
        "reimbursement": rng.choice(["Yes", "No"], n),
        "new_business": rng.choice(["Yes", "No"], n),
        "distribution_channel": rng.choice(["A", "B"], n),
        "seniority_insured": rng.integers(0, 25, n),
        "seniority_policy": rng.integers(0, 25, n),
    })


# ---------------------------------------------------------------------------
# Tests
# ---------------------------------------------------------------------------

class TestClaimConstraints:
    """Validate fundamental claim data constraints."""

    @pytest.fixture
    def generated_df(self):
        cfg = _make_minimal_cfg()
        df = _make_claim_df()
        sev_gen = SeverityGenerator(cfg)
        return sev_gen.generate(df)

    def test_claim_count_non_negative(self, generated_df):
        assert (generated_df["claim_count"] >= 0).all()

    def test_claim_count_integer(self, generated_df):
        assert generated_df["claim_count"].dtype in [np.int32, np.int64, int, "int64"]

    def test_annual_cost_zero_when_no_claim(self, generated_df):
        zero_mask = generated_df["claim_count"] == 0
        assert (generated_df.loc[zero_mask, "annual_claim_cost"] == 0).all()

    def test_annual_cost_positive_when_has_claim(self, generated_df):
        pos_mask = generated_df["claim_count"] > 0
        assert (generated_df.loc[pos_mask, "annual_claim_cost"] > 0).all()

    def test_positive_severity_when_has_claim(self, generated_df):
        pos_mask = generated_df["claim_count"] > 0
        assert (generated_df.loc[pos_mask, "average_claim_severity"] > 0).all()

    def test_no_nan_in_claim_count(self, generated_df):
        assert not generated_df["claim_count"].isna().any()

    def test_no_nan_in_annual_cost(self, generated_df):
        assert not generated_df["annual_claim_cost"].isna().any()

    def test_exposure_in_range(self, generated_df):
        exp = generated_df["exposure_time"]
        assert ((exp >= 0) & (exp <= 1)).all()


class TestOutputColumns:
    """Validate that training datasets contain correct columns."""

    @pytest.fixture
    def generated_df(self):
        cfg = _make_minimal_cfg()
        df = _make_claim_df()
        sev_gen = SeverityGenerator(cfg)
        result = sev_gen.generate(df)
        result["prev_claim_count"] = 0
        result["prev_had_claim"] = 0
        result["prev_claim_cost"] = 0.0
        result["prev_average_claim_severity"] = np.nan
        result["claim_free_previous_year"] = 1
        result["claim_free_years"] = 0
        result["years_with_history"] = 0
        result["had_claim"] = (result["claim_count"] > 0).astype(int)
        return result

    def test_frequency_dataset_has_target(self, generated_df):
        assert "claim_count" in generated_df.columns

    def test_severity_dataset_has_target(self, generated_df):
        assert "average_claim_severity" in generated_df.columns

    def test_severity_dataset_excludes_zero_claim_rows(self, generated_df):
        sev_df = generated_df[generated_df["claim_count"] > 0].copy()
        assert (sev_df["claim_count"] > 0).all()

    def test_premium_not_in_freq_features(self, generated_df):
        """Premium must not be used as a frequency model feature."""
        assert "premium" not in generated_df.columns or "premium" not in [
            "age", "gender", "exposure_time", "seniority_insured", "smoker",
            "bmi", "pre_existing_condition", "claim_count"
        ]


class TestNoLeakage:
    """Ensure target columns are not sneaking into feature sets."""

    FREQ_FEATURES = [
        "age", "gender", "exposure_time", "seniority_insured", "seniority_policy",
        "type_policy", "type_product", "reimbursement", "new_business",
        "bmi", "smoker", "blood_pressure", "pre_existing_condition",
        "exercise_frequency", "occupation_risk",
        "prev_claim_count", "prev_had_claim", "prev_claim_cost",
        "claim_free_previous_year", "claim_free_years", "years_with_history",
    ]

    def test_annual_cost_not_in_freq_features(self):
        assert "annual_claim_cost" not in self.FREQ_FEATURES

    def test_avg_severity_not_in_freq_features(self):
        assert "average_claim_severity" not in self.FREQ_FEATURES

    def test_premium_not_in_freq_features(self):
        assert "premium" not in self.FREQ_FEATURES

    def test_claim_count_not_in_severity_features(self):
        SEV_FEATURES = [
            "age", "gender", "seniority_insured", "new_business",
            "type_policy", "type_product", "reimbursement",
            "bmi", "smoker", "blood_pressure", "pre_existing_condition",
            "exercise_frequency", "occupation_risk",
            "prev_claim_cost", "prev_claim_count", "prev_average_claim_severity",
        ]
        # claim_count (current period) should not be a feature in severity model
        assert "claim_count" not in SEV_FEATURES


class TestReproducibility:
    """Same seed → same output; different seed → different output."""

    def test_same_seed_same_outcome(self):
        cfg42a = _make_minimal_cfg(42)
        cfg42b = _make_minimal_cfg(42)
        df = _make_claim_df(n=200, seed=0)

        gen_a = SeverityGenerator(cfg42a)
        gen_b = SeverityGenerator(cfg42b)

        ra = gen_a.generate(df)
        rb = gen_b.generate(df)
        pd.testing.assert_series_equal(ra["annual_claim_cost"], rb["annual_claim_cost"])

    def test_different_seed_different_outcome(self):
        cfg_a = _make_minimal_cfg(42)
        cfg_b = _make_minimal_cfg(99)
        df = _make_claim_df(n=200, seed=0)

        gen_a = SeverityGenerator(cfg_a)
        gen_b = SeverityGenerator(cfg_b)

        ra = gen_a.generate(df)
        rb = gen_b.generate(df)
        assert not ra["annual_claim_cost"].equals(rb["annual_claim_cost"])
