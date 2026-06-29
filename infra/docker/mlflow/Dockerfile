FROM python:3.11-slim

ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1

RUN pip install --no-cache-dir \
    mlflow==3.14.0 \
    psycopg2-binary==2.9.10 \
    boto3==1.43.36

EXPOSE 5000
