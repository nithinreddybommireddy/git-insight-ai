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

> ✅ **Phases 1–5 are implemented.** See [`docs/PHASES.md`](docs/PHASES.md) for the phase-by-phase breakdown.

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

Detailed phase docs: [`docs/PHASES.md`](docs/PHASES.md)

---

## 🔑 Environment Variables

| Variable | Service | Purpose |
|----------|---------|---------|
| `GITHUB_TOKEN` | github-service | GitHub PAT → 5,000 req/hr instead of 60. Optional but recommended |
| `GEMINI_API_KEY` | github-service | Google Gemini key → live AI summaries/roadmaps/reviews. Falls back to templates |
| `GITHUB_CLIENT_ID` / `GITHUB_CLIENT_SECRET` | auth-service | GitHub OAuth (optional) |
| `JWT_SECRET` | auth-service | Override default JWT signing key |

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

---

## 📚 Documentation

| Document | Contents |
|----------|----------|
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | Services, ports, request flow, data flow |
| [`docs/API-SPEC.md`](docs/API-SPEC.md) | Every REST endpoint across all services |
| [`docs/PHASES.md`](docs/PHASES.md) | Phase-by-phase deliverables (1–5) |
| [`docs/SCORING-ENGINE.md`](docs/SCORING-ENGINE.md) | The 10 metrics, formulas, weights, levels, filtering rules |
| [`docs/DATABASE.md`](docs/DATABASE.md) | Tables, indexes, relationships |

---

## ⭐ Support

If you like this project, please consider giving it a **⭐ Star** on GitHub.

It motivates continued development and helps others discover the project.
