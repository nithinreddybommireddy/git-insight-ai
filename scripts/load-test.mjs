#!/usr/bin/env node
/**
 * GitInsight-AI — lightweight load test for github-service.
 *
 * Dependency-free (global fetch, Node >= 18). Run against a running stack
 * (start-dev.sh or Docker) and read the summary. GitHub API calls are the real
 * bottleneck, so run WITH GITHUB_TOKEN set on github-service and expect cached
 * endpoints to be much faster than cold ones — that difference IS the point.
 *
 * Env:
 *   BASE_URL          github-service base        (default http://localhost:8081)
 *   CONCURRENCY       parallel workers           (default 5)
 *   DURATION_SECONDS  timed test length          (default 30)
 *   USERNAMES         comma-separated            (default torvalds,gaearon,addyosmani)
 *   ORGS              comma-separated            (default facebook,google)
 *
 * Example:
 *   BASE_URL=http://localhost:8081 CONCURRENCY=10 DURATION_SECONDS=60 \
 *     node scripts/load-test.mjs
 */
const BASE = process.env.BASE_URL ?? "http://localhost:8081";
const CONCURRENCY = Number(process.env.CONCURRENCY ?? 5);
const DURATION = Number(process.env.DURATION_SECONDS ?? 30);
const USERNAMES = (process.env.USERNAMES ?? "torvalds,gaearon,addyosmani").split(",").filter(Boolean);
const ORGS = (process.env.ORGS ?? "facebook,google").split(",").filter(Boolean);

const pick = (arr) => arr[Math.floor(Math.random() * arr.length)];

// Weighted mix of the public surfaces. Score/commits/languages dominate because
// they are what real users hit; org analytics is the heaviest single call.
const ROUTES = [
  { path: () => "/api/health", weight: 5 },
  { path: () => `/api/github/${pick(USERNAMES)}/score`, weight: 3 },
  { path: () => `/api/github/${pick(USERNAMES)}/commits/analytics`, weight: 2 },
  { path: () => `/api/github/${pick(USERNAMES)}/languages`, weight: 2 },
  { path: () => `/api/github/${pick(USERNAMES)}/profile`, weight: 2 },
  { path: () => `/api/github/${pick(USERNAMES)}/repos`, weight: 1 },
  { path: () => `/api/github/org/${pick(ORGS)}/overview`, weight: 1 },
  { path: () => "/api/github/rate-limit", weight: 1 },
];
const TOTAL_WEIGHT = ROUTES.reduce((sum, r) => sum + r.weight, 0);
const pickRoute = () => {
  let n = Math.random() * TOTAL_WEIGHT;
  for (const r of ROUTES) {
    n -= r.weight;
    if (n <= 0) return r;
  }
  return ROUTES[0];
};

const latencies = [];
const statuses = new Map();
const errors = [];
let inflight = 0;

async function requestOnce() {
  const route = pickRoute();
  const url = BASE + route.path();
  const start = performance.now();
  inflight++;
  try {
    const res = await fetch(url, { signal: AbortSignal.timeout(60_000) });
    await res.arrayBuffer(); // consume body
    const ms = performance.now() - start;
    latencies.push(ms);
    statuses.set(res.status, (statuses.get(res.status) ?? 0) + 1);
  } catch (err) {
    errors.push(`${url}: ${err?.message ?? err}`);
  } finally {
    inflight--;
  }
}

async function warmup() {
  // Fill the in-process caches once so the timed run measures steady-state
  // (cached) throughput rather than cold GitHub calls.
  for (const route of ROUTES) {
    try {
      await fetch(BASE + route.path(), { signal: AbortSignal.timeout(60_000) });
    } catch {
      /* cold path may fail on missing data; ignore during warmup */
    }
  }
}

function percentile(sorted, p) {
  if (sorted.length === 0) return 0;
  const idx = Math.min(sorted.length - 1, Math.max(0, Math.ceil((p / 100) * sorted.length) - 1));
  return sorted[idx];
}

async function main() {
  console.log(`GitInsight-AI load test — ${BASE}`);
  console.log(`Concurrency: ${CONCURRENCY} | Duration: ${DURATION}s | Users: [${USERNAMES}] | Orgs: [${ORGS}]`);
  console.log("Warming caches...");
  await warmup();

  const startedAt = performance.now();
  const deadline = startedAt + DURATION * 1000;
  const workers = Array.from({ length: CONCURRENCY }, async () => {
    while (performance.now() < deadline) {
      await requestOnce();
    }
  });
  await Promise.all(workers);

  const elapsed = (performance.now() - startedAt) / 1000;
  const sorted = [...latencies].sort((a, b) => a - b);
  const total = latencies.length;

  console.log("\n=== Summary ===");
  console.log(`Requests:      ${total}`);
  console.log(`Elapsed:       ${elapsed.toFixed(1)}s`);
  console.log(`Throughput:    ${(total / elapsed).toFixed(1)} req/s`);
  console.log(`Latency P50:   ${percentile(sorted, 50).toFixed(0)} ms`);
  console.log(`Latency P90:   ${percentile(sorted, 90).toFixed(0)} ms`);
  console.log(`Latency P95:   ${percentile(sorted, 95).toFixed(0)} ms`);
  console.log(`Latency P99:   ${percentile(sorted, 99).toFixed(0)} ms`);
  console.log("Status codes:");
  for (const [code, count] of [...statuses.entries()].sort((a, b) => b[1] - a[1])) {
    console.log(`  ${code}: ${count}`);
  }
  const pct5xx = [...statuses.entries()]
    .filter(([c]) => c >= 500)
    .reduce((sum, [, n]) => sum + n, 0);
  const errorRate = total > 0 ? (pct5xx / total) * 100 : 0;
  console.log(`5xx rate:      ${errorRate.toFixed(2)}%`);
  if (errors.length > 0) {
    console.log(`\nNetwork errors (${errors.length}):`);
    for (const e of errors.slice(0, 10)) console.log(`  ${e}`);
  }
}

main().catch((err) => {
  console.error("Load test failed:", err);
  process.exit(1);
});
