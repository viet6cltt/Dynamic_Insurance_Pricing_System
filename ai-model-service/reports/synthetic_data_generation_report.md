# Synthetic Insurance Claim Data Generation Report

> **Data type**: SEMI-SYNTHETIC – simulation parameters are illustrative only
> and do NOT represent official actuarial pricing factors.

**Generated**: 2026-07-05 21:48:42
**Config**: `/home/viet6cltt/Dev/Viettel_Project/Dynamic_Insurance_Pricing_System/config/synthetic_claim_generation.yml`
**Random seed**: `42`
**Generation version**: `1.0.0`

---

## 1. Objective

Build a frequency–severity training pipeline for a health insurance dynamic
pricing system. The pipeline produces:

- `synthetic_insurance_claims.csv` – master semi-synthetic claim dataset
- `frequency_training_dataset.csv` – ready for Poisson / NB / XGBoost count models
- `severity_training_dataset.csv` – ready for Gamma / Lognormal / XGBoost regression

**Expected Loss Cost = Expected Claim Frequency × Expected Claim Severity**

---

## 2. Source Data

| Dataset | Rows | Description |
|---|---|---|
| `health_insurance_cost_and_risk_dataset.csv` | 1338 | Health profile + annual charges |
| `health_insurance_portfolio.csv` | 228711 | Contract exposure + claim outcomes |

---

## 3. Limitations of the Health Dataset

- Contains **1338 rows** – too small to represent the full portfolio.
- Nearly **all records have charges > 0** (zero-charge rate:
  0.0%) –
  does not represent the zero-claim insured population.
- Therefore, `charges` cannot directly serve as a frequency indicator.
- `charges` is used only as a **severity shape reference** (annual medical cost
  for those with positive claims).

---

## 4. Health Dataset Distribution

### 4.1 Charges (all records)

| Statistic | Value |
|---|---|
| Count | 1338 |
| Mean | 13270.422265141257 |
| Median | 9382.033 |
| Std | 12110.011236694001 |
| Min | 1121.8739 |
| Max | 63770.42801 |
| P5 | 1757.7534 |
| P25 | 4740.28715 |
| P75 | 16639.912515 |
| P90 | 34831.7197 |
| P95 | 41181.827787499926 |
| P99 | 48537.480726 |
| Skewness | 1.5158796580240388 |

### 4.2 Positive Charges

| Statistic | Value |
|---|---|
| Count | 1338 |
| Mean | 13270.422265141257 |
| Median | 9382.033 |
| P90 | 34831.7197 |
| P99 | 48537.480726 |
| Skewness | 1.5158796580240388 |
| Lognormal μ | 9.098658729424766 |
| Lognormal σ | 0.9195271129310933 |

---

## 5. Portfolio Dataset Distribution

### 5.1 Claim Frequency (n_medical_services)

| Statistic | Value |
|---|---|
| Mean | 16.812291494506166 |
| Median | 6.0 |
| Std | 28.228930738786392 |
| Zero-claim rate | 0.2704 |
| Var/Mean (overdispersion) | 47.398 |
| Recommend NB | True |

### 5.2 Claim Severity (avg_claim_severity, positive claims only)

| Statistic | Value |
|---|---|
| Count | 166877 |
| Mean | 43.445204046956405 |
| Median | 30.017142857142858 |
| P90 | 81.77755757575756 |
| P99 | 258.09125106382857 |
| Skewness | 81.13274195751134 |
| Lognormal μ | 3.4012179960869986 |
| Lognormal σ | 0.8172945079753534 |

---

## 6. Frequency Model Target

- **Target**: `claim_count` (aliased from `n_medical_services`)
- **Offset**: `log(exposure_time)` must be used as an offset in GLM models
- **Recommended families**: Negative Binomial, Poisson, Tweedie (p≈1)
- **No premium in features**: eliminates circular pricing risk

## 7. Severity Model Target

- **Target**: `average_claim_severity = annual_claim_cost / claim_count`
- **Training rows**: only records with `claim_count > 0`
- **Recommended families**: Gamma (log link), Lognormal, Tweedie (p≈2)

---

## 8. Feature Sets

### 8.1 Frequency Features
age, gender, exposure_time, seniority_insured, seniority_policy, type_policy,
type_policy_dg, type_product, reimbursement, new_business, distribution_channel,
bmi, smoker, blood_pressure, pre_existing_condition, exercise_frequency,
occupation_risk, prev_claim_count, prev_had_claim, prev_claim_cost,
claim_free_previous_year, claim_free_years, years_with_history

### 8.2 Severity Features
age, gender, seniority_insured, new_business, type_policy, type_policy_dg,
type_product, reimbursement, bmi, smoker, blood_pressure, pre_existing_condition,
exercise_frequency, occupation_risk, prev_claim_cost, prev_claim_count,
prev_average_claim_severity

---

## 9. Health Profile Generation Method

**Method**: Donor Sampling (conditional on age_band × gender)

For each portfolio record:
1. Find all health dataset records with the same age_band and gender.
2. Sample one record uniformly at random.
3. Copy bmi, smoker, blood_pressure, pre_existing_condition, exercise_frequency,
   occupation_risk to portfolio record.
4. Do NOT copy `charges`.

Fallback: if no donor with matching age_band + gender, match on age_band only,
then sample from entire health dataset.

---

## 10. Claim Frequency Generation Method

For the portfolio dataset, `n_medical_services` is aliased directly to
`claim_count` (preserving the real observed counts). This ensures the
frequency distribution reflects actual historical utilisation.

If simulating from scratch (configurable), the generator uses:

    claim_count ~ NegativeBinomial(mu, dispersion)
    mu = exposure_time × base_rate × product(all factor multipliers)

Multipliers are sourced from `config/synthetic_claim_generation.yml` only.

---

## 11. Claim Severity Generation Method

Per-claim severity is drawn from:

    claim_severity_i ~ Lognormal(mu_profile, sigma)

where:

    mu_profile = base_log_mean (calibrated from portfolio avg_claim_severity)
               + log(compressed_health_relativity)
               + product_log_adj + reimbursement_log_adj

Health relativity compression:

    compressed = 1 + alpha × log(raw_health_relativity)
    clipped to [0.85, 1.3]

Annual cost: sum of per-claim severities for claim_count > 0, else 0.

---

## 12. Business Assumptions

> ⚠️ These are simulation parameters for illustrative purposes only.
> They are NOT official actuarial pricing factors.

- Smoker effect on frequency: +10% (moderate, not extreme)
- Smoker effect on severity: +10% log-scale → compressed
- Pre-existing condition frequency uplift: +20%
- BMI Obese frequency uplift: +12%
- Claim-free year discount: -5% per year (capped at 0.70×)
- Severity relativity clipped to [0.85, 1.30] to prevent extreme values
- Premium is NOT a feature in any model

---

## 13. Validation Results

### Constraint Checks

| Check | Count |
|---|---|
| Total violations | 0 |
| Negative claim_count | 0 |
| Non-integer claim_count | 0 |
| Zero count, nonzero cost | 0 |
| Positive count, zero severity | 0 |
| Exposure out of range | 0 |

### Distribution Summary

| Metric | Value |
|---|---|
| Zero-claim rate | 0.2704 |
| Mean claim_count | 16.8123 |
| Var/Mean (overdispersion) | 47.398 |
| Mean annual cost (positive) | 984.56 |
| Mean avg_severity | 42.79 |
| Median avg_severity | 41.31 |
| P90 avg_severity | 57.49 |
| Skewness avg_severity | 5.4436 |

### Data Leakage

Leakage detected: **False**

---

## 14. Sensitivity Analysis

| Profile | Expected Per-Claim Severity |
|---|---|
| Baseline | 39.47 |
| Smoker | 40.86 |
| Obese | 41.14 |
| Pre-existing | 41.55 |
| High BP | 41.27 |
| Low exercise | 40.86 |
| High occ risk | 40.58 |
| Rich product | 48.69 |
| Smoker+Obese | 42.53 |
| Smoker+Obese+Disease | 44.61 |
| Healthy non-smoker | 39.05 |


---

## 15. Limitations and Improvements

1. **Small health dataset** (1,338 rows): donor pool is limited.
   Improvement: use conditional parametric sampling or expand health survey data.

2. **Simulation parameters not validated**: frequency and severity multipliers
   are expert assumptions, not fitted from enterprise data.
   Improvement: fit GLM relativities on historical claims before simulation.

3. **History features partially synthetic**: for first-period records,
   prior experience is simulated, not observed.

4. **No geographical variables**: portfolio has no postcode or region.
   Improvement: add geographic risk bands if available.

5. **Single product/benefit tier**: type_product {S, G, E} is a simplified
   product hierarchy. Real products may have many more dimensions.

> **Disclaimer**: This dataset is semi-synthetic, generated for demonstration
> of the frequency–severity ML architecture. Models trained on this data
> must NOT be presented as having real actuarial validity without further
> validation against actual enterprise claims data.

---

## 16. Output Files

| File | Rows | Description |
|---|---|---|
| `synthetic_insurance_claims.csv` | 228711 | Master dataset |
| `frequency_training_dataset.csv` | 228711 | Frequency model training set |
| `severity_training_dataset.csv` | 166877 | Severity model training set (claim_count > 0) |

## 17. How to Run

```bash
cd <project_root>
# Activate virtualenv if needed
source .venv/bin/activate

# Run the full pipeline
python ai-model-service/src/scripts/generate_synthetic_claim_data.py

# Skip EDA plots (faster)
python ai-model-service/src/scripts/generate_synthetic_claim_data.py --skip-eda

# Custom config
python ai-model-service/src/scripts/generate_synthetic_claim_data.py \
    --config config/synthetic_claim_generation.yml
```
