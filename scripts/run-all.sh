#!/usr/bin/env bash
# Start PostgresSQL (Docker), payment-service, then import-service. Ctrl+C stops background Java/Maven.
# After payment-service is healthy, applies docs/seed.sql if GET .../contracts/by-number/CNT-1001 fails (demo data).
# Prerequisites: Docker, Maven, Node.js, curl.
# Usage: ./scripts/run-all.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

echo "==> Stopping anything still bound to dev ports (3000, 8080)"
"$ROOT/scripts/kill-dev-ports.sh"

export DATABASE_URL="${DATABASE_URL:-jdbc:postgresql://127.0.0.1:5432/payment_db}"
export DATABASE_USER="${DATABASE_USER:-user}"
export DATABASE_PASSWORD="${DATABASE_PASSWORD:-pass}"
export PAYMENT_API_BASE_URL="${PAYMENT_API_BASE_URL:-http://127.0.0.1:8080}"
# Trim trailing slash for safe URL joins
PAYMENT_API_BASE_URL="${PAYMENT_API_BASE_URL%/}"
export PORT="${PORT:-3000}"
export HOST="${HOST:-0.0.0.0}"
export SERVER_PORT="${SERVER_PORT:-8080}"

PAY_PID=""

cleanup() {
  if [[ -n "${PAY_PID:-}" ]]; then
    kill "$PAY_PID" 2>/dev/null || true
    if command -v pkill >/dev/null 2>&1; then
      pkill -P "$PAY_PID" 2>/dev/null || true
    fi
  fi
  local pids
  pids="$(jobs -p 2>/dev/null || true)"
  if [[ -n "${pids:-}" ]]; then
    kill $pids 2>/dev/null || true
    wait $pids 2>/dev/null || true
  fi
}
trap cleanup EXIT INT TERM

echo "==> Starting PostgresSQL (docker compose)"
if docker compose version >/dev/null 2>&1; then
  if docker compose up -d --wait postgres 2>/dev/null; then
    echo "    Postgres is healthy."
  else
    docker compose up -d postgres
    echo "    Waiting for Postgres..."
    for _ in $(seq 1 60); do
      if docker compose exec -T postgres pg_isready -U user -d payment_db >/dev/null 2>&1; then
        echo "    Postgres is ready."
        break
      fi
      sleep 1
    done
  fi
else
  echo "ERROR: 'docker compose' not found. Start PostgresSQL yourself and run run-payment-service.sh + run-import-service.sh in two terminals."
  exit 1
fi

echo "==> Starting payment-service (background, logging to payment-service/target/logs/payment-service.log)"
mkdir -p "$ROOT/payment-service/target/logs"
(
  cd "$ROOT/payment-service"
  mvn spring-boot:run -B
) > "$ROOT/payment-service/target/logs/payment-service.log" 2>&1 &
PAY_PID=$!

echo "==> Waiting for http://127.0.0.1:8080/actuator/health"
for _ in $(seq 1 120); do
  if curl -sf "http://127.0.0.1:8080/actuator/health" >/dev/null 2>&1; then
    echo "    payment-service is up."
    break
  fi
  sleep 2
done
if ! curl -sf "http://127.0.0.1:8080/actuator/health" >/dev/null 2>&1; then
  echo "ERROR: payment-service did not become healthy in time."
  echo "Check $ROOT/payment-service/target/logs/payment-service.log for details."
  exit 1
fi

echo "==> Demo data (docs/seed.sql) if contract CNT-1001 is missing"
# Retry: right after /health the API can still 404; avoid applying seed when data already exists in DB.
has_contract=0
for _ in $(seq 1 30); do
  if curl -sf "${PAYMENT_API_BASE_URL}/api/v1/contracts/by-number/CNT-1001" >/dev/null 2>&1; then
    has_contract=1
    break
  fi
  sleep 1
done
if [[ "$has_contract" -eq 1 ]]; then
  echo "    Contract CNT-1001 already present; skipping seed."
else
  echo "    Applying seed (idempotent)..."
  docker compose exec -T postgres psql -U user -d payment_db -v ON_ERROR_STOP=1 < "$ROOT/docs/seed.sql"
  
  # Verification loop after seed
  for _ in $(seq 1 10); do
    if curl -sf "${PAYMENT_API_BASE_URL}/api/v1/contracts/by-number/CNT-1001" >/dev/null 2>&1; then
      has_contract=1
      break
    fi
    sleep 1
  done

  if [[ "$has_contract" -eq 0 ]]; then
    echo "ERROR: CNT-1001 still not visible after seed (check docs/seed.sql, Postgres, and API logs)."
    exit 1
  fi
  echo "    Seed applied; CNT-1001 is available for imports."
fi

echo "==> Starting import-service (foreground on port ${PORT})"
# Always ensure node_modules is healthy and dist is up to date
(
  cd "$ROOT/import-service"
  if [[ ! -d "node_modules/express" ]]; then
    echo "    Installing dependencies..."
    npm install
  fi
  echo "    Building..."
  npm run build
)

echo "==> Starting import-service (background, logging to import-service/logs/import-service.log)"
mkdir -p "$ROOT/import-service/logs"
cd "$ROOT/import-service"
npm start > "$ROOT/import-service/logs/import-service.log" 2>&1 &
IMPORT_PID=$!

echo "==> Waiting for http://127.0.0.1:${PORT}/"
for _ in $(seq 1 30); do
  if curl -sf "http://127.0.0.1:${PORT}/" >/dev/null 2>&1; then
    echo "    import-service is up."
    break
  fi
  sleep 1
done

if ! curl -sf "http://127.0.0.1:${PORT}/" >/dev/null 2>&1; then
  echo "ERROR: import-service failed to start. Check $ROOT/import-service/logs/import-service.log"
  kill "$IMPORT_PID" 2>/dev/null || true
  exit 1
fi

echo "----------------------------------------------------------------"
echo "  All services are UP!"
echo "  Import UI: http://127.0.0.1:${PORT}/payments/import"
echo "  Payment API: http://127.0.0.1:8080/"
echo ""
echo "  LOGS (tail -f):"
echo "    Java API:   tail -f payment-service/target/logs/payment-service.log"
echo "    Import Svc: tail -f import-service/logs/import-service.log"
echo "----------------------------------------------------------------"

wait "$IMPORT_PID"
