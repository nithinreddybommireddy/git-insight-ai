#!/bin/bash
# GitInsight AI - Dev Startup
# Starts backend services in background, then frontend in foreground

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# JWT_SECRET is required (no fallback in the app). Generate a fresh random one
# for this dev run unless the developer already exported one.
if [ -z "${JWT_SECRET:-}" ]; then
  export JWT_SECRET="dev-$(openssl rand -hex 32)"
  echo "JWT_SECRET not set — generated a random one for this run."
fi

# Start PostgreSQL
pg_isready -q 2>/dev/null || pg_ctlcluster 14 main start 2>/dev/null || true

# Build JARs if needed
if [ ! -f eureka-server/target/eureka-server-*-exec.jar ]; then
  echo "Building backend..."
  sh ./mvnw clean package -DskipTests -q
fi

# Start all 4 backend services in background
echo "Starting backend services..."
setsid java -jar eureka-server/target/eureka-server-*-exec.jar > /tmp/eureka.log 2>&1 &
setsid java -jar analytics-service/target/analytics-service-*-exec.jar > /tmp/analytics.log 2>&1 &
setsid java -jar github-service/target/github-service-*-exec.jar > /tmp/github.log 2>&1 &
setsid java -jar auth-service/target/auth-service-*-exec.jar > /tmp/auth.log 2>&1 &
echo "Backend services starting in background (will take ~25s to be ready)"

# Start frontend dev server in foreground
cd frontend
bun run dev --host 0.0.0.0 --port "${PORT:-5173}"
