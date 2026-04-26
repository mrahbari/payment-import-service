#!/usr/bin/env bash
# Run payment-service (Spring Boot). Expects PostgreSQL reachable (see docker-compose).
# Usage: ./scripts/run-payment-service.sh
# Env: DATABASE_URL, DATABASE_USER, DATABASE_PASSWORD, SERVER_PORT (optional)
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

export SERVER_PORT="${SERVER_PORT:-8080}"
"$ROOT/scripts/kill-dev-ports.sh" "${SERVER_PORT}"

cd "$ROOT/payment-service"

export DATABASE_URL="${DATABASE_URL:-jdbc:postgresql://localhost:5432/payment_db}"
export DATABASE_USER="${DATABASE_USER:-user}"
export DATABASE_PASSWORD="${DATABASE_PASSWORD:-pass}"

exec mvn spring-boot:run
