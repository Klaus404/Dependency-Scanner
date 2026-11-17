#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

command -v docker >/dev/null 2>&1 || {
  printf '%s\n' "Docker is required but was not found." >&2
  exit 1
}

docker compose up --build -d

printf '%s\n' "Dependency scanner started."
printf '%s\n' "API:       http://localhost:8080"
printf '%s\n' "RabbitMQ:  http://localhost:15672 (scanner/scanner)"
