# 🚀 GitInsight AI

<p align="center">
  <h3 align="center">AI-Powered GitHub Developer Analytics Platform</h3>
  <p align="center">
    Analyze GitHub profiles, compute an explainable Developer Score, review code & commit quality,
    and empower recruiters with AI-driven analytics and PDF reports.
  </p>
</p>

---

## 📖 About the Project

GitInsight AI is a **Java 21 / Spring Boot microservices** platform that analyzes GitHub profiles using the GitHub API and Google Gemini AI.

Instead of manually reviewing repositories, commits, and contribution graphs, users get an interactive dashboard containing a **10-metric Developer Score (0–100)**, repository health, byte-weighted language stacks, **commit & code quality analysis**, AI portfolio reviews, career roadmaps, recruiter tooling, score history tracking, and PDF exportable reports.

> ✅ **Phases 1–7 are implemented.** See [`docs/PHASES.md`](docs/PHASES.md) for the phase-by-phase breakdown.

---

## 🏛 High-Level Architecture

```
                       React + TypeScript Frontend (Vite, port 5173)
                                      │  /api proxy
                                      ▼
        ┌─────────────────────────────────────────────────────────┐
        │  Spring Cloud Microservices (Eureka registry, port 8761) │
        │                                                         │
        │  auth-service (8083)   github-service (8081)            │
        │  JWT / OAuth / Roles   GitHub API + Scoring + Gemini    │
        │  Recruiter dashboard   Reports + score history + PDF    │
        │                                                         │
        │  analytics-service (8082)   common (shared DTOs)        │
        └─────────────────────────────────────────────────────────┘
                                      │
                ┌─────────────────────┴─────────────────────┐
                ▼                                           ▼
          PostgreSQL (gitinsight_auth / gitinsight_github)   GitHub REST API
                                        │
                                        ▼
                                  Google Gemini AI
```

---

## 📂 Project Structure

```
GitInsight-AI/
│
├── common/                  # Shared DTOs (ApiResponse), global exception handler
├── eureka-server/           # Service registry (port 8761)
├── github-service/          # Core: GitHub API client, ScoringEngine, commit quality, Gemini, reports (8081)
├── analytics-service/       # Developer analytics service (8082)
├── auth-service/            # JWT auth, roles, recruiter dashboard backend (8083)
├── e2e-tests/               # Cross-service contract tests (auth ↔ github JWT seam)
├── frontend/                # React + TypeScript + Vite + Tailwind (5173)
├── docs/                    # Architecture, API spec, phases, scoring spec, database design
├── start-dev.sh             # One-shot: PG + all JARs + frontend
├── start-services.sh        # Backend-only startup
├── mvnw                     # Maven wrapper
└── README.md
```

### github-service internals (the heart of the product)

```
github-service/src/main/java/com/gitinsight/githubservice/
├── controller/
│   ├── GitHubController.java     # /api/github/** (profile, repos, score, commits/analytics, rate-limit …)
│   ├── GeminiController.java     # /api/ai/** (summary, roadmap, skills, interview, code-quality …)
│   ├── ReportsController.java    # /api/reports/** (record, history, generate)
│   └── HealthController.java
├── dto/response/                 # DeveloperScoreResponse, CommitAnalyticsResponse, RepositoryResponse …
├── entity/  +  repository/       # ScoreHistory persistence
└── service/
    ├── ScoringEngine.java        # Modular 10-metric engine (see docs/SCORING-ENGINE.md)
    ├── CommitQualityService.java # Phase 5: commit message quality, conventional rate, weekly activity
    ├── CommitDiffService.java    # Phase 6: real commit diffs (per-file patches) for AI review
    ├── DeveloperScoreService.java    # Cached full score (30 min) — cuts repeat views to 0 GitHub calls
    ├── OrganizationAnalyticsService.java # Phase 7: org/team analytics (profile, repo health, languages, contributors, team activity)
    ├── GitHubIntegrationService.java  # PRs, issues, events, languages (byte-weighted), contributors
    ├── GeminiService.java        # AI prompt builders + graceful fallback
    ├── ScoreHistoryService.java  # Trend snapshots & stats
    ├── GitHubCacheService.java   # In-memory TTL cache (rate-limit friendly)
    └── impl/GitHubServiceImpl.java  # Raw GitHub REST client
```

---

## 🗓 Delivery Phases

| Phase | Scope | Status |
|-------|-------|--------|
| **Phase 1** | 10-metric Developer Scoring Engine (0–100) with levels, repo filtering rules | ✅ Done |
| **Phase 2** | Recruiter dashboard — save/bookmark candidates, notes, compare | ✅ Done |
| **Phase 3** | Gemini AI module — summaries, skills, career roadmap, interview, compare, insights | ✅ Done |
| **Phase 4** | Reports — score history tracking, trend charts, PDF export | ✅ Done |
| **Phase 5** | Commit & Code Quality Analysis — commit message quality, conventional-commit rate, commit size balance, weekly activity, AI code-quality review | ✅ Done |
| **Phase 6** | Commit-Diff AI Review — fetch real per-file patches, Gemini reviews each file with per-file scores/issues/suggestions, rule-based fallback | ✅ Done |
| **Phase 7** | Organization / Team-Level Analytics — org profile + repo stats, repo health (archived/inactive/fork ratio), byte-weighted language stack, top contributors with share %, **team activity (commits/PRs/issues over 30/90 days)**, AI team review | ✅ Done |

Detailed phase docs: [`docs/PHASES.md`](docs/PHASES.md)

---

## ⚡ Performance Notes

- **Cached developer score** — `DeveloperScoreService` caches the full computed score per username for 30 min; repeat views of the same profile make **zero GitHub API calls**.
- **Cached base data** — profile + repositories are cached 5 min, per-repo languages/contributors 1 h, so even cold score/AI requests are cheap.
- **Parallel per-repo fetches** — language/contributor fan-out runs on Java 21 virtual threads instead of sequentially.
- **Bounded fan-out** — enrichment caps at 15 repos (languages) / 10 repos (contributors); commit analytics at 15 repos; org team activity at 8 repos (parallel virtual-thread fetches, best-effort).
- **Fast-fail rate limits** — the 429 retry waits at most 20 s per attempt, so an exhausted quota returns a clear error instead of hanging.

## 🔑 Environment Variables

| Variable | Service | Purpose |
|----------|---------|---------|
| `GITHUB_TOKEN` | github-service | GitHub PAT → 5,000 req/hr instead of 60. Optional but recommended |
| `GEMINI_API_KEY` | github-service | Google Gemini key → live AI summaries/roadmaps/reviews. Falls back to templates |
| `JWT_SECRET` | auth-service + github-service | **Required.** Random 32+ byte key that signs JWTs in auth-service and validates them in github-service |
| `GITHUB_CLIENT_ID` / `GITHUB_CLIENT_SECRET` | auth-service | GitHub OAuth app credentials (optional — required for GitHub login) |
| `GITHUB_OAUTH_REDIRECT_URI` | auth-service | OAuth callback URL registered in the GitHub app (default `http://localhost:8083/api/auth/oauth/github/callback`) |
| `OAUTH_FRONTEND_REDIRECT_URI` | auth-service | Where the browser lands after OAuth (default `http://localhost:5173/auth/callback`) |

### 🔐 Security

- **No committed secrets** — `JWT_SECRET` has no fallback: auth-service and github-service refuse to start without it (fail-fast instead of signing with a known key). GitHub OAuth credentials come only from the environment.
- **JWT-protected reports** — github-service keeps the analysis/AI surface public (GitHub data is public by nature), but `GET /api/reports/all`, `GET /api/reports/stats`, and the other `/api/reports/**` endpoints require a valid auth-service JWT.
- **OAuth CSRF protection** — GitHub login uses a random, single-use, expiring `state` token stored server-side; the callback validates it before exchanging the code. The frontend URL is never used as the state.

---

## 🚀 Running Locally

Prerequisites: **Java 21**, **Maven** (or `./mvnw`), **Node 20+ / Bun**, **PostgreSQL 14+**.

```bash
# 1. Create databases (skip if you already created them)
sudo -u postgres createdb gitinsight_auth
sudo -u postgres createdb gitinsight_github

# 2. Build everything (including the common module) — run from the repo ROOT
./mvnw clean package -DskipTests

# 3. Start the full stack (PostgreSQL + Eureka + all services + frontend)
./start-dev.sh
```

Or start services manually:

```bash
# Terminal A — Eureka
java -jar eureka-server/target/eureka-server-*.jar

# Terminal B — backends (order matters: Eureka first)
java -jar auth-service/target/auth-service-*.jar       # 8083
java -jar analytics-service/target/analytics-service-*.jar  # 8082
java -jar github-service/target/github-service-*.jar   # 8081

# Terminal C — frontend
cd frontend && bun install && bun run dev --host 0.0.0.0 --port 5173
```

Open **http://localhost:5173** → search a GitHub username (e.g. `torvalds`) → explore the Developer Score, Language Stack, Top Contributors, Commit & Code Quality, and AI reviews.

> ⚠️ The `github-service` JAR must be **rebuilt** after any backend change: `./mvnw clean package -DskipTests`, then restart it.

## 🧪 Testing

```bash
# Run the full backend test suite (all services + e2e contract tests) from the repo ROOT
./mvnw test

# Frontend
cd frontend && bun run build   # tsc + vite build
cd frontend && bun run lint    # oxlint
```

| Suite | Coverage |
|-------|----------|
| `auth-service` | Full auth flow over the real HTTP + security + JPA stack: register → login → JWT → `/me` → refresh → role authorization → recruiter CRUD, plus the complete GitHub OAuth round trip (random state, code exchange, user upsert, token redirect, replay rejection) |
| `github-service` | Profile/score endpoints, 30-min score caching, 404/429 mapping, org/team analytics, AI endpoints, and authenticated reports (`/api/reports/**` requires a Bearer JWT) with real score persistence |
| `e2e-tests` | Cross-service contract: tokens minted by the **real auth-service `JwtUtil`** validate against the **real github-service `JwtUtil`** (claim names, algorithm, shared-secret derivation) — catches drift the per-service suites can't |

---

## 📚 Documentation

| Document | Contents |
|----------|----------|
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | Services, ports, request flow, data flow |
| [`docs/API-SPEC.md`](docs/API-SPEC.md) | Every REST endpoint across all services |
| [`docs/PHASES.md`](docs/PHASES.md) | Phase-by-phase deliverables (1–7) |
| [`docs/SCORING-ENGINE.md`](docs/SCORING-ENGINE.md) | The 10 metrics, formulas, weights, levels, filtering rules |
| [`docs/DATABASE.md`](docs/DATABASE.md) | Tables, indexes, relationships |

---

## ⭐ Support

If you like this project, please consider giving it a **⭐ Star** on GitHub.

It motivates continued development and helps others discover the project.
