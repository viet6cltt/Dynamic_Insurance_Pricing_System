#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

echo "Deleting Dynamic Insurance resources..."
kubectl delete -k "${ROOT}/k8s/overlays/local" --ignore-not-found=true

echo "Resources were deleted. PersistentVolumeClaims may remain depending on the minikube storage class."
