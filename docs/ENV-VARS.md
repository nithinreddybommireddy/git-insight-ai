# Environment Variables

All secrets and credentials are read from **environment variables** — nothing is hard-coded in the repository. `JWT_SECRET` is **required** (auth-service and github-service refuse to boot without it — there is intentionally no fallback key). Everything else has a safe default so the app boots without setup, but the values below unlock the real experience.

## Quick start

Set these in your shell, or in each service's IDE run configuration (IntelliJ: Run → Edit Configurations → Environment variables):

| Variable | Service | Default | Purpose |
|---|---|---|---|
| `SPRING_DATASOURCE_USERNAME` | all 3 DB services | `postgres` | PostgreSQL username |
| `SPRING_DATASOURCE_PASSWORD` | all 3 DB services | `postgres` | PostgreSQL password |
| `JWT_SECRET` | auth-service + github-service | **none — required** | Random 32+ byte key; signs JWTs (auth) and validates them (github reports). Generate with `openssl rand -base64 48` |
| `GITHUB_TOKEN` | github-service | *(empty → anonymous)* | GitHub API token — **60 req/hr without it, 5,000 req/hr with it** |
| `GEMINI_API_KEY` | github-service | *(empty → template fallback)* | Enables real AI summaries, roadmaps, skills, interview & code reviews |
| `GITHUB_CLIENT_ID` | auth-service | *(empty → OAuth disabled)* | GitHub OAuth app client id (only for OAuth login) |
| `GITHUB_CLIENT_SECRET` | auth-service | *(empty → OAuth disabled)* | GitHub OAuth app client secret (only for OAuth login) |
| `GITHUB_OAUTH_REDIRECT_URI` | auth-service | `http://localhost:8083/api/auth/oauth/github/callback` | Public callback URL registered in the GitHub OAuth app |
| `OAUTH_FRONTEND_REDIRECT_URI` | auth-service | `http://localhost:5173/auth/callback` | Where the browser lands after OAuth login (frontend token-consumer route) |
| `GITHUB_SERVICE_URL` | auth-service | `http://localhost:8081` | Base URL auth-service uses to reach github-service |

## Which service reads what

- **github-service** — `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `JWT_SECRET`, `GITHUB_TOKEN`, `GEMINI_API_KEY`
- **auth-service** — `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `JWT_SECRET`, `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET`, `GITHUB_OAUTH_REDIRECT_URI`, `OAUTH_FRONTEND_REDIRECT_URI`, `GITHUB_SERVICE_URL`
- **analytics-service** — `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`

## Example

```bash
export SPRING_DATASOURCE_PASSWORD=your-postgres-password
export GITHUB_TOKEN=ghp_your-github-token
export GEMINI_API_KEY=your-gemini-api-key
export JWT_SECRET=$(openssl rand -base64 48)   # REQUIRED — no fallback
# Optional: GitHub OAuth login
export GITHUB_CLIENT_ID=your-oauth-client-id
export GITHUB_CLIENT_SECRET=your-oauth-client-secret
```

> ⚠️ Never commit real secrets. The `.env*` files are gitignored; use a private secrets manager or your IDE's env-var fields instead.
