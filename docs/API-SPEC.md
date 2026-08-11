# 🔌 API Specification

All responses are wrapped in `ApiResponse<T>`:
```json
{ "success": true, "message": "...", "data": { ... } }
```

## auth-service (8083) — `/api/auth`

| Method | Path | Description |
|--------|------|-------------|
| POST | `/auth/register` | Register `{name, email, password}` → `{token, refreshToken, user}` |
| POST | `/auth/login` | Login `{email, password}` → `{token, refreshToken, user}` |
| POST | `/auth/refresh` | Refresh `{refreshToken}` → new tokens |
| GET | `/auth/me` | Current user (Bearer token) |

## auth-service (8083) — `/api/recruiter` (roles RECRUITER/ADMIN)

| Method | Path | Description |
|--------|------|-------------|
| POST | `/recruiter/candidates/save` | Save a candidate with score snapshot |
| DELETE | `/recruiter/candidates/{username}` | Unsave candidate |
| GET | `/recruiter/candidates` | List saved candidates |
| GET | `/recruiter/candidates/bookmarked` | List bookmarked candidates |
| PUT | `/recruiter/candidates/{username}/bookmark` | Toggle bookmark `{bookmarked}` |
| POST | `/recruiter/candidates/{username}/notes` | Add note `{content, title?}` |
| GET | `/recruiter/candidates/{username}/notes` | List notes |
| DELETE | `/recruiter/notes/{noteId}` | Delete note (owner only) |
| GET | `/recruiter/stats` | Saved count + notes count |
| POST | `/recruiter/match` | Multipart: `file` = job description (.txt/.md/.pdf, required), `usernames` = CSV/TXT of GitHub usernames (optional; falls back to saved candidates), `ai` = `true` to add Gemini per-candidate fit explanations → ranked job-fit candidate search |

## github-service (8081) — `/api/github`

| Method | Path | Description |
|--------|------|-------------|
| GET | `/github/profile/{username}` | GitHub profile |
| GET | `/github/{username}/repos` | Repositories (sorted by stars) |
| GET | `/github/{username}/score` | Full 10-metric Developer Score + insights |
| GET | `/github/{username}/insights` | Alias of score (full insights) |
| GET | `/github/{username}/commits/analytics` | **Phase 5** commit & code quality analysis |
| GET | `/github/{username}/commits/diffs` | **Phase 6** recent commit diffs (`limit` query param, default 15) with per-file patches, status, additions/deletions |
| GET | `/github/{username}/commits` | Recent commits (up to 50, author-filtered) |
| GET | `/github/{username}/organizations` | User's orgs |
| GET | `/github/{username}/pull-requests` | PRs authored (search API) |
| GET | `/github/{username}/issues` | Issues authored (search API) |
| GET | `/github/{username}/languages` | Language breakdown by repo count |
| GET | `/github/{username}/languages/weighted` | Byte-weighted language breakdown |
| GET | `/github/{username}/contributors` | Aggregate contributors across repos |
| GET | `/github/{username}/events` | User activity events |
| GET | `/github/{username}/received-events` | Events received from others |
| GET | `/github/{username}/contribution-stats` | Commit/PR/issue/org stats |
| GET | `/github/rate-limit` | GitHub quota from x-ratelimit headers + body |
| GET | `/github/{owner}/{repo}/languages` | Per-repo language bytes |
| GET | `/github/{owner}/{repo}/contributors` | Per-repo contributors |
| GET | `/github/{owner}/{repo}/pulls` | Per-repo PRs (core API) |
| GET | `/github/{owner}/{repo}/issues` | Per-repo issues (core API) |

## github-service (8081) — `/api/ai`

| Method | Path | Description |
|--------|------|-------------|
| GET | `/ai/status` | AI enabled? provider/model |
| GET | `/ai/summary/{username}` | AI developer summary |
| GET | `/ai/review/{username}/{repoName}` | AI repository review |
| GET | `/ai/skills/{username}` | AI skill analysis |
| GET | `/ai/roadmap/{username}` | AI career roadmap |
| GET | `/ai/interview/{username}` | AI interview readiness |
| GET | `/ai/compare/{user1}/{user2}` | AI comparison of two developers |
| GET | `/ai/insights/{username}` | Enhanced AI insights + score |
| GET | `/ai/code-quality/{username}` | **Phase 5** AI code quality review + analytics |
| POST | `/ai/commit-diff-review` | **Phase 6** Body: `{username, commits: [{sha, message, repoName, files: [{filename, status, additions, deletions, patch}]}]}` → per-file AI code review (`overallScore`, `keyIssues`, `strengths`, `recommendations`, `fileReviews[]`) with rule-based fallback when `GEMINI_API_KEY` is missing |
| POST | `/ai/job-match` | Body: `{jobTitle, jobDescription, requiredSkills, candidates[]}` → per-candidate AI fit explanations (`enabled`, `model`, `explanations[]`) |

## github-service (8081) — `/api/reports`

| Method | Path | Description |
|--------|------|-------------|
| POST | `/reports/record/{username}` | Compute + persist score snapshot |
| GET | `/reports/history/{username}` | Score history ascending (charts) |
| GET | `/reports/latest/{username}` | Latest snapshot |
| GET | `/reports/all` | All snapshots |
| GET | `/reports/stats` | totalSnapshots / uniqueUsers / averageScore |
| GET | `/reports/generate/{username}` | Score + profile + repos + history + recorded snapshot |

## analytics-service (8082)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/` or `/api/health` | Health check |

## Error Handling

`GlobalExceptionHandler` (common module) maps:
- `*not found*` → **404**
- `*rate limit*` → **429**
- everything else → **500**

Frontend callers should always handle `success === false` and the `message` field.
