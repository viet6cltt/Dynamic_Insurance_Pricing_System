# MLflow Docker Compose

This compose stack runs MLflow Tracking + Model Registry for local development.

Services:

- MLflow server: http://127.0.0.1:15000
- MinIO console: http://127.0.0.1:19001
- PostgreSQL backend store: localhost:5433

## Start

Run from this directory:

```bash
cp .env.example .env
docker compose up -d --build
```

Open MLflow:

```text
http://127.0.0.1:15000
```

Open MinIO console (started via docker-compose.full.yml):

```text
http://127.0.0.1:9001
```

Default MinIO credentials are:

```text
user: minioadmin
password: minioadminpassword
```

## Train From Host

When training from the repository root, export these variables so the local
Python process can log artifacts to MinIO through MLflow:

```bash
export MLFLOW_TRACKING_URI=http://127.0.0.1:15000
export MLFLOW_S3_ENDPOINT_URL=http://127.0.0.1:9002
export AWS_ACCESS_KEY_ID=minioadmin
export AWS_SECRET_ACCESS_KEY=minioadminpassword
```

Then run a training script with MLflow enabled:

```bash
python3 ml/scripts/train_portfolio_expected_cost_model.py --mlflow
python3 ml/scripts/train_health_risk_modifier_model.py --mlflow
```

Each script registers every trained candidate model as its own model version.
The best candidate by holdout RMSE is marked with model-version tag
`stage=Production` and registered-model alias `Production`.

## Stop

```bash
docker compose down
```

To delete local MLflow metadata and artifact volumes:

```bash
docker compose down -v
```
