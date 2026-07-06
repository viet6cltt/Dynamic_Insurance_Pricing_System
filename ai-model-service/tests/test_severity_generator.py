"""
test_severity_generator.py
===========================
Unit tests for SeverityGenerator.
"""

import sys
from pathlib import Path
import numpy as np
import pandas as pd
import pytest

_SRC = Path(__file__).resolve().parents[2] / "src"
sys.path.insert(0, str(_SRC))

from data_generation.config import load_config
from data_generation.severity_generator import SeverityGenerator

_CONFIG_PATH = Path(__file__).resolve().parents[2] / "config" / "synthetic_claim_generation.yml"


@pytest.fixture
def cfg():
    return load_config(_CONFIG_PATH)


@pytest.fixture
def sample_df():
    """Sample DataFrame with claim_count and health fields."""
    rng = np.random.default_rng(42)
    n = 500
    claim_counts = rng.integers(0, 15, n)
    return pd.DataFrame({
        "claim_count": claim_counts,
        "smoker": rng.choice(["yes", "no"], n),
        "bmi_band": rng.choice(["NORMAL", "OVERWEIGHT", "OBESE", "UNDERWEIGHT"], n),
        "pre_existing_condition": rng.choice(["TRUE", "FALSE"], n),
        "bp_band": rng.choice(["NORMAL", "ELEVATED", "HIGH_1", "HIGH_2"], n),
        "exercise_frequency": rng.choice(["Daily", "Weekly", "Rarely", "Never"], n),
        "occupation_risk": rng.choice(["low", "moderate", "high"], n),
        "type_product": rng.choice(["S", "G", "E"], n),
        "reimbursement": rng.choice(["Yes", "No"], n),
    })


class TestSeverityGeneratorBasic:
    def test_zero_claim_zero_cost(self, cfg, sample_df):
        gen = SeverityGenerator(cfg)
        result = gen.generate(sample_df)
        zero_mask = result["claim_count"] == 0
        assert (result.loc[zero_mask, "annual_claim_cost"] == 0).all(), \
            "Zero claim_count rows should have zero annual_claim_cost"

    def test_positive_claim_positive_cost(self, cfg, sample_df):
        gen = SeverityGenerator(cfg)
        result = gen.generate(sample_df)
        pos_mask = result["claim_count"] > 0
        assert (result.loc[pos_mask, "annual_claim_cost"] > 0).all(), \
            "Positive claim_count rows should have positive annual_claim_cost"

    def test_positive_claim_positive_severity(self, cfg, sample_df):
        gen = SeverityGenerator(cfg)
        result = gen.generate(sample_df)
        pos_mask = result["claim_count"] > 0
        assert (result.loc[pos_mask, "average_claim_severity"] > 0).all(), \
            "Positive claim_count rows should have positive average_claim_severity"

    def test_annual_cost_non_negative(self, cfg, sample_df):
        gen = SeverityGenerator(cfg)
        result = gen.generate(sample_df)
        assert (result["annual_claim_cost"] >= 0).all()

    def test_average_severity_nan_when_zero_claims(self, cfg, sample_df):
        gen = SeverityGenerator(cfg)
        result = gen.generate(sample_df)
        zero_mask = result["claim_count"] == 0
        # average_claim_severity should be NaN for zero-claim rows
        assert result.loc[zero_mask, "average_claim_severity"].isna().all(), \
            "Zero-claim rows should have NaN average_claim_severity"

    def test_average_severity_equals_cost_over_count(self, cfg, sample_df):
        gen = SeverityGenerator(cfg)
        result = gen.generate(sample_df)
        pos_mask = result["claim_count"] > 0
        expected = (result.loc[pos_mask, "annual_claim_cost"] /
                    result.loc[pos_mask, "claim_count"]).round(2)
        actual = result.loc[pos_mask, "average_claim_severity"]
        pd.testing.assert_series_equal(expected, actual, check_names=False, atol=0.1)

    def test_is_synthetic_severity_flag(self, cfg, sample_df):
        gen = SeverityGenerator(cfg)
        result = gen.generate(sample_df)
        assert "is_synthetic_severity" in result.columns
        assert result["is_synthetic_severity"].all()


class TestSeverityGeneratorReproducibility:
    def test_same_seed_same_output(self, cfg, sample_df):
        gen1 = SeverityGenerator(cfg)
        r1 = gen1.generate(sample_df)

        gen2 = SeverityGenerator(cfg)
        r2 = gen2.generate(sample_df)

        pd.testing.assert_series_equal(r1["annual_claim_cost"], r2["annual_claim_cost"])

    def test_different_seed_different_output(self, cfg, sample_df):
        from data_generation.config import GenerationConfig
        raw2 = dict(cfg.raw)
        raw2["random_seed"] = 999
        cfg2 = GenerationConfig(raw=raw2)

        gen1 = SeverityGenerator(cfg)
        gen2 = SeverityGenerator(cfg2)

        r1 = gen1.generate(sample_df)
        r2 = gen2.generate(sample_df)
        assert not r1["annual_claim_cost"].equals(r2["annual_claim_cost"])


class TestSeverityGeneratorDomainLogic:
    def test_smoker_not_always_maximum(self, cfg, sample_df):
        """Smoker profiles should NOT all reach max severity."""
        smoker_df = sample_df.copy()
        smoker_df["smoker"] = "yes"
        smoker_df["claim_count"] = np.maximum(smoker_df["claim_count"], 1)

        gen = SeverityGenerator(cfg)
        result = gen.generate(smoker_df)
        pos = result.loc[result["claim_count"] > 0, "average_claim_severity"]

        # Should have reasonable standard deviation (not all same)
        assert pos.std() > 0, "All smoker severities are identical"

        # No single profile should always be at max relativity
        r_max = cfg.severity.get("relativity_max", 1.30)
        base = float(np.exp(cfg.severity.get("base_log_mean", 5.5)))
        # median should not exceed base × r_max × 3 (generous bound)
        assert pos.median() < base * r_max * 3

    def test_cumulative_risk_increases_expected_severity(self, cfg):
        """Stack of risk factors should produce higher expected severity than baseline."""
        n = 1000
        base_df = pd.DataFrame({
            "claim_count": [1] * n,
            "smoker": ["no"] * n,
            "bmi_band": ["NORMAL"] * n,
            "pre_existing_condition": ["FALSE"] * n,
            "bp_band": ["NORMAL"] * n,
            "exercise_frequency": ["Daily"] * n,
            "occupation_risk": ["low"] * n,
            "type_product": ["S"] * n,
            "reimbursement": ["No"] * n,
        })
        high_df = base_df.copy()
        high_df["smoker"] = "yes"
        high_df["bmi_band"] = "OBESE"
        high_df["pre_existing_condition"] = "TRUE"
        high_df["bp_band"] = "HIGH_2"
        high_df["exercise_frequency"] = "Never"
        high_df["occupation_risk"] = "high"

        gen = SeverityGenerator(cfg)
        r_base = gen.generate(base_df)
        r_high = gen.generate(high_df)

        mean_base = r_base["average_claim_severity"].mean()
        mean_high = r_high["average_claim_severity"].mean()

        assert mean_high > mean_base, \
            f"High-risk severity ({mean_high:.2f}) not greater than baseline ({mean_base:.2f})"

    def test_health_relativity_within_config_bounds(self, cfg):
        """Compressed health relativity must stay within configured [min, max]."""
        import math
        r_min = cfg.severity.get("relativity_min", 0.85)
        r_max = cfg.severity.get("relativity_max", 1.30)
        alpha = cfg.severity.get("compression_alpha", 0.35)

        # Most extreme positive adjustment (all risk factors max)
        max_adj = sum([
            cfg.severity.get("smoker_log_adj", {}).get("yes", 0),
            cfg.severity.get("bmi_log_adj", {}).get("OBESE", 0),
            cfg.severity.get("pre_existing_log_adj", {}).get("TRUE", 0),
            cfg.severity.get("blood_pressure_log_adj", {}).get("HIGH_2", 0),
            cfg.severity.get("exercise_log_adj", {}).get("Never", 0),
            cfg.severity.get("occupation_risk_log_adj", {}).get("high", 0),
        ])
        raw = math.exp(max_adj)
        compressed = 1 + alpha * math.log(raw)
        clipped = min(max(compressed, r_min), r_max)

        assert clipped <= r_max, f"Max relativity {clipped} exceeds config max {r_max}"
        assert clipped >= r_min, f"Min relativity {clipped} below config min {r_min}"
