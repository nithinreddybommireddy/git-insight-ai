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

# Start Redis (used by auth-service for the login rate limiter and OAuth state).
# Best-effort: auth-service degrades gracefully (rate limiter fails open,
# OAuth fails closed) if Redis cannot be started.
if ! redis-cli ping >/dev/null 2>&1; then
  apt-get install -y -qq redis-server >/dev/null 2>&1 || true
  service redis-server start >/dev/null 2>&1 || redis-server --daemonize yes >/dev/null 2>&1 || true
fi

# Build JARs if needed
if [ ! -f eureka-server/target/eureka-server-*-exec.jar ]; then
  echo "Building backend..."
  sh ./mvnw clean package -DskipTests -q
fi

# Start all 4 backend services in background.
# Heap/metaspace are capped so the full stack fits in small sandboxes (the
# default JVM sizing on a ~1-2GB container overcommits and the 4th service
# gets OOM-killed, taking auth-service/OAuth offline).
JVM_FLAGS="-Xmx256m -XX:MaxMetaspaceSize=160m -XX:+ExitOnOutOfMemoryError"
echo "Starting backend services..."
setsid java $JVM_FLAGS -jar eureka-server/target/eureka-server-*-exec.jar > /tmp/eureka.log 2>&1 &
setsid java $JVM_FLAGS -jar analytics-service/target/analytics-service-*-exec.jar > /tmp/analytics.log 2>&1 &
setsid java $JVM_FLAGS -jar github-service/target/github-service-*-exec.jar > /tmp/github.log 2>&1 &
setsid java $JVM_FLAGS -jar auth-service/target/auth-service-*-exec.jar > /tmp/auth.log 2>&1 &
echo "Backend services starting in background (will take ~25s to be ready)"

# Start frontend dev server in foreground
cd frontend
bun run dev --host 0.0.0.0 --port "${PORT:-5173}"
