# 🚀 GitInsight AI

<p align="center">
  <strong>AI-Powered GitHub Developer Analytics & Recruitment Platform</strong>
</p>

<p align="center">
  Analyze GitHub profiles, evaluate developer activity, generate an explainable Developer Score,
  review code and commits with AI, and help recruiters discover and compare developers.
</p>

<p align="center">
  Java 21 • Spring Boot • React • TypeScript • PostgreSQL • GitHub API • Gemini AI • Docker
</p>

---

## 📌 Overview

**GitInsight AI** is an AI-powered GitHub Developer Analytics Platform built with **Java 21, Spring Boot microservices, React, PostgreSQL, GitHub API, and Google Gemini AI**.

The platform converts raw GitHub activity into meaningful developer insights.

Instead of manually reviewing repositories, commits, pull requests, issues, contribution activity, and programming languages, GitInsight AI provides an interactive dashboard with:

- 📊 Explainable Developer Score
- 📈 Contribution and activity analytics
- 💻 Repository health analysis
- 🧑‍💻 Developer profile insights
- 🔤 Language stack analysis
- 📝 Commit quality analysis
- 🔍 Commit-diff AI code review
- 🤖 AI-generated developer summaries
- 🎯 Skills analysis
- 🗺️ Career roadmap generation
- 🎤 AI interview preparation
- 👥 Recruiter candidate management
- ⚖️ Developer comparison
- 🏢 Organization/team analytics
- 📜 Score history and trends
- 📄 PDF reports
- 🔐 JWT authentication
- 🐙 GitHub OAuth login

---

# 🎯 Why GitInsight AI?

Traditional GitHub profile review requires recruiters or developers to manually inspect:

```text
GitHub Profile
      ↓
Repositories
      ↓
Commits
      ↓
Pull Requests
      ↓
Issues
      ↓
Languages
      ↓
Contribution Activity
      ↓
Code Quality
      ↓
Manual Evaluation
```

GitInsight AI automates this process:

```text
                GitHub Profile
                       │
                       ▼
              GitHub REST API
                       │
                       ▼
             Data Collection Layer
                       │
          ┌────────────┼────────────┐
          ▼            ▼            ▼
       Repos        Commits       PRs/Issues
          │            │            │
          └────────────┼────────────┘
                       ▼
              Analytics Engine
                       │
                       ▼
             Developer Score
                       │
          ┌────────────┼────────────┐
          ▼            ▼            ▼
      Dashboard      Gemini AI    Reports
          │            │            │
          └────────────┼────────────┘
                       ▼
             Actionable Insights
```

The goal is not simply to count GitHub activity, but to transform activity into **explainable developer insights**.

---

# ✨ Key Features

## 📊 Developer Analytics

Analyze a GitHub developer using multiple dimensions:

- Developer Score
- Contribution activity
- Repository health
- Programming language distribution
- Commit activity
- Pull requests
- Issues
- Repository statistics
- Open-source activity
- Collaboration activity

---

## 🏆 10-Metric Developer Score

GitInsight AI calculates an explainable **0–100 Developer Score** using a modular scoring engine.

The score is designed to provide a structured view of developer activity instead of relying on a single GitHub metric.

```text
GitHub Activity
      │
      ├── Contribution Activity
      ├── Repository Health
      ├── Language Analysis
      ├── Commit Activity
      ├── Collaboration
      ├── Open Source Activity
      ├── Pull Requests
      ├── Issues
      ├── Code Quality
      └── Activity Recency
                │
                ▼
        Scoring Engine
                │
                ▼
       Developer Score
          0 ───── 100
```

For the complete formulas, weights, filtering rules, and scoring levels:

[`docs/SCORING-ENGINE.md`](docs/SCORING-ENGINE.md)

---

# 🤖 AI-Powered Developer Insights

Google Gemini AI is integrated into the platform to generate intelligent insights from GitHub data.

### AI capabilities include:

- Developer summary
- Skills analysis
- Career roadmap
- Interview preparation
- Developer comparison
- AI insights
- Portfolio review
- Code quality review
- Commit-diff review
- Team-level review

If Gemini is unavailable, the application provides graceful fallback responses where supported.

---

# 🔍 Commit & Code Quality Analysis

GitInsight AI goes beyond simple commit counting.

The platform analyzes:

- Commit message quality
- Conventional commit usage
- Commit size balance
- Weekly activity
- Commit patterns
- Real commit diffs
- Per-file code changes
- Code quality issues
- Improvement suggestions

For commit-diff analysis:

```text
GitHub Commit
      │
      ▼
Fetch Commit Diff
      │
      ▼
Extract Changed Files
      │
      ▼
Analyze Individual Patches
      │
      ├── Code Quality
      ├── Potential Issues
      ├── Suggestions
      └── Score
      │
      ▼
Gemini AI Review
      │
      ▼
Developer Insights
```

---

# 👥 Recruiter Module

GitInsight AI also provides functionality for recruiters to manage developer candidates.

Recruiter capabilities include:

- Save candidates
- Bookmark candidates
- Add recruiter notes
- Compare developers
- Review developer scores
- Analyze GitHub activity
- Generate reports
- View candidate analytics

This allows GitHub-based developer evaluation to happen inside one platform rather than manually opening multiple GitHub profiles.

---

# 🏢 Organization & Team Analytics

GitInsight AI supports organization-level analysis.

The platform can analyze:

- Organization profile
- Repository statistics
- Repository health
- Archived repository ratio
- Inactive repository ratio
- Fork ratio
- Language distribution
- Top contributors
- Contributor share
- Team activity
- Commit activity
- Pull requests
- Issues
- AI-generated team review

Team activity can be analyzed across different time periods such as:

```text
30 Days
90 Days
```

---

# 📈 Score History & Reports

Developer scores can be stored over time to identify trends.

Example:

```text
Score History

100 ┤
 90 ┤                    ●
 80 ┤              ●─────
 70 ┤        ●─────
 60 ┤  ●─────
 50 ┤
    └────────────────────────
       Jan  Feb  Mar  Apr
```

The platform also supports:

- Score history
- Trend analysis
- Report generation
- PDF export

---

# 🔐 Authentication & Security

GitInsight AI uses JWT-based authentication and role-based authorization.

Supported authentication functionality includes:

- User registration
- User login
- JWT authentication
- Role-based authorization
- GitHub OAuth login
- Recruiter access control
- Protected report endpoints
- OAuth CSRF protection
- Authentication rate limiting

### Security highlights

- No committed secrets
- JWT secret required through environment variables
- Minimum 32-byte JWT secret
- GitHub OAuth credentials stored through environment variables
- OAuth state validation
- Single-use OAuth state
- Expiring OAuth state
- Authentication rate limiting
- Protected report endpoints
- SQL logging disabled by default
- Actuator limited to health endpoint

---

# 🏗️ Architecture

GitInsight AI follows a **Spring Cloud Microservices Architecture**.

```text
                         ┌─────────────────────────┐
                         │ React + TypeScript       │
                         │ Vite + Tailwind          │
                         │ (Vercel)                 │
                         └────────────┬────────────┘
                                      │
                                      │ HTTPS
                                      ▼
                         ┌─────────────────────────┐
                         │    API Gateway           │
                         │    Port: 8080            │
                         │    Spring Cloud Gateway  │
                         └────────────┬────────────┘
                                      │
                         Eureka Service Discovery
                                      │
             ┌────────────────────────┼────────────────────────┐
             │                        │                        │
             ▼                        ▼                        ▼
    ┌────────────────┐      ┌────────────────┐      ┌────────────────┐
    │ auth-service   │      │ github-service │      │ analytics-     │
    │ Port: 8083     │      │ Port: 8081     │      │ service        │
    │                │      │                │      │ Port: 8082     │
    │ JWT            │      │ GitHub API     │      │ Analytics      │
    │ OAuth          │      │ Scoring Engine │      │                │
    │ Roles          │      │ Gemini AI      │      │                │
    │ Recruiters     │      │ Reports        │      │                │
    └───────┬────────┘      └───────┬────────┘      └────────────────┘
            │                       │
            ▼                       ▼
    ┌────────────────┐      ┌────────────────┐
    │ PostgreSQL     │      │ GitHub REST API│
    │ Auth DB        │      └───────┬────────┘
    └────────────────┘              │
                                    ▼
                            ┌────────────────┐
                            │ Google Gemini  │
                            │ AI             │
                            └────────────────┘
```

---

# 🧩 Microservices

| Service | Port | Responsibility |
|---|---:|---|
| `api-gateway` | 8080 | JWT validation, CORS, routing, role-based authorization |

> **Production note:** In production (e.g. Vercel frontend + Railway gateway on different origins),
> set `AUTH_COOKIE_SECURE=true` and `AUTH_COOKIE_SAME_SITE=None` so HttpOnly cookies work
> cross-site. For the most reliable cookie behavior, consider using custom subdomains
> (e.g. `app.example.com` + `api.example.com`). |
| `eureka-server` | 8761 | Service discovery |
| `github-service` | 8081 | GitHub integration, scoring, AI, reports |
| `analytics-service` | 8082 | Analytics (placeholder — currently only exposes health; not yet implemented) |
| `auth-service` | 8083 | Authentication, OAuth, JWT, roles |
| `frontend` | 5173 | React web application |

---

# 📂 Project Structure

```text
GitInsight-AI/
│
├── common/
│   └── Shared DTOs, JWT, CSRF, security components
│
├── api-gateway/
│   └── Spring Cloud Gateway — JWT validation, CORS, routing
│
├── eureka-server/
│   └── Service registry
│
├── github-service/
│   └── GitHub API, scoring, AI, reports
│
├── analytics-service/
│   └── Analytics placeholder — health endpoint only, no real analytics APIs yet
│
├── auth-service/
│   └── JWT authentication, OAuth, recruiter functionality
│
├── e2e-tests/
│   └── Cross-service contract tests
│
├── frontend/
│   └── React + TypeScript + Vite + Tailwind
│
├── docs/
│   ├── ARCHITECTURE.md
│   ├── API-SPEC.md
│   ├── PHASES.md
│   ├── SCORING-ENGINE.md
│   ├── DATABASE.md
│   ├── ENV-VARS.md
│   ├── DEPLOYMENT.md
│   └── LOAD-TESTING.md
│
├── docker/
│   └── env.example
│
├── start-dev.sh
├── start-services.sh
├── docker-compose.yml
├── docker-compose.prod.yml
├── Dockerfile
├── mvnw
└── README.md
```

---

# 🛠️ Technology Stack

## Frontend

- React
- TypeScript
- Vite
- Tailwind CSS
- Axios
- Charting / analytics UI

## Backend

- Java 21
- Spring Boot
- Spring Cloud
- Spring Security
- Spring Data JPA
- Maven
- JWT

## Database

- PostgreSQL

## AI

- Google Gemini AI

## External APIs

- GitHub REST API

## DevOps

- Docker
- Docker Compose
- Git
- GitHub
- Nginx

## Testing

- Spring Boot Test
- HTTP integration tests
- E2E contract tests
- Frontend build and linting
- Load testing

---

# 🗓️ Development Phases

| Phase | Scope | Status |
|---|---|---|
| Phase 1 | 10-metric Developer Scoring Engine | ✅ Done |
| Phase 2 | Recruiter Dashboard | ✅ Done |
| Phase 3 | Gemini AI Module | ✅ Done |
| Phase 4 | Reports & Score History | ✅ Done |
| Phase 5 | Commit & Code Quality Analysis | ✅ Done |
| Phase 6 | Commit-Diff AI Review | ✅ Done |
| Phase 7 | Organization / Team Analytics | ✅ Done |

Detailed breakdown:

[`docs/PHASES.md`](docs/PHASES.md)

---

# ⚡ Performance

GitInsight AI includes several optimizations to reduce GitHub API usage and improve response times.

### Developer Score Cache

The complete developer score is cached for **30 minutes**.

Repeated requests for the same developer can therefore avoid additional GitHub API calls.

### Base Data Cache

Cached data includes:

- Profile
- Repositories
- Repository languages
- Contributors

### Parallel Processing

Per-repository enrichment uses **Java 21 virtual threads** for parallel processing.

### Bounded API Fan-Out

The system limits repository enrichment to avoid excessive GitHub API usage.

### Rate-Limit Handling

GitHub API `429` responses are handled with bounded retry behavior.

---

# 🔑 Environment Variables

Create the required environment variables before starting the application.

| Variable | Service | Required | Purpose |
|---|---|---|---|
| `GITHUB_TOKEN` | github-service | Optional | GitHub API authentication |
| `GEMINI_API_KEY` | github-service | Optional | Gemini AI |
| `JWT_SECRET` | auth + github | **Yes** | JWT signing/validation |
| `GITHUB_CLIENT_ID` | auth-service | OAuth only | GitHub OAuth |
| `GITHUB_CLIENT_SECRET` | auth-service | OAuth only | GitHub OAuth |
| `GITHUB_OAUTH_REDIRECT_URI` | auth-service | OAuth only | OAuth callback |
| `OAUTH_FRONTEND_REDIRECT_URI` | auth-service | OAuth only | Frontend OAuth redirect |

Example:

```env
GITHUB_TOKEN=your_github_token
GEMINI_API_KEY=your_gemini_api_key

JWT_SECRET=your_random_32_byte_or_longer_secret

GITHUB_CLIENT_ID=your_github_client_id
GITHUB_CLIENT_SECRET=your_github_client_secret

GITHUB_OAUTH_REDIRECT_URI=http://localhost:8080/api/auth/oauth/github/callback
OAUTH_FRONTEND_REDIRECT_URI=http://localhost:5173/auth/callback
```

> ⚠️ Never commit `.env` files, API keys, JWT secrets, or OAuth secrets to GitHub.

---

# 🚀 Running Locally

## Prerequisites

Install:

- Java 21
- Maven or Maven Wrapper
- Node.js 20+ / Bun
- PostgreSQL 14+
- Git

---

## 1. Clone the Repository

```bash
git clone https://github.com/<your-username>/GitInsight-AI.git

cd GitInsight-AI
```

---

## 2. Create PostgreSQL Databases

```bash
sudo -u postgres createdb gitinsight_auth
sudo -u postgres createdb gitinsight_github
sudo -u postgres createdb gitinsight_analytics
```

If you are using Windows, create the databases through PostgreSQL/pgAdmin instead.

---

## 3. Configure Environment Variables

Configure the required variables described in the **Environment Variables** section.

---

## 4. Build the Backend

Run this command from the repository root:

```bash
./mvnw clean package -DskipTests
```

On Windows:

```bash
mvnw.cmd clean package -DskipTests
```

---

## 5. Start the Full Stack

```bash
./start-dev.sh
```

The application will start:

```text
Frontend       → http://localhost:5173
Eureka         → http://localhost:8761
GitHub Service → http://localhost:8081
Analytics      → http://localhost:8082
Auth Service   → http://localhost:8083
PostgreSQL     → localhost:5432
```

> 🪟 **Windows:** the bash scripts (`start-dev.sh`, `scripts/init-env.sh`) assume a POSIX shell. Use **Git Bash** or **WSL** to run them (or run the equivalent commands manually from the sections below).

---

# ▶️ Manual Startup

If you want to start each service individually:

### Terminal 1 — Eureka

```bash
java -jar eureka-server/target/eureka-server-*-exec.jar
```

### Terminal 2 — Auth Service

```bash
java -jar auth-service/target/auth-service-*-exec.jar
```

### Terminal 3 — Analytics Service

```bash
java -jar analytics-service/target/analytics-service-*-exec.jar
```

### Terminal 4 — GitHub Service

```bash
java -jar github-service/target/github-service-*-exec.jar
```

### Terminal 5 — Frontend

```bash
cd frontend

bun install

bun run dev --host 0.0.0.0 --port 5173
```

---

# 🐳 Docker

GitInsight AI includes Docker support for running the complete stack.

The Docker setup contains:

```text
PostgreSQL
    +
Redis
    +
Eureka
    +
API Gateway
    +
Auth Service
    +
GitHub Service
    +
Analytics Service (placeholder)
    +
Frontend / Nginx
```

## Configure Environment

```bash
# Bootstraps .env from docker/env.example and generates a random JWT_SECRET
# (JWT_SECRET is required — the services refuse to boot with a weak one).
sh scripts/init-env.sh
# ...or copy manually and edit:  cp docker/env.example .env
```

Add `GITHUB_TOKEN` / `GEMINI_API_KEY` / OAuth credentials to `.env` if you have them.

For example:

```env
JWT_SECRET=your_secure_secret
GITHUB_TOKEN=your_github_token
GEMINI_API_KEY=your_gemini_api_key
```

---

## Start Docker Stack

```bash
docker compose up --build
```

---

## Stop Docker Stack

```bash
docker compose down
```

Stop and remove the database volume:

```bash
docker compose down -v
```

View GitHub service logs:

```bash
docker compose logs -f github-service
```

---

# 🌐 Application URLs

| URL | Service |
|---|---|
| http://localhost:5173 | Frontend |
| http://localhost:8761 | Eureka Dashboard |
| http://localhost:8081 | GitHub Service |
| http://localhost:8082 | Analytics Service |
| http://localhost:8083 | Auth Service |
| localhost:5432 | PostgreSQL |

---

# 🧪 Testing

## Backend

Run the complete backend test suite:

```bash
./mvnw test
```

---

## Frontend Build

```bash
cd frontend

bun run build
```

---

## Frontend Lint

```bash
cd frontend

bun run lint
```

---

## Load Testing

Against a running application:

```bash
node scripts/load-test.mjs
```

Detailed information:

[`docs/LOAD-TESTING.md`](docs/LOAD-TESTING.md)

---

# 🔬 Test Coverage

### Auth Service

Tests include:

- Registration
- Login
- JWT generation
- `/me`
- Refresh token
- Role authorization
- Recruiter CRUD
- GitHub OAuth flow
- OAuth state validation
- OAuth replay rejection

### GitHub Service

Tests include:

- GitHub profile endpoints
- Developer scoring
- Score caching
- GitHub 404 handling
- GitHub 429 handling
- Organization analytics
- Team analytics
- AI endpoints
- Protected report endpoints
- Score persistence

### E2E Tests

Cross-service JWT compatibility is tested between:

```text
auth-service
      │
      │ JWT
      ▼
github-service
```

This helps detect authentication contract drift between services.

---

# 📚 Documentation

| Document | Description |
|---|---|
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | System architecture and request/data flow |
| [`docs/API-SPEC.md`](docs/API-SPEC.md) | REST API documentation |
| [`docs/PHASES.md`](docs/PHASES.md) | Development phases |
| [`docs/SCORING-ENGINE.md`](docs/SCORING-ENGINE.md) | Developer Score formulas and weights |
| [`docs/DATABASE.md`](docs/DATABASE.md) | Database design |
| [`docs/ENV-VARS.md`](docs/ENV-VARS.md) | Environment variables |
| [`docs/DEPLOYMENT.md`](docs/DEPLOYMENT.md) | Deployment configuration |
| [`docs/LOAD-TESTING.md`](docs/LOAD-TESTING.md) | Load testing |

---

# 📊 What a Developer Can Learn from GitInsight AI

A GitHub username can be transformed into a structured developer profile:

```text
GitHub Username
       │
       ▼
┌─────────────────────────┐
│ Developer Profile       │
└────────────┬────────────┘
             │
     ┌───────┼────────┐
     ▼       ▼        ▼
   Repos   Commits   PRs
     │       │        │
     └───────┼────────┘
             ▼
      Analytics Engine
             │
    ┌────────┼─────────┐
    ▼        ▼         ▼
  Score    Quality    Activity
    │        │         │
    └────────┼─────────┘
             ▼
          Gemini AI
             │
    ┌────────┼─────────┐
    ▼        ▼         ▼
 Summary   Skills   Roadmap
             │
             ▼
       Developer Profile
```

---

# 🎯 Example Use Cases

## 👨‍💻 Developers

Understand:

- Current GitHub activity
- Repository quality
- Coding consistency
- Language usage
- Commit quality
- Areas for improvement
- Career development opportunities

## 👨‍💼 Recruiters

Use GitInsight AI to:

- Discover candidates
- Analyze GitHub activity
- Compare developers
- Save candidates
- Add notes
- Review developer scores
- Generate reports

## 🏢 Engineering Teams

Use organization analytics to understand:

- Team activity
- Repository health
- Contributor distribution
- Language stack
- Commit activity
- PR activity
- Issue activity

---

# 📈 Current Project Status

### Completed

- ✅ Microservices architecture
- ✅ GitHub integration
- ✅ Developer scoring engine
- ✅ Recruiter dashboard
- ✅ JWT authentication
- ✅ GitHub OAuth
- ✅ Gemini AI integration
- ✅ Commit quality analysis
- ✅ Commit-diff AI review
- ✅ Score history
- ✅ PDF reports
- ✅ Organization analytics
- ✅ Team analytics
- ✅ Docker deployment
- ✅ API Gateway with JWT validation
- ✅ Automated tests
- ✅ E2E contract testing
- ✅ Load testing support

---

# 🔮 Future Improvements

Potential future enhancements include:

- Advanced recruiter search and filtering
- More AI-powered developer recommendations
- Additional code-hosting integrations
- Improved organization benchmarking
- More detailed historical analytics
- Advanced developer comparison
- Additional deployment options
- Enhanced AI portfolio evaluation

---

# ⚠️ Important Notes

### GitHub API Rate Limits

Using a GitHub Personal Access Token is recommended because unauthenticated GitHub API requests have significantly lower rate limits.

Configure:

```env
GITHUB_TOKEN=your_token
```

### Rebuild After Backend Changes

After modifying backend code:

```bash
./mvnw clean package -DskipTests
```

Then restart the affected service.

### Secrets

Never commit:

```text
.env
JWT_SECRET
GITHUB_TOKEN
GEMINI_API_KEY
GITHUB_CLIENT_SECRET
```

to the repository.

---

# 🤝 Contributing

Contributions are welcome.

```text
1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add/update tests
5. Commit your changes
6. Push the branch
7. Open a Pull Request
```

Example:

```bash
git checkout -b feature/new-feature

git add .

git commit -m "feat: add new feature"

git push origin feature/new-feature
```

---

# 📄 License

Add your preferred license here.

For example:

```text
MIT License
```

---

# ⭐ Support

If you find GitInsight AI useful:

- ⭐ Star the repository
- 🍴 Fork the project
- 🐛 Report issues
- 💡 Suggest improvements
- 🤝 Contribute to the project

Your support helps the project grow.

---

<p align="center">
  <strong>GitInsight AI</strong>
</p>

<p align="center">
  Turning GitHub activity into meaningful developer intelligence.
</p>
