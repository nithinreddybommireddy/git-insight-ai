# 📊 Developer Scoring Engine (Phase 1)

`github-service/src/main/java/com/gitinsight/githubservice/service/ScoringEngine.java`

## Overall Score

```
Overall Score =
  Contribution Recency  × 0.15
+ Commit Frequency      × 0.15
+ Repository Health     × 0.15
+ Repository Quality    × 0.10
+ Contribution Consistency × 0.10
+ Language Diversity    × 0.10
+ Collaboration Score   × 0.10
+ Open Source Impact    × 0.05
+ Popularity            × 0.05
+ Maintenance           × 0.05
```

Clamped to **0–100**. Weights are public constants in `ScoringEngine` — rebalance without touching business logic.

## Repository Filtering Rules

Only the following repos contribute to scoring (implemented in `ScoringEngine.calculate`):

- ❌ Forked repositories
- ❌ Archived repositories
- ❌ Empty repositories (`size == 0`)
- ✅ All other original repositories

> ℹ️ `archived`/`disabled` are mapped from the real GitHub API values in `GitHubServiceImpl.mapToRepoResponse` so filtering works correctly.

## The 10 Metrics

### 1. Contribution Recency (15%)
```
active30 = repos pushed within last 30 days
active90 = repos pushed within last 90 days
score = (active30 / total × 60) + (active90 / total × 40)
```

### 2. Commit Frequency (15%)
Average days since last push → score:

| Avg days | Score |
|----------|-------|
| ≤ 7  | 100 |
| ≤ 14 | 90  |
| ≤ 30 | 75  |
| ≤ 60 | 55  |
| ≤ 90 | 35  |
| ≤ 180| 20  |
| > 180| 5   |

### 3. Repository Health (15%)
Per-repo points for: description, license, topics, homepage, size, stars, forks, recent push → averaged.

### 4. Repository Quality (10%)
Per-repo points for: description length, homepage, topics, license, codebase size, community validation → averaged.

### 5. Contribution Consistency (10%)
```
score = (repos with activityScore ≥ 30) / totalRepos × 100
```

### 6. Language Diversity (10%)
Byte-weighted (from `/repos/{o}/{r}/languages`): count bonus + Shannon entropy of byte share.
Falls back to repo-size heuristic when language data is unavailable.

### 7. Collaboration (10%)
Forks, open issues, forked-elsewhere count, watchers, and **external contributors**
(from `/contributors`, excluding the developer themself — added in the enrichment update).

### 8. Open Source Impact (5%)
Stars (`/10`, cap 50) + forks (`/3`, cap 20) + watchers (cap 15) + repo count (cap 15).

### 9. Popularity (5%)
Followers thresholds, total stars, watchers.

### 10. Maintenance (5%)
Per-repo: recency of push, open-issue count, size, forks → averaged.

## Developer Levels

| Score | Level |
|-------|-------|
| 90–100 | Elite 🏆 |
| 80–89  | Expert 🏅 |
| 65–79  | Advanced 🚀 |
| 50–64  | Proficient 💼 |
| 35–49  | Intermediate 📘 |
| 20–34  | Beginner 🌱 |
| 0–19   | Newcomer 🌟 |

## Response Shape

```json
{
  "username": "torvalds",
  "overallScore": 84,
  "level": "Expert 🏅",
  "contributionRecency": 90, "...": 0,
  "contributionRecencyDetails": { "score": 90, "weight": 15, "label": "...", "explanation": "...", "improvementSuggestion": "...", "trend": "up", "icon": "activity" },
  "insights": { "overallAssessment": "...", "strongestSkill": "...", "recommendations": "..." }
}
```

## Enriched Inputs (upstream of scoring)

Controllers fetch `EnrichedScoreData(weightedLanguages, contributors)` via
`GitHubIntegrationService.getEnrichedScoreData(repos)` before calling `calculate(...)`.
Both inputs are **optional** — `ScoringEngine` falls back to heuristics when null/empty,
so every caller stays deterministic and rate-limit friendly (cached 1 hour).
