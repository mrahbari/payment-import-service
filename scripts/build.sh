#!/usr/bin/env bash
# Build payment-service (Maven) and import-service (TypeScript → dist/).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "==> Building payment-service"
(
  cd "$ROOT/payment-service"
  mvn -B clean package -DskipTests
)

echo "==> Building import-service"
(
  cd "$ROOT/import-service"
  if [[ -f package-lock.json ]]; then
    npm ci
  else
    npm install
  fi
  npm run build
)

echo "==> Build finished successfully."
