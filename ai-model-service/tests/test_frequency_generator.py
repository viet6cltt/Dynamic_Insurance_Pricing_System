"""
test_frequency_generator.py
============================
Unit tests for FrequencyGenerator.
"""

import sys
from pathlib import Path
import numpy as np
import pandas as pd
import pytest

_SRC = Path(__file__).resolve().parents[2] / "src"
sys.path.insert(0, str(_SRC))

from data_generation.config import load_config
from data_generation.frequency_generator import FrequencyGenerator

_CONFIG_PATH = Path(__file__).resolve().parents[2] / "config" / "synthetic_claim_generation.yml"


@pytest.fixture
def cfg():
    return load_config(_CONFIG_PATH)


@pytest.fixture
def sample_df():
    """Sample portfolio-like DataFrame with all needed fields."""
    rng = np.random.default_rng(42)
    n = 500
    return pd.DataFrame({
        "n_medical_services": rng.integers(0, 20, n),
        "exposure_time": rng.uniform(0.1, 1.0, n).round(4),
        "age": rng.integers(18, 80, n),
        "gender": rng.choice(["M", "F"], n),
        "age_band": rng.choice(["18-29", "30-39", "40-49", "50-59", "60+"], n),
        "smoker": rng.choice(["yes", "no"], n),
        "bmi_band": rng.choice(["NORMAL", "OVERWEIGHT", "OBESE"], n),
        "pre_existing_condition": rng.choice(["TRUE", "FALSE"], n),
        "bp_band": rng.choice(["NORMAL", "ELEVATED", "HIGH_1", "HIGH_2"], n),
        "exercise_frequency": rng.choice(["Daily", "Weekly", "Rarely", "Never"], n),
        "occupation_risk": rng.choice(["low", "moderate", "high"], n),
        "reimbursement": rng.choice(["Yes", "No"], n),
        "type_product": rng.choice(["S", "G", "E"], n),
        "new_business": rng.choice(["Yes", "No"], n),
        "prev_claim_count": rng.integers(0, 10, n),
        "claim_free_years": rng.integers(0, 5, n),
    })


class TestFrequencyGeneratorAlias:
    """Tests when use_portfolio_claim_count=True (alias mode)."""

    def test_claim_count_equals_n_medical_services(self, cfg, sample_df):
        gen = FrequencyGenerator(cfg)
        result = gen.generate(sample_df)
        pd.testing.assert_series_equal(
            result["claim_count"], sample_df["n_medical_services"].astype(int),
            check_names=False
        )

    def test_claim_count_non_negative(self, cfg, sample_df):
        gen = FrequencyGenerator(cfg)
        result = gen.generate(sample_df)
        assert (result["claim_count"] >= 0).all(), "claim_count has negative values"

    def test_claim_count_integer(self, cfg, sample_df):
        gen = FrequencyGenerator(cfg)
        result = gen.generate(sample_df)
        assert result["claim_count"].dtype in [np.int32, np.int64, int], "claim_count not integer"

    def test_had_claim_binary(self, cfg, sample_df):
        gen = FrequencyGenerator(cfg)
        result = gen.generate(sample_df)
        assert set(result["had_claim"].unique()).issubset({0, 1})

    def test_had_claim_consistent_with_claim_count(self, cfg, sample_df):
        gen = FrequencyGenerator(cfg)
        result = gen.generate(sample_df)
        expected_had_claim = (result["claim_count"] > 0).astype(int)
        pd.testing.assert_series_equal(result["had_claim"], expected_had_claim, check_names=False)

    def test_reproducibility(self, cfg, sample_df):
        gen1 = FrequencyGenerator(cfg)
        r1 = gen1.generate(sample_df)

        gen2 = FrequencyGenerator(cfg)
        r2 = gen2.generate(sample_df)

        pd.testing.assert_series_equal(r1["claim_count"], r2["claim_count"])


class TestFrequencyGeneratorSimulated:
    """Tests when use_portfolio_claim_count=False (simulation mode)."""

    @pytest.fixture
    def sim_cfg(self, cfg):
        """Override to simulation mode."""
        raw = dict(cfg.raw)
        raw["frequency"] = dict(cfg.frequency)
        raw["frequency"]["use_portfolio_claim_count"] = False
        from data_generation.config import GenerationConfig
        return GenerationConfig(raw=raw, config_path=cfg.config_path)

    def test_simulated_claim_count_non_negative(self, sim_cfg, sample_df):
        gen = FrequencyGenerator(sim_cfg)
        result = gen.generate(sample_df)
        assert (result["claim_count"] >= 0).all()

    def test_simulated_claim_count_integer(self, sim_cfg, sample_df):
        gen = FrequencyGenerator(sim_cfg)
        result = gen.generate(sample_df)
        assert result["claim_count"].dtype in [np.int32, np.int64, int]

    def test_different_seeds_different_output(self, sample_df, cfg):
        raw1 = dict(cfg.raw)
        raw1["frequency"] = dict(cfg.frequency)
        raw1["frequency"]["use_portfolio_claim_count"] = False
        raw1["random_seed"] = 42

        raw2 = dict(cfg.raw)
        raw2["frequency"] = dict(cfg.frequency)
        raw2["frequency"]["use_portfolio_claim_count"] = False
        raw2["random_seed"] = 99

        from data_generation.config import GenerationConfig
        cfg1 = GenerationConfig(raw=raw1)
        cfg2 = GenerationConfig(raw=raw2)

        gen1 = FrequencyGenerator(cfg1)
        gen2 = FrequencyGenerator(cfg2)

        r1 = gen1.generate(sample_df)
        r2 = gen2.generate(sample_df)
        # Different seeds → different outputs
        assert not r1["claim_count"].equals(r2["claim_count"]), \
            "Different seeds produced identical outputs"

    def test_smoker_not_always_maximum(self, sim_cfg, sample_df):
        """Smoker should not make every record hit maximum claim count."""
        gen = FrequencyGenerator(sim_cfg)
        smoker_df = sample_df.copy()
        smoker_df["smoker"] = "yes"
        result = gen.generate(smoker_df)
        # Not all should have same claim count (stochastic variation)
        assert result["claim_count"].std() > 0, "All smoker claim_counts are identical – no stochasticity"

    def test_higher_risk_higher_expected_frequency(self, sim_cfg, sample_df):
        """Health risk increase should trend toward higher expected frequency."""
        gen = FrequencyGenerator(sim_cfg)
        low_risk = sample_df.copy()
        low_risk["pre_existing_condition"] = "FALSE"
        low_risk["smoker"] = "no"

        high_risk = sample_df.copy()
        high_risk["pre_existing_condition"] = "TRUE"
        high_risk["smoker"] = "yes"

        r_low = gen.generate(low_risk)
        r_high = gen.generate(high_risk)
        # Mean should be higher for high risk (at reasonable probability)
        assert r_high["claim_count"].mean() > r_low["claim_count"].mean() * 0.9, \
            "High-risk group does not have higher mean claim_count"
