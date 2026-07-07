#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SKIP_BUILD=false

for arg in "$@"; do
  case "$arg" in
    --skip-build) SKIP_BUILD=true ;;
    *)
      echo "Unknown argument: $arg" >&2
      echo "Usage: $0 [--skip-build]" >&2
      exit 1
      ;;
  esac
done

if ! minikube status >/dev/null 2>&1; then
  echo "Starting minikube..."
  minikube start
fi

if [ "$SKIP_BUILD" = false ]; then
  "${ROOT}/scripts/minikube/build-images.sh"
fi

echo "Deploying Dynamic Insurance to minikube..."
kubectl apply -k "${ROOT}/k8s/overlays/local"

echo "Waiting for datastores..."
for sts in \
  redis kafka minio mlflow-postgres \
  user-db auth-db product-db pricing-db policy-db payment-db notification-db; do
  kubectl rollout status "statefulset/${sts}" -n dynamic-insurance --timeout=300s
done

echo "Waiting for init jobs..."
kubectl wait --for=condition=complete job/kafka-init -n dynamic-insurance --timeout=300s
kubectl wait --for=condition=complete job/minio-init -n dynamic-insurance --timeout=300s

echo "Waiting for applications..."
for deploy in \
  service-discovery authorization-server user-service product-service ai-model-service \
  pricing-service application-policy-service payment-service notification-service apigateway \
  kafka-ui mailpit mlflow-server; do
  kubectl rollout status "deployment/${deploy}" -n dynamic-insurance --timeout=600s
done

cat <<EOF

Dynamic Insurance is deployed on minikube.

API Gateway:      http://localhost:30080
Authorization:    http://localhost:30090
Eureka:           http://localhost:30876
AI model service: http://localhost:30085
Kafka UI:         http://localhost:30081
Mailpit:          http://localhost:30082
MinIO console:    http://localhost:30901
MLflow:           http://localhost:30500

If NodePort localhost forwarding is unavailable on your driver, use:
  minikube service apigateway -n dynamic-insurance
EOF
