"""
test_health_profile_generator.py
=================================
Unit tests for HealthProfileGenerator.
"""

import sys
from pathlib import Path
import numpy as np
import pandas as pd
import pytest

# Allow importing from src
_SRC = Path(__file__).resolve().parents[2] / "src"
sys.path.insert(0, str(_SRC))

from data_generation.config import load_config
from data_generation.health_profile_generator import HealthProfileGenerator

_CONFIG_PATH = Path(__file__).resolve().parents[2] / "config" / "synthetic_claim_generation.yml"


@pytest.fixture
def cfg():
    return load_config(_CONFIG_PATH)


@pytest.fixture
def small_health_df():
    """Minimal health DataFrame for testing."""
    rng = np.random.default_rng(42)
    n = 200
    return pd.DataFrame({
        "age": rng.integers(18, 75, n),
        "sex": rng.choice(["male", "female"], n),
        "bmi": rng.uniform(18, 40, n).round(1),
        "smoker": rng.choice(["yes", "no"], n),
        "blood_pressure": rng.uniform(100, 160, n).round(1),
        "pre_existing_condition": rng.choice(["TRUE", "FALSE"], n),
        "exercise_frequency": rng.choice(["Daily", "Weekly", "Rarely", "Never"], n),
        "occupation_risk": rng.choice(["low", "moderate", "high"], n),
        "charges": rng.exponential(10000, n),
    })


@pytest.fixture
def small_portfolio_df():
    """Minimal portfolio DataFrame for testing."""
    rng = np.random.default_rng(42)
    n = 100
    return pd.DataFrame({
        "age": rng.integers(18, 75, n),
        "gender": rng.choice(["M", "F"], n),
        "exposure_time": rng.uniform(0.1, 1.0, n).round(4),
        "type_product": rng.choice(["S", "G", "E"], n),
    })


class TestHealthProfileGeneratorFit:
    def test_fit_runs_without_error(self, cfg, small_health_df):
        gen = HealthProfileGenerator(cfg)
        gen.fit(small_health_df)
        assert gen._health_df is not None

    def test_donor_lookup_has_entries(self, cfg, small_health_df):
        gen = HealthProfileGenerator(cfg)
        gen.fit(small_health_df)
        assert len(gen._donor_lookup) > 0


class TestHealthProfileGeneratorTransform:
    def test_transform_returns_same_row_count(self, cfg, small_health_df, small_portfolio_df):
        gen = HealthProfileGenerator(cfg)
        gen.fit(small_health_df)
        result = gen.transform(small_portfolio_df)
        assert len(result) == len(small_portfolio_df)

    def test_health_fields_added(self, cfg, small_health_df, small_portfolio_df):
        gen = HealthProfileGenerator(cfg)
        gen.fit(small_health_df)
        result = gen.transform(small_portfolio_df)
        for field in ["bmi", "smoker", "blood_pressure", "pre_existing_condition",
                      "exercise_frequency", "occupation_risk"]:
            assert field in result.columns, f"Missing field: {field}"

    def test_charges_not_copied(self, cfg, small_health_df, small_portfolio_df):
        """charges from health dataset must NOT appear in portfolio output."""
        gen = HealthProfileGenerator(cfg)
        gen.fit(small_health_df)
        result = gen.transform(small_portfolio_df)
        assert "charges" not in result.columns

    def test_bmi_band_derived(self, cfg, small_health_df, small_portfolio_df):
        gen = HealthProfileGenerator(cfg)
        gen.fit(small_health_df)
        result = gen.transform(small_portfolio_df)
        assert "bmi_band" in result.columns
        valid_bands = {"UNDERWEIGHT", "NORMAL", "OVERWEIGHT", "OBESE", "nan", "NaN"}
        assert set(result["bmi_band"].dropna().unique()).issubset(valid_bands)

    def test_health_risk_band_derived(self, cfg, small_health_df, small_portfolio_df):
        gen = HealthProfileGenerator(cfg)
        gen.fit(small_health_df)
        result = gen.transform(small_portfolio_df)
        assert "health_risk_band" in result.columns
        valid = {"LOW", "MODERATE", "HIGH", "nan"}
        assert set(result["health_risk_band"].dropna().unique()).issubset(valid)

    def test_is_synthetic_flag(self, cfg, small_health_df, small_portfolio_df):
        gen = HealthProfileGenerator(cfg)
        gen.fit(small_health_df)
        result = gen.transform(small_portfolio_df)
        assert "is_synthetic_health_profile" in result.columns
        assert result["is_synthetic_health_profile"].all()

    def test_smoker_values_valid(self, cfg, small_health_df, small_portfolio_df):
        gen = HealthProfileGenerator(cfg)
        gen.fit(small_health_df)
        result = gen.transform(small_portfolio_df)
        if "smoker" in result.columns:
            valid_smoker = {"yes", "no"}
            actual = set(result["smoker"].dropna().unique())
            assert actual.issubset(valid_smoker), f"Invalid smoker values: {actual - valid_smoker}"

    def test_reproducibility_same_seed(self, cfg, small_health_df, small_portfolio_df):
        gen1 = HealthProfileGenerator(cfg)
        gen1.fit(small_health_df)
        result1 = gen1.transform(small_portfolio_df)

        gen2 = HealthProfileGenerator(cfg)
        gen2.fit(small_health_df)
        result2 = gen2.transform(small_portfolio_df)

        pd.testing.assert_series_equal(result1["bmi"], result2["bmi"])
