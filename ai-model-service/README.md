# AI Model Service

This service returns Frequency and Severity AI signals for Pricing Service.

Responsibilities:

- Load Frequency model to predict annual claim frequency.
- Load Severity model to predict per-claim severity.
- Calculate Pure Premium as `predictedAnnualFrequency * predictedAverageSeverity`.
- Return pure premium predictions, risk level, model versions, and frequency/severity explanations.

Guardrails:

- The service does not receive or use Coverage Plan `loadingRate`.
- The service does not calculate `finalPremium`.
- Pricing Service / Rating Engine applies:

```text
finalPremium = purePremium * (1 + loadingRate)
```

## Contract

See `contracts/openapi/ai-model-service.openapi.yml`.

## Local Training Flow

Run from the repository root:

```bash
python3 -m pip install -r ai-model-service/requirements.txt
python3 ml/train.py --model-type FREQUENCY
python3 ml/train.py --model-type SEVERITY
```

## Run Service

Run from inside this directory:

```bash
uvicorn app.main:app --reload --port 8005
```
