# syntax=docker/dockerfile:1
# Produção — build multi-stage. Para desenvolvimento local (hot-reload), use
# Dockerfile.dev via docker-compose.

# --- Build stage ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline -q
COPY src ./src
RUN mvn -B package -DskipTests -q

# --- Runtime stage ---
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=build --chown=app:app /app/target/quarkus-app/ ./
USER app

ENV PORT=8084
EXPOSE 8084

HEALTHCHECK --interval=30s --timeout=3s --start-period=20s --retries=3 \
  CMD wget -qO- "http://127.0.0.1:${PORT}/q/health/ready" || exit 1

CMD ["sh", "-c", "java -Dquarkus.http.host=0.0.0.0 -Dquarkus.http.port=${PORT} -jar quarkus-run.jar"]
