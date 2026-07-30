# syntax=docker/dockerfile:1
# Produção — build estático servido por Nginx (não-root). Para desenvolvimento
# local (hot-reload + Module Federation dev), use Dockerfile.dev via docker-compose.

# --- Build stage ---
FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .

# Variáveis VITE_* são embutidas no bundle em build-time — precisam ser
# passadas como --build-arg no `docker build`.
ARG VITE_SUPABASE_URL
ARG VITE_SUPABASE_ANON_KEY
ARG VITE_AUTH_API_URL
ARG VITE_CATALOG_API_URL
ARG VITE_PROFILE_API_URL
ARG VITE_SUBSCRIPTION_API_URL
ARG VITE_ADMIN_APP_URL
ARG VITE_PDF_REMOTE_URL
ARG VITE_WHATSAPP_REMOTE_URL
ENV VITE_SUPABASE_URL=${VITE_SUPABASE_URL} \
    VITE_SUPABASE_ANON_KEY=${VITE_SUPABASE_ANON_KEY} \
    VITE_AUTH_API_URL=${VITE_AUTH_API_URL} \
    VITE_CATALOG_API_URL=${VITE_CATALOG_API_URL} \
    VITE_PROFILE_API_URL=${VITE_PROFILE_API_URL} \
    VITE_SUBSCRIPTION_API_URL=${VITE_SUBSCRIPTION_API_URL} \
    VITE_ADMIN_APP_URL=${VITE_ADMIN_APP_URL} \
    VITE_PDF_REMOTE_URL=${VITE_PDF_REMOTE_URL} \
    VITE_WHATSAPP_REMOTE_URL=${VITE_WHATSAPP_REMOTE_URL}
RUN npm run build

# --- Runtime stage ---
FROM nginxinc/nginx-unprivileged:1.27-alpine AS runtime
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf.template /etc/nginx/templates/default.conf.template

# Restringe o envsubst do entrypoint oficial do nginx só a $PORT, para não
# atropelar as variáveis próprias do nginx (ex.: $uri) no template.
ENV NGINX_ENVSUBST_FILTER=PORT \
    PORT=8080
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
  CMD wget -qO- "http://127.0.0.1:${PORT}/" || exit 1
