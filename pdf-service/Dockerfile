# syntax=docker/dockerfile:1
# Produção — sem --reload, honra $PORT (Cloud Run), usuário não-root.

# --- Build stage ---
FROM python:3.11-slim AS build
WORKDIR /app
COPY libs/platform-module-security-python /libs/platform-module-security-python
COPY libs/platform-database-python /libs/platform-database-python
COPY pdf-service/requirements.txt .
RUN pip install --no-cache-dir --user -r requirements.txt

# --- Runtime stage ---
FROM python:3.11-slim AS runtime
WORKDIR /app
RUN adduser --disabled-password --gecos "" appuser
COPY --from=build /root/.local /home/appuser/.local
COPY --from=build /libs/platform-module-security-python /libs/platform-module-security-python
COPY --from=build /libs/platform-database-python /libs/platform-database-python
COPY pdf-service/ .
RUN chown -R appuser:appuser /app /libs/platform-module-security-python /libs/platform-database-python
USER appuser

ENV PATH=/home/appuser/.local/bin:$PATH \
    PORT=8001
EXPOSE 8001

HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
  CMD python -c "import os,urllib.request; urllib.request.urlopen('http://127.0.0.1:'+os.environ.get('PORT','8001')+'/health')" || exit 1

CMD ["sh", "-c", "uvicorn main:app --host 0.0.0.0 --port ${PORT}"]
