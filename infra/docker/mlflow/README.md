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
python3 ml/scripts/train_frequency_model.py --mlflow
python3 ml/scripts/train_severity_model.py --mlflow
```

The current pricing flow uses two registered models:

- `FrequencyModel`
- `SeverityModel`

With `--mlflow`, the scripts upload runs, metrics, reports, metadata and model
artifacts to MLflow Tracking, then create model versions in the MLflow Model
Registry. The generated CSV files remain local training/reference inputs; they
are not uploaded as dataset artifacts by default.

By default, training uses `--deployment-mode candidate`, so candidates are
registered for review without changing production aliases.

## Stop

```bash
docker compose down
```

To delete local MLflow metadata and artifact volumes:

```bash
docker compose down -v
```
