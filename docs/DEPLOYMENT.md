# Deployment — GitInsight-AI

GitInsight-AI is a **Java 21 + Spring Cloud (Eureka) microservice stack with a
PostgreSQL database** and a Vite/React frontend. Pick the topology that matches
your host.

## Option A — Docker Compose (recommended, full stack on one host)

Everything needed to run the whole system is in the repo: a root `Dockerfile`
(multi-stage — one Maven build, a slim runtime image per service), a
`frontend/Dockerfile` (Bun build → nginx that also proxies `/api`), and a
`docker-compose.yml` that wires PostgreSQL + Eureka + all three services + the
frontend.

Works on any container host: a VPS (Docker Engine), Render (Blueprint →
"Run with Docker"), Railway, Fly.io, or a VM with docker compose.

```bash
cp docker/env.example .env          # set JWT_SECRET (required), GITHUB_TOKEN (recommended)
docker compose up --build -d
```

- Frontend: `http://<host>:5173` (nginx serves the SPA and proxies `/api/auth` + `/api/recruiter` to auth-service, everything else to github-service — same-origin, so **no CORS needed**).
- Services register with Eureka; the frontend never talks to services directly.
- Set `GITHUB_OAUTH_REDIRECT_URI` / `OAUTH_FRONTEND_REDIRECT_URI` to the real public URLs if you enable GitHub login.

## Option B — Static frontend + containerized backend (split origins)

Frontend hosting can be anything that serves static files (Netlify, Vercel,
Cloudflare Pages, S3+CDN, or Freebuff hosting); the backend stack runs via
Option A's compose file on a container host.

1. Build the frontend with the backend origin baked in:
   ```bash
   cd frontend && VITE_API_BASE=https://api.example.com bun run build   # → dist/
   ```
   (Without `VITE_API_BASE` the bundle calls same-origin `/api/...`, which only
   works when a gateway serves both.)
2. Backend: `docker compose up --build -d` on the API host, with the same
   `VITE_API_BASE` origin added to CORS:
   ```bash
   CORS_ALLOWED_ORIGINS=https://app.example.com docker compose up --build -d
   ```
3. Put a TLS termination + reverse proxy (`https://api.example.com`) in front
   of the backend that forwards `/api` to the gateway port.

## Option C — Freebuff hosting (frontend only)

Freebuff hosting builds static output (`dist/`) on a Node-only image — it
**cannot run the Java services or PostgreSQL**, so a Freebuff-hosted frontend
is only useful together with a backend running under Option B. The repo's
preview/dev commands already boot the full Java stack in the sandbox, but that
is not a production deployment.

## Required / recommended env (prod)

| Variable | Required | Notes |
|---|---|---|
| `JWT_SECRET` | ✅ | 32+ random bytes — both auth and github refuse to boot without it |
| `SPRING_DATASOURCE_PASSWORD` | ✅ | PostgreSQL password (compose default `postgres` — change it) |
| `GITHUB_TOKEN` | recommended | 5,000 GitHub req/hr instead of 60 |
| `GEMINI_API_KEY` | optional | live AI features; template fallback without it |
| `GITHUB_CLIENT_ID` / `GITHUB_CLIENT_SECRET` | optional | GitHub OAuth login |
| `CORS_ALLOWED_ORIGINS` | Option B only | frontend origin allowlist |
| `VITE_API_BASE` | Option B only | backend origin baked into the bundle |

## Smoke-test after deploy

```bash
curl -s https://<host>/api/health                      # github-service up
curl -s https://<host>:8083/api/health                 # auth-service up
curl -s "https://<host>:8081/api/github/torvalds/score" | head -c 300   # analysis works
curl -s -X POST https://<host>:8083/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"a@b.co","password":"Str0ng!Pass","name":"A"}'            # auth works
# Then log in, view the dashboard, and (if configured) complete GitHub OAuth.
```
