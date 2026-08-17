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

**Local development:**

```bash
cp docker/env.example .env          # set JWT_SECRET (required), GITHUB_TOKEN (recommended)
docker compose up --build -d
```

- Frontend: `http://<host>:5173` (nginx serves the SPA and proxies `/api/auth` + `/api/recruiter` to auth-service, everything else to github-service — same-origin, so **no CORS needed**).
- Services register with Eureka; the frontend never talks to services directly.
- Set `GITHUB_OAUTH_REDIRECT_URI` / `OAUTH_FRONTEND_REDIRECT_URI` to the real public URLs if you enable GitHub login.

**Public production:** never use the dev file verbatim — it publishes PostgreSQL,
Redis, Eureka and all three services to the host, which means the internet can
reach them. Use the production override instead, which keeps every service on
the private Docker network and exposes only the frontend (nginx) on port 80:

```bash
cp docker/env.example .env
# Set the production secrets (see table below): AUTH_COOKIE_SECURE=true,
# JWT_SECRET, POSTGRES_PASSWORD, REDIS_PASSWORD, INTERNAL_API_KEY, OAuth URLs.
docker compose -f docker-compose.yml -f docker-compose.prod.yml up --build -d
```

Topology with the override (requires Docker Compose v2.24+):

```
Internet → HTTPS :443 → nginx (frontend + /api proxy) → private network
   ├── auth-service
   ├── github-service
   ├── analytics-service
   ├── eureka-server
   ├── redis
   └── postgres
```

TLS terminates at your load balancer / ingress or in nginx; port 80 is the
entry point the override exposes. Verify nothing internal is public after
deploy:

```bash
# All of these must fail to connect from the internet:
#   :5432  :6379  :8761  :8081  :8082  :8083
# Only :443 (and optionally :80 → redirect) is reachable.
```

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
| `JWT_SECRET` | ✅ | 64+ random bytes in prod — both auth and github refuse to boot without it |
| `AUTH_COOKIE_SECURE` | ✅ (prod) | must be `true` on HTTPS so session cookies get the Secure flag |
| `POSTGRES_PASSWORD` | ✅ (prod) | strong random — never the dev default `postgres` |
| `REDIS_PASSWORD` | ✅ (prod) | strong random |
| `INTERNAL_API_KEY` | ✅ (prod) | shared secret auth→github (`/api/ai/job-match`); same value on both |
| `GITHUB_TOKEN` | recommended | 5,000 GitHub req/hr instead of 60 |
| `GEMINI_API_KEY` | optional | live AI features; template fallback without it |
| `GITHUB_CLIENT_ID` / `GITHUB_CLIENT_SECRET` | optional | GitHub OAuth login |
| `GITHUB_OAUTH_REDIRECT_URI` | prod | exact HTTPS callback URL of auth-service |
| `OAUTH_FRONTEND_REDIRECT_URI` | prod | exact HTTPS frontend URL |
| `CORS_ALLOWED_ORIGINS` | Option B only | frontend origin allowlist |
| `VITE_API_BASE` | Option B only | backend origin baked into the bundle |

## Smoke-test after deploy

With the production override, everything goes through nginx (no direct service
ports are reachable):

```bash
curl -s https://<host>/api/health                      # github-service up (via nginx)
curl -s "https://<host>/api/github/torvalds/score" | head -c 300   # analysis works
curl -s -X POST https://<host>/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"a@b.co","password":"Str0ng!Pass","name":"A"}'            # auth works
# Then log in, view the dashboard, and (if configured) complete GitHub OAuth.
```

Full manual checklist after deploy: register/login → GitHub OAuth → refresh
browser (session persists) → analyze a developer → AI analysis → recruiter
login → candidate/job matching → logout → confirm protected pages are blocked
→ confirm a normal USER gets 403 on recruiter endpoints → confirm :5432/:6379/
:8761/:8081/:8082/:8083 are NOT reachable from the internet.
