# syntax=docker/dockerfile:1
# Produção — build estático (remoteEntry.js incluso) servido por Nginx
# (não-root). Para desenvolvimento local, use Dockerfile.dev via docker-compose.

# --- Build stage ---
FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .

ARG VITE_PDF_API_URL
ENV VITE_PDF_API_URL=${VITE_PDF_API_URL}
RUN npm run build

# --- Runtime stage ---
FROM nginxinc/nginx-unprivileged:1.27-alpine AS runtime
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf.template /etc/nginx/templates/default.conf.template

ENV NGINX_ENVSUBST_FILTER=PORT \
    PORT=8080
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
  CMD wget -qO- "http://127.0.0.1:${PORT}/remoteEntry.js" || exit 1
