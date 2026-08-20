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

## Option B — Vercel frontend + Railway backend (recommended split-origin)

**Architecture:** Vercel serves the React SPA and proxies `/api/*` to the
Railway Gateway via `vercel.json` rewrites. The browser makes same-origin
requests (`https://app.example.com/api/...`) — no cross-origin cookies, no
CORS issues, no complex CSRF handling.

### Vercel setup

1. `frontend/vercel.json` is pre-configured to rewrite `/api/:path*` to
   Railway. Update the destination URL to your actual Railway Gateway domain:
   ```json
   {
     "rewrites": [
       {
         "source": "/api/:path*",
         "destination": "https://<your-gateway>.up.railway.app/api/:path*"
       }
     ]
   }
   ```
2. Build the frontend with `VITE_API_BASE=` empty (same-origin proxy):
   ```bash
   cd frontend && VITE_API_BASE= npm run build   # → dist/
   ```

### Railway setup

Deploy each service as a separate Railway service. Only the API Gateway gets
a public domain; all others use Railway private networking.

**Required Railway services:**

| Service | Public Domain | Private Domain |
|---|---|---|
| api-gateway | ✅ `https://<gateway>.up.railway.app` | `api-gateway.railway.internal` |
| auth-service | ❌ | `auth-service.railway.internal` |
| github-service | ❌ | `github-service.railway.internal` |
| eureka-server | ❌ | `eureka-server.railway.internal` |
| PostgreSQL | ❌ | *(managed Railway DB)* |
| Redis | ❌ | *(managed Railway Redis)* |

### Railway environment variables

**API Gateway:**
```
JWT_SECRET=<same as auth-service>
EUREKA_URL=http://eureka-server.railway.internal:8761/eureka/
CORS_ALLOWED_ORIGINS=https://git-insight-ai-one.vercel.app
```

**Auth Service:**
```
JWT_SECRET=<64+ random bytes>
SPRING_DATASOURCE_URL=<Railway PostgreSQL URL>
SPRING_DATASOURCE_USERNAME=<db user>
SPRING_DATASOURCE_PASSWORD=<db password>
REDIS_HOST=redis.railway.internal
REDIS_PORT=<Railway Redis port>
REDIS_PASSWORD=<Railway Redis password>
EUREKA_URL=http://eureka-server.railway.internal:8761/eureka/
GITHUB_CLIENT_ID=<OAuth client ID>
GITHUB_CLIENT_SECRET=<OAuth client secret>
GITHUB_OAUTH_REDIRECT_URI=https://git-insight-ai-one.vercel.app/api/auth/oauth/github/callback
OAUTH_FRONTEND_REDIRECT_URI=https://git-insight-ai-one.vercel.app/auth/callback
GITHUB_SERVICE_URL=http://github-service.railway.internal:8081
INTERNAL_API_KEY=<shared secret>
AUTH_COOKIE_SECURE=true
AUTH_COOKIE_SAME_SITE=None
JWT_MIN_SECRET_BYTES=64
```

**GitHub Service:**
```
JWT_SECRET=<same as auth-service>
SPRING_DATASOURCE_URL=<Railway PostgreSQL URL>
SPRING_DATASOURCE_USERNAME=<db user>
SPRING_DATASOURCE_PASSWORD=<db password>
REDIS_HOST=redis.railway.internal
REDIS_PORT=<Railway Redis port>
REDIS_PASSWORD=<Railway Redis password>
EUREKA_URL=http://eureka-server.railway.internal:8761/eureka/
GITHUB_TOKEN=<GitHub PAT>
GEMINI_API_KEY=<Gemini API key>
INTERNAL_API_KEY=<same as auth-service>
JWT_MIN_SECRET_BYTES=64
```

### GitHub OAuth configuration

Register these URLs in your GitHub OAuth App settings:

- **Homepage URL:** `https://git-insight-ai-one.vercel.app`
- **Authorization callback URL:** `https://git-insight-ai-one.vercel.app/api/auth/oauth/github/callback`

The callback goes through Vercel → Railway Gateway → Auth Service.
Auth Service sets HttpOnly cookies and redirects to `/auth/callback`.

### Railway deployment notes

- `vercel.json` rewrites make the Vercel→Railway connection **same-origin**
  from the browser's perspective. No cross-origin cookie issues.
- `AUTH_COOKIE_SECURE=true` is required because Vercel serves over HTTPS.
- `AUTH_COOKIE_SAME_SITE=Lax` works with same-origin proxy (recommended).
  Set `None` only if you need cross-origin fallback.
- Internal services (auth, github) must NOT have public Railway domains.
  Only the API Gateway is public.

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
