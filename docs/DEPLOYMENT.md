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
JWT_SECRET=<64+ random bytes — generate with: openssl rand -hex 32>

# PostgreSQL — copy the JDBC URL from Railway's Postgres service Variables tab.
# Do NOT pass Railway's raw DATABASE_URL directly — it uses postgres:// not jdbc:postgresql://
SPRING_DATASOURCE_URL=jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}
SPRING_DATASOURCE_USERNAME=${{Postgres.PGUSER}}
SPRING_DATASOURCE_PASSWORD=${{Postgres.PGPASSWORD}}

# Redis — copy the actual variable names from Railway's Redis service Variables tab.
# Railway Redis typically provides: REDISHOST, REDISPORT, REDISPASSWORD, REDIS_URL.
# Use the private URL when available:
REDIS_HOST=${{Redis.REDISHOST}}
REDIS_PORT=${{Redis.REDISPORT}}
REDIS_PASSWORD=${{Redis.REDISPASSWORD}}

# Eureka — copy the actual Private Domain from the Eureka service's Settings tab.
EUREKA_URL=http://<eureka-actual-private-domain>:8761/eureka/

# GitHub OAuth — callback goes through Vercel proxy (same-origin from browser).
GITHUB_CLIENT_ID=<OAuth client ID>
GITHUB_CLIENT_SECRET=<OAuth client secret>
GITHUB_OAUTH_REDIRECT_URI=https://git-insight-ai-one.vercel.app/api/auth/oauth/github/callback
OAUTH_FRONTEND_REDIRECT_URI=https://git-insight-ai-one.vercel.app/auth/callback

# Internal service-to-service — copy the actual Private Domain from github-service.
GITHUB_SERVICE_URL=http://<github-service-actual-private-domain>:8081
INTERNAL_API_KEY=<shared secret — same value on both auth and github service>

# Cookie security — Vercel /api proxy makes this same-origin.
AUTH_COOKIE_SECURE=true
AUTH_COOKIE_SAME_SITE=Lax
JWT_MIN_SECRET_BYTES=64
```

**GitHub Service:**
```
JWT_SECRET=<same as auth-service — 64+ random bytes>

# PostgreSQL — each service needs its own database.
SPRING_DATASOURCE_URL=jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}
SPRING_DATASOURCE_USERNAME=${{Postgres.PGUSER}}
SPRING_DATASOURCE_PASSWORD=${{Postgres.PGPASSWORD}}

# Redis
REDIS_HOST=${{Redis.REDIS_PRIVATE_URL}}
REDIS_PORT=6379
REDIS_PASSWORD=${{Redis.REDIS_PASSWORD}}

# Eureka — copy the actual Private Domain from the Eureka service.
EUREKA_URL=http://<eureka-actual-private-domain>:8761/eureka/

# External API keys
GITHUB_TOKEN=<GitHub PAT>
GEMINI_API_KEY=<Gemini API key>

# Internal — must match auth-service.
INTERNAL_API_KEY=<same as auth-service>
JWT_MIN_SECRET_BYTES=64
```

**Analytics Service (placeholder):**
```
# Currently a placeholder — only exposes health endpoint.
# Deploy when real analytics endpoints are implemented.
SPRING_DATASOURCE_URL=jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}
SPRING_DATASOURCE_USERNAME=${{Postgres.PGUSER}}
SPRING_DATASOURCE_PASSWORD=${{Postgres.PGPASSWORD}}
EUREKA_URL=http://<eureka-actual-private-domain>:8761/eureka/
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
  Only set `None` if frontend and API are genuinely cross-origin (no Vercel proxy).
- **Only the API Gateway should have a public Railway domain.**
  All other services (auth, github, analytics, eureka, postgres, redis) must
  remain private — accessible only via Railway private networking.
- When setting `EUREKA_URL`, `GITHUB_SERVICE_URL`, etc., copy the actual
  Private Domain from each service's Railway Settings tab — the hostnames
  are generated and may not match the service name exactly.

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

With the Vercel proxy architecture, the browser calls same-origin `/api/*`
which Vercel rewrites to the Railway Gateway:

```bash
# Gateway health (direct — use the actual Gateway public domain)
curl -i https://<gateway-domain>/actuator/health

# Through Vercel proxy (use the Vercel frontend URL)
curl -s "https://git-insight-ai-one.vercel.app/api/github/torvalds/score" | head -c 300

curl -s -X POST https://git-insight-ai-one.vercel.app/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"a@b.co","password":"Str0ng!Pass","name":"A"}'
# Then log in, view the dashboard, and (if configured) complete GitHub OAuth.
```

Full manual checklist after deploy: register/login → GitHub OAuth → refresh
browser (session persists) → analyze a developer → AI analysis → recruiter
login → candidate/job matching → logout → confirm protected pages are blocked
→ confirm a normal USER gets 403 on recruiter endpoints → confirm :5432/:6379/
:8761/:8081/:8082/:8083 are NOT reachable from the internet.
