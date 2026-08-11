# 🗓 Delivery Phases

## Phase 1 — Developer Scoring Engine ✅

Goal: replace star-based scoring with a transparent, industry-standard 0–100 Developer Score.

**Delivered**
- `ScoringEngine.java` — 10 independent, deterministic metrics with configurable weights (spec in `SCORING-ENGINE.md`)
- Contribution recency, commit frequency, repository health, repository quality, consistency, language diversity, collaboration, OSS impact, popularity, maintenance
- Repository filtering (forks/archived/empty excluded) + developer levels (Newcomer → Elite)
- Per-metric `MetricScore` breakdown with explanation, improvement suggestion, trend, icon
- Rule-based `DeveloperInsights` generator (assessment, strongest skill, weakest area, recommendations)
- Enrichment: byte-weighted languages (`/languages`) and external contributors (`/contributors`) wired into Language Diversity & Collaboration

**Files**: `ScoringEngine.java`, `DeveloperScoreResponse.java`, `GitHubServiceImpl.java`, `GitHubIntegrationService.java`, `GitHubController.java`, `DeveloperScore.tsx` (frontend)

---

## Phase 2 — Recruiter Dashboard ✅

Goal: give recruiters a hiring workflow around the developer score.

**Delivered**
- Save / unsave candidates with score snapshot (username, name, avatar, githubId, score, level, languages)
- Bookmark flag, per-candidate recruiter notes (title + content), stats (saved count, notes count)
- `RecruiterController` (auth-service) with `@PreAuthorize` for `RECRUITER` / `ADMIN` roles
- Frontend: `RecruiterDashboard.tsx`, `CandidateDetails.tsx`
- **Job-description file matching**: upload a JD (.txt/.md/.pdf) + optional usernames CSV/TXT → fresh candidate search ranked by skill fit (match = 60% skill match + 40% developer score). Pool = uploaded usernames or the recruiter's saved candidates. `JobMatcherService` (auth-service) with word-boundary skill dictionary, PDF/text parsing, and github-service enrichment
- **Gemini job-match mode**: with the `ai` flag, auth-service sends the JD + deterministic match data to github-service `POST /api/ai/job-match`, and Gemini returns per-candidate fit labels, explanations, strengths, gaps, and Interview/Consider/Skip recommendations — merged into the ranked results (graceful fallback when `GEMINI_API_KEY` is missing)

**Endpoints** (auth-service): `POST /api/recruiter/candidates/save`, `DELETE /api/recruiter/candidates/{username}`, `GET /api/recruiter/candidates`, `GET /api/recruiter/candidates/bookmarked`, `PUT /api/recruiter/candidates/{username}/bookmark`, `POST /api/recruiter/candidates/{username}/notes`, `GET /api/recruiter/candidates/{username}/notes`, `DELETE /api/recruiter/notes/{noteId}`, `GET /api/recruiter/stats`

---

## Phase 3 — Gemini AI Module ✅

Goal: AI-powered developer summaries and career guidance with graceful fallback.

**Delivered**
- `GeminiService.java` — prompt builders + `callGemini` (gemini-2.0-flash), 800-token output cap
- System instruction enforcing data-driven, constructive, concise analysis
- 8 endpoints: summary, repository review, skills, career roadmap, interview readiness, compare two developers, enhanced insights, **code-quality review (Phase 5)**
- Fallback templates when `GEMINI_API_KEY` is missing so the UI never breaks

**Endpoints** (github-service `/api/ai/*`): `status`, `summary/{u}`, `review/{u}/{repo}`, `skills/{u}`, `roadmap/{u}`, `interview/{u}`, `compare/{u1}/{u2}`, `insights/{u}`, `code-quality/{u}`

**Frontend**: `AIAnalysis.tsx` (tabs), `AISummaryPanel.tsx`, `SkillsMatrix.tsx`

---

## Phase 4 — Reports, Score History & PDF ✅

Goal: track score trends over time and export professional reports.

**Delivered**
- `ScoreHistory` entity + `ScoreHistoryService` (record snapshot, ascending/descending history, latest, stats)
- `ReportsController`: record, history, latest, all, stats, generate
- Frontend `ReportsPage.tsx`: score-over-time SVG chart, snapshot table, report summary
- `ReportExport.tsx`: PDF export (client-side rendering of profile + score + repos + history)

**Endpoints** (github-service `/api/reports/*`): `POST /record/{u}`, `GET /history/{u}`, `GET /latest/{u}`, `GET /all`, `GET /stats`, `GET /generate/{u}`

---

## Phase 5 — Commit & Code Quality Analysis ✅

Goal: analyze how a developer actually commits — beyond repo-level metadata.

**Delivered**
- `CommitQualityService.java` — fetches real commit history (`/repos/{o}/{r}/commits?author={u}&per_page=50`) with per-commit stats
- Metrics (all deterministic, computed independently):
  - **Commit Message Quality** (0–100): conventional prefix, descriptive length, multi-line body
  - **Conventional Commit Rate** (0–100): % using `feat:/fix:/docs:/...` prefixes
  - **Commit Size Balance** (0–100): reviewable avg change size (penalizes mega-commits & empty commits)
  - **Commits per Week**: span-weighted frequency
  - **Weekly Activity**: last 12 ISO weeks
  - **Per-repo breakdown**: commits, +additions/−deletions, message quality
- Aggregation → normalized **Code Quality Score** (message 40% · conventional 20% · size 20% · frequency 20%)
- `CommitAnalyticsResponse` DTO with explanation, improvement suggestion, trend
- **AI code quality review** endpoint (`/api/ai/code-quality/{u}`) — Gemini analyzes the real commit data
- Frontend `CommitQualityPanel.tsx`: score ring, stat chips, quality bars, weekly activity chart, AI review button

**Endpoints**: `GET /api/github/{u}/commits/analytics`, `GET /api/ai/code-quality/{u}`

---

## Phase 6 — Commit-Diff AI Review ✅

Goal: review the actual code a developer writes — the per-file patches of real commits — with AI.

**Delivered**
- `CommitDiffService.java` — fetches real commit diffs (`/repos/{o}/{r}/commits?author=...` then per-commit detail with `files[].patch`) for the developer's own repos, capped (8 repos, 15 commits, 12 files/commit, patches truncated at 6 KB) and cached 10 min
- `CommitDiffResponse` / `CommitDiffListResponse` — commit metadata + per-file diff stats and truncated unified diff
- **AI per-file review** (`/api/ai/commit-diff-review`): Gemini reads each file's patch and returns strict JSON — overall 0–100 score, verdict, key issues, strengths, recommendations, and per-file findings (score, summary, issues, suggestions)
- **Rule-based fallback** — `CommitDiffReviewResponse.deterministic()` scores reviewability (balanced file sizes, conventional/descriptive messages) so the feature works without `GEMINI_API_KEY`
- Frontend `CommitQualityPanel.tsx` — “Commit Diff Review” card: load recent diffs, pick a commit, run the AI review, see overall verdict + per-file findings

**Endpoints**: `GET /api/github/{u}/commits/diffs?limit=15`, `POST /api/ai/commit-diff-review`

---

## Backlog (future phases)

- Historical trends beyond snapshots (commit-level time series)
- Organization / team-level analytics
- Resume parsing & interview readiness scoring
- Docker / CI-CD / deployment automation
