#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
IMAGE_TAG="${IMAGE_TAG:-local}"
PUSH="${PUSH:-false}"

APP_SERVICES=(
  service-discovery
  authorization-server
  user-service
  product-service
  ai-model-service
  pricing-service
  application-policy-service
  payment-service
  notification-service
  apigateway
)

cd "$ROOT_DIR"

echo "Building application images with IMAGE_TAG=$IMAGE_TAG"
docker compose -f docker-compose.full.yml build "${APP_SERVICES[@]}"

if [[ "$PUSH" == "true" ]]; then
  echo "Pushing application images with IMAGE_TAG=$IMAGE_TAG"
  for service in "${APP_SERVICES[@]}"; do
    docker push "dynamic-insurance/${service}:${IMAGE_TAG}"
  done
fi
