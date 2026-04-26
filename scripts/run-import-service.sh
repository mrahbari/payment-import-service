#!/usr/bin/env bash
# Run import-service (Node). Run scripts/build.sh first, or use npm run dev for TypeScript watch.
# Usage: ./scripts/run-import-service.sh
# Env: PORT, PAYMENT_API_BASE_URL
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

export PORT="${PORT:-3000}"
"$ROOT/scripts/kill-dev-ports.sh" "${PORT}"

cd "$ROOT/import-service"
export PAYMENT_API_BASE_URL="${PAYMENT_API_BASE_URL:-http://localhost:8080}"

if [[ ! -d node_modules ]]; then
  echo "==> Installing npm dependencies"
  npm install
fi

if [[ ! -f dist/server.js ]]; then
  echo "==> dist/ missing; run: ./scripts/build.sh (import-service part) or npm run build"
  npm run build
fi

exec npm start
