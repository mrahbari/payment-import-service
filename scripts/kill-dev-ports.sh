#!/usr/bin/env bash
# Free TCP ports used by this repo so dev servers can bind (avoids EADDRINUSE).
# Default ports: 3000 (import-service), 8080 (payment-service).
# Usage: ./scripts/kill-dev-ports.sh [PORT ...]

if [[ $# -eq 0 ]]; then
  PORTS=(3000 8080)
else
  PORTS=("$@")
fi

echo "==> Freeing port(s): ${PORTS[*]}"

for p in "${PORTS[@]}"; do
  if command -v fuser >/dev/null 2>&1; then
    fuser -k "${p}/tcp" 2>/dev/null || true
  elif command -v lsof >/dev/null 2>&1; then
    pids="$(lsof -ti:"$p" -sTCP:LISTEN 2>/dev/null || true)"
    if [[ -n "${pids:-}" ]]; then
      # shellcheck disable=SC2086
      kill -9 $pids 2>/dev/null || true
    fi
  else
    echo "    WARN: install fuser or lsof to free port $p on this OS." >&2
  fi
done
