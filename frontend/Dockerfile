# ============================================================================
# GitInsight-AI — frontend image.
#
# Build context is the REPO ROOT (so nginx.conf from docker/ can be copied):
#   docker build -f frontend/Dockerfile -t gitinsight/frontend .
# ============================================================================

# ---------- Build stage: Vite app ----------
FROM oven/bun:1 AS build
WORKDIR /app
COPY frontend/package.json frontend/bun.lock ./
RUN bun install --frozen-lockfile
COPY frontend/ ./
# package.json "build" runs `tsc -b && vite build` → dist/
RUN bun run build

# ---------- Runtime: nginx (SPA + API proxy) ----------
FROM nginx:1.27-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY docker/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
HEALTHCHECK --interval=10s --timeout=3s --retries=5 \
  CMD wget -q -O /dev/null http://localhost/ || exit 1
