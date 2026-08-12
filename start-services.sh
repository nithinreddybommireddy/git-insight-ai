#!/bin/bash
# GitInsight AI - Microservices Startup Script
# Starts Eureka Server, GitHub Service, and Analytics Service

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# JWT_SECRET is required (no fallback in the app). Generate a fresh random one
# for this run unless the developer already exported one.
if [ -z "${JWT_SECRET:-}" ]; then
  export JWT_SECRET="dev-$(openssl rand -hex 32)"
  echo "JWT_SECRET not set — generated a random one for this run."
fi

# Start PostgreSQL if not running
echo "=== Starting PostgreSQL ==="
pg_isready -q 2>/dev/null || pg_ctlcluster 14 main start 2>/dev/null || echo "PostgreSQL already running"

# Build the project (skip tests for speed)
echo "=== Building all modules ==="
./mvnw clean package -DskipTests -q

# Start Eureka Server in background
echo "=== Starting Eureka Server (port 8761) ==="
java -jar eureka-server/target/eureka-server-*.jar &
EUREKA_PID=$!
echo "Eureka PID: $EUREKA_PID"

# Wait for Eureka to be ready
echo "=== Waiting for Eureka Server... ==="
for i in $(seq 1 30); do
    if curl -s http://localhost:8761/actuator/health 2>/dev/null | grep -q "UP"; then
        echo "Eureka Server is ready!"
        break
    fi
    sleep 2
done

# Start Analytics Service in background
echo "=== Starting Analytics Service (port 8082) ==="
java -jar analytics-service/target/analytics-service-*.jar &
ANALYTICS_PID=$!
echo "Analytics PID: $ANALYTICS_PID"

# Start GitHub Service in foreground (this will be the preview)
echo "=== Starting GitHub Service (port 8081) ==="
java -jar github-service/target/github-service-*.jar
