# Load Testing — GitInsight-AI

## The one sentence that matters

**GitHub's API quota is the bottleneck, not the application.** Every uncached
profile view costs a batch of GitHub calls (rate-limited to 5,000 req/hr with a
token, **60 req/hr without one**). The service mitigates this with aggressive
caching and bounded fan-out — so load tests measure *cache hit rate* as much as
raw throughput.

## What is already in place (from `README.md`)

| Mechanism | Limit / TTL |
|---|---|
| Full developer score cache | 30 min per username — repeat views make **0 GitHub calls** |
| Base data cache (profile + repos) | 5 min |
| Per-repo languages / contributors | 1 h |
| Organization analytics cache | 15 min |
| Repo enrichment fan-out | 15 repos (languages), 10 repos (contributors) |
| Commit analytics fan-out | 15 repos |
| Org team activity fan-out | 8 repos (parallel virtual threads, best-effort) |
| GitHub 429 handling | retry up to 20 s per attempt, then a clear error (no hang) |

## Run the load test

Prereqs: a running stack (`./start-dev.sh` or Docker) with **`GITHUB_TOKEN` set**
on github-service, and Node >= 18.

```bash
# Default: 5 workers, 30 s, popular usernames/orgs
node scripts/load-test.mjs

# Realistic burst
BASE_URL=http://localhost:8081 CONCURRENCY=10 DURATION_SECONDS=60 \
  USERNAMES=torvalds,gaearon,addyosmani,sindresorhus,tj \
  ORGS=facebook,google,vercel \
  node scripts/load-test.mjs
```

The script warms the caches first, then runs a closed-loop (each worker fires
the next request as soon as the previous returns) and reports:

```
Requests / throughput (req/s)
Latency P50 / P90 / P95 / P99 (ms)
Status-code histogram
5xx rate (%)
Network errors
```

## Interpreting the numbers

- **First view of a username is slow, repeat views are fast.** A cold
  `/{username}/score` can take seconds (many parallel GitHub calls, possibly a
  Gemini call); the same URL 30 s later should be tens of milliseconds (cache
  hit). The difference is the caching working.
- **Expect some 429s in the histogram on cold runs** — that is GitHub's quota
  being respected, not an application failure. Re-run after warmup for
  steady-state numbers.
- **5xx rate should be ~0 %** at steady state. Non-zero 5xx on cached paths is
  a real bug.
- **P99 >> P95** on a mixed workload usually means one heavy route (org
  analytics) is in the tail — expected, and why org fan-out is capped at 8
  repos.

## GitHub budget math (why caching matters)

| Scenario | GitHub calls per request |
|---|---|
| Cached score view (within 30 min) | **0** |
| Cold profile + score (15-repo enrichment) | ~2 + 15 (languages) + up to 10 (contributors) ≈ **≤ 30** |
| Cold org analytics (8-repo team activity) | ~8–25 depending on repo count |

With a token (5,000 req/hr): a cold score costs ≤ 0.6 % of the hourly quota —
**~160 cold profiles/hr**, or effectively unlimited if users re-view cached
profiles. Without a token (60 req/hr): **~2 cold profiles/hr** — the app will
work but quota is exhausted almost immediately. `GET /api/github/rate-limit`
reports the current GitHub quota state.

## Honest scope note

The numbers above are what to expect; measure them on your own hardware against
a local stack (`node scripts/load-test.mjs`). This project was not load-tested
against a live deployment in the Freebuff sandbox, because the Spring services
require a JVM + PostgreSQL that the sandbox preview does not run — the script
and this guide are the repeatable path to producing those numbers.
