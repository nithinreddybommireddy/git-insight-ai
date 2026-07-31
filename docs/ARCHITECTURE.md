# 🏛 Architecture

## Services & Ports

| Service | Port | Responsibility |
|---------|------|----------------|
| `eureka-server` | 8761 | Spring Cloud service registry / discovery |
| `github-service` | 8081 | GitHub REST client, ScoringEngine, CommitQualityService, Gemini AI, score history & reports |
| `analytics-service` | 8082 | Developer analytics endpoints (health) |
| `auth-service` | 8083 | JWT auth, roles, recruiter dashboard backend |
| `frontend` (Vite) | 5173 | React SPA; proxies `/api` to the backends |

## Request Flow

```
Browser → Vite dev server (5173)
            │  /api/** proxy
            ▼
auth-service (8083)   /api/auth/*        → login/register/refresh/me
github-service (8081) /api/github/*      → profile, repos, score, commits/analytics, rate-limit
                      /api/ai/*          → Gemini summaries, roadmap, code-quality review
                      /api/reports/*     → record, history, generate
auth-service (8083)   /api/recruiter/*   → saved candidates, notes, bookmarks
```

## Data Flow (Developer Score)

```
GET /api/github/{username}/score
  1. GitHubServiceImpl.getRepositories()   → GitHub REST /users/{u}/repos (mapped to RepositoryResponse)
  2. GitHubServiceImpl.getProfile()        → GitHub REST /users/{u}
  3. GitHubIntegrationService.getEnrichedScoreData(repos)
       ├─ getWeightedLanguageBreakdown()   → /repos/{o}/{r}/languages (byte-weighted, cached 1h)
       └─ getAggregateContributors()       → /repos/{o}/{r}/contributors (cached 1h)
  4. ScoringEngine.calculate(...)          → deterministic 10-metric 0-100 score + insights
```

## Data Flow (Commit & Code Quality — Phase 5)

```
GET /api/github/{username}/commits/analytics
  1. GitHubServiceImpl.getRepositories()
  2. CommitQualityService.analyze()
       └─ per non-fork/non-archived repo → /repos/{o}/{r}/commits?author={u}&per_page=50
          (message, date, stats.additions/deletions)
  3. Metrics computed independently:
       - commit message quality        (conventional prefix, length, body)
       - conventional commit rate      (feat/fix/docs/... prefixes)
       - commit size balance           (avg additions+deletions per commit)
       - commits per week              (span-weighted)
       - weekly activity               (last 12 ISO weeks)
  4. Aggregation → normalized 0-100 codeQualityScore (40/20/20/20 weights)
```

## Caching

`GitHubCacheService` provides an in-memory TTL cache (default 15 min, per-key overrides).
- Events/commits/PRs/issues: 10 min
- Languages/contributors: 1 hour
- Commit quality: 10 min
- Orgs: 1 hour

This keeps repeated dashboard loads well within the GitHub rate limit.

## Security

- `auth-service`: Spring Security + JWT (jjwt), roles `USER` / `RECRUITER` / `ADMIN`.
- `github-service` & `analytics-service`: permissive for public endpoints (`/api/github/**`, `/api/ai/**`, `/api/reports/**`).
- All GitHub requests use `Authorization: Bearer <GITHUB_TOKEN>` when configured.
