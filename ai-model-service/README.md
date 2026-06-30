# AI Model Service

This service returns hybrid Health Insurance AI signals for Pricing Service.

Responsibilities:

- Load Model 1 from `ml/artifacts/portfolio_expected_cost_model.joblib` when
  available and return `predictedAnnualClaimCost` plus `portfolioRiskFactor`.
- Load Model 2 from `ml/artifacts/health_risk_modifier_model.joblib`.
- Predict `predictedHealthCost` from health profile fields.
- Predict `baselineHealthCost` for the same age/sex/children/region with
  `bmi=22` and `smoker=no`.
- Return the clamped `healthRiskFactor`.

Guardrails:

- The service does not receive or use `basePremium`.
- The service does not calculate `finalPremium`.
- Pricing Service / Rating Engine applies:

```text
finalPremium = basePremium * portfolioRiskFactor * healthRiskFactor * underwritingRules * businessRules
```

## Contract

See `contracts/openapi/ai-model-service.openapi.yml`.

## Local Training Flow

Run from the repository root:

```bash
python3 -m pip install -r ai-model-service/requirements.txt
python3 ml/scripts/train_portfolio_expected_cost_model.py
python3 ml/scripts/train_health_risk_modifier_model.py
python3 ml/scripts/explain_health_risk_modifier_model.py
```

## Run Service

Run from inside this directory:

```bash
uvicorn app.main:app --reload --port 8005
```
