#!/usr/bin/env bash
# Remove build outputs for payment-service and import-service; stop local dev servers on 3000/8080.
# Does NOT remove import-service node_modules (use CLEAN_NODE_MODULES=1 to delete them).
# Usage: ./scripts/cleanup.sh
# Git Bash, WSL, macOS, Linux (fuser or lsof required to free ports; install one if missing).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "==> Stopping dev listeners on 3000 (import) and 8080 (payment) [if any]"
"$ROOT/scripts/kill-dev-ports.sh" 3000 8080

echo "==> Stopping PostgresSQL and removing volumes (docker compose down -v)"
if docker compose version >/dev/null 2>&1; then
  docker compose down -v --remove-orphans
else
  echo "    (skip docker compose; command not found)"
fi

echo "==> Maven clean: payment-service"
( cd "$ROOT/payment-service" && mvn -q clean )

echo "==> Removing import-service dist/ (compiled JS)"
rm -rf "$ROOT/import-service/dist"

echo "==> Removing log files"
rm -rf "$ROOT/import-service/logs"
rm -f "$ROOT"/*.log

if [[ "${CLEAN_NODE_MODULES:-}" == "1" ]]; then
  echo "==> Removing import-service node_modules/ (CLEAN_NODE_MODULES=1)"
  rm -rf "$ROOT/import-service/node_modules"
else
  echo "    (skip node_modules; set CLEAN_NODE_MODULES=1 to remove)"
fi

echo "==> Cleanup done"
echo "    PostgresSQL:      Stopped and volumes removed"
echo "    payment-service: target/ removed (mvn clean)"
echo "    import-service:  dist/ removed"
echo "    Rebuild: ./scripts/build.sh"
