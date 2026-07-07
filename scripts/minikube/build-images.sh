#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
IMAGE_TAG="${IMAGE_TAG:-local}"

if ! minikube status >/dev/null 2>&1; then
  echo "minikube is not running. Start it first: minikube start"
  exit 1
fi

eval "$(minikube docker-env)"

spring_services=(
  "service-discovery"
  "authorization-server"
  "user-service"
  "product-service"
  "pricing-service"
  "application-policy-service"
  "payment-service"
  "notification-service"
  "apigateway"
)

for service in "${spring_services[@]}"; do
  image="dynamic-insurance/${service}:${IMAGE_TAG}"
  echo "Building ${image}..."
  docker build -t "${image}" "${ROOT}/${service}"
done

echo "Building dynamic-insurance/ai-model-service:${IMAGE_TAG}..."
docker build -f "${ROOT}/ai-model-service/Dockerfile" -t "dynamic-insurance/ai-model-service:${IMAGE_TAG}" "${ROOT}"

echo "Building dynamic-insurance/mlflow-server:${IMAGE_TAG}..."
docker build -t "dynamic-insurance/mlflow-server:${IMAGE_TAG}" "${ROOT}/infra/docker/mlflow"

echo "Images were built inside the minikube Docker daemon."
