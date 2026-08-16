#!/bin/bash
# GitInsight AI — local .env bootstrap for docker compose.
#
# Creates .env at the repo root from docker/env.example (docker compose reads it
# automatically) and fills in a random JWT_SECRET when the file is missing or
# the secret is blank. It never writes real secrets — only local development
# values — so production credentials are always supplied separately.
#
# Usage:  sh scripts/init-env.sh   (or make it executable and run ./scripts/init-env.sh)

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="$ROOT/.env"
TEMPLATE="$ROOT/docker/env.example"

if [ ! -f "$TEMPLATE" ]; then
  echo "error: docker/env.example not found — run this from the repo root." >&2
  exit 1
fi

if [ -f "$ENV_FILE" ]; then
  if grep -q '^JWT_SECRET=.\+' "$ENV_FILE"; then
    echo ".env already exists with JWT_SECRET set — leaving it untouched."
    echo "Next: docker compose up --build"
    exit 0
  fi
  echo ".env exists but JWT_SECRET is blank — generating one and appending it."
  printf '\nJWT_SECRET=%s\n' "$(openssl rand -hex 32)" >> "$ENV_FILE"
else
  cp "$TEMPLATE" "$ENV_FILE"
  echo ".env created from docker/env.example — generating JWT_SECRET."
  printf '\nJWT_SECRET=%s\n' "$(openssl rand -hex 32)" >> "$ENV_FILE"
fi

echo "Done. Add GITHUB_TOKEN / GEMINI_API_KEY / OAuth credentials to .env if you have them."
echo "Next: docker compose up --build"
