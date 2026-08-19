# ============================================================================
# GitInsight-AI — Java service images.
#
# Build the whole Maven reactor ONCE (modules share the `common` dependency),
# then copy each service's fat jar into its own slim runtime image.
#
#   docker build --target eureka-server      -t gitinsight/eureka-server .
#   docker build --target github-service     -t gitinsight/github-service .
#   docker build --target analytics-service  -t gitinsight/analytics-service .
#   docker build --target auth-service       -t gitinsight/auth-service .
#
# Or just `docker compose up --build` which wires everything together.
# ============================================================================

# ---------- Build stage ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY . .
# Wrapper needs network to fetch the Maven dist; the image already ships `mvn`.
RUN mvn -q -DskipTests package

# ---------- Runtime: Eureka Server (8761) ----------
FROM eclipse-temurin:21-jre AS eureka-server
WORKDIR /app
COPY --from=build /build/eureka-server/target/eureka-server-*-exec.jar app.jar
EXPOSE 8761
ENTRYPOINT ["java", "-jar", "app.jar"]

# ---------- Runtime: GitHub Service (8081) ----------
FROM eclipse-temurin:21-jre AS github-service
WORKDIR /app
COPY --from=build /build/github-service/target/github-service-*-exec.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]

# ---------- Runtime: Analytics Service (8082) ----------
FROM eclipse-temurin:21-jre AS analytics-service
WORKDIR /app
COPY --from=build /build/analytics-service/target/analytics-service-*-exec.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]

# ---------- Runtime: Auth Service (8083) ----------
FROM eclipse-temurin:21-jre AS auth-service
WORKDIR /app
COPY --from=build /build/auth-service/target/auth-service-*-exec.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]

# ---------- Runtime: API Gateway (8080) ----------
FROM eclipse-temurin:21-jre AS api-gateway
WORKDIR /app
COPY --from=build /build/api-gateway/target/api-gateway-*-exec.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
