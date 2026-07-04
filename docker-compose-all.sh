#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ACTION="${1:-}"

COMPOSE_FILES=(
  "infra/docker/redis/docker-compose.yml"
  "infra/docker/kafka/docker-compose.yml"
  "user-service/docker-compose.yml"
  "authorization-server/docker-compose.yml"
  "product-service/docker-compose.yml"
  "pricing-service/docker-compose.yml"
  "application-policy-service/docker-compose.yml"
  "payment-service/docker-compose.yml"
  "notification-service/docker-compose.yml"
)

usage() {
  cat <<'EOF'
Usage:
  ./docker-compose-all.sh up
  ./docker-compose-all.sh down
  ./docker-compose-all.sh restart
  ./docker-compose-all.sh ps

This script starts/stops all local Docker Compose dependency stacks.
It runs each compose file with its own project name so database volumes stay isolated.
EOF
}

project_name_for() {
  local compose_file="$1"
  local dir_name

  dir_name="$(basename "$(dirname "$compose_file")")"

  case "$compose_file" in
    infra/docker/redis/docker-compose.yml) echo "dynamic-insurance-redis" ;;
    infra/docker/kafka/docker-compose.yml) echo "dynamic-insurance-kafka" ;;
    *) echo "dynamic-insurance-${dir_name}" ;;
  esac
}

run_compose() {
  local compose_file="$1"
  shift

  docker compose \
    -p "$(project_name_for "$compose_file")" \
    -f "$ROOT_DIR/$compose_file" \
    "$@"
}

up_all() {
  for compose_file in "${COMPOSE_FILES[@]}"; do
    echo "Starting $compose_file"
    run_compose "$compose_file" up -d
  done
}

down_all() {
  for ((idx=${#COMPOSE_FILES[@]}-1; idx>=0; idx--)); do
    compose_file="${COMPOSE_FILES[$idx]}"
    echo "Stopping $compose_file"
    run_compose "$compose_file" down
  done
}

ps_all() {
  for compose_file in "${COMPOSE_FILES[@]}"; do
    echo
    echo "$compose_file"
    run_compose "$compose_file" ps
  done
}

case "$ACTION" in
  up)
    up_all
    ;;
  down)
    down_all
    ;;
  restart)
    down_all
    up_all
    ;;
  ps)
    ps_all
    ;;
  -h|--help|help|"")
    usage
    ;;
  *)
    echo "Unknown command: $ACTION" >&2
    usage
    exit 1
    ;;
esac
