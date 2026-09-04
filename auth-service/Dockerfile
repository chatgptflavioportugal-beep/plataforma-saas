# syntax=docker/dockerfile:1
# Produção — build multi-stage. Para desenvolvimento local (hot-reload), use
# Dockerfile.dev via docker-compose.
# Contexto de build precisa ser a raiz do repositório (ex.: `docker build -f
# auth-service/Dockerfile .`), pois este Dockerfile instala libs/platform-database-quarkus
# antes de compilar o serviço.

# --- Build stage ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /libs/platform-database-quarkus
COPY libs/platform-database-quarkus/pom.xml .
COPY libs/platform-database-quarkus/src ./src
RUN mvn install -N -DskipTests -q

WORKDIR /libs/platform-tenant-security-quarkus
COPY libs/platform-tenant-security-quarkus/pom.xml .
COPY libs/platform-tenant-security-quarkus/src ./src
RUN mvn install -N -DskipTests -q

WORKDIR /app
COPY auth-service/pom.xml .
RUN mvn -B dependency:go-offline -q
COPY auth-service/src ./src
RUN mvn -B package -DskipTests -q

# --- Runtime stage ---
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=build --chown=app:app /app/target/quarkus-app/ ./
USER app

ENV PORT=8082
EXPOSE 8082

HEALTHCHECK --interval=30s --timeout=3s --start-period=20s --retries=3 \
  CMD wget -qO- "http://127.0.0.1:${PORT}/q/health/ready" || exit 1

CMD ["sh", "-c", "java -Dquarkus.http.host=0.0.0.0 -Dquarkus.http.port=${PORT} -jar quarkus-run.jar"]
