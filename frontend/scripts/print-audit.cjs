#!/usr/bin/env node
/**
 * Print-layout verification harness (read-only tool; writes only to /tmp).
 *
 * Usage: node scripts/print-audit.cjs <baseURL> <outPrefix> [username] [theme]
 *   baseURL   e.g. http://127.0.0.1:4173 (Vite preview of the current build)
 *   outPrefix e.g. /tmp/printfix/before
 * Captures:
 *   <prefix>.pdf                       — Chrome print-to-PDF of the Reports page
 *   <prefix>-printmode.png             — screenshot with print media emulation
 *   <prefix>-screen.png                — screenshot with screen media emulation
 *   <prefix>.json                      — measured layout metrics
 */
const { chromium } = require("playwright-core");
const fs = require("fs");

const [, , baseURL, outPrefix, usernameArg, themeArg] = process.argv;
if (!baseURL || !outPrefix) {
  console.error("usage: node scripts/print-audit.cjs <baseURL> <outPrefix> [username] [theme]");
  process.exit(2);
}
const USERNAME = usernameArg || "torvalds";
const THEME = themeArg === "dark" ? "dark" : "light";

const NW = "109.2924.076"; // Chromium 109-era UA marker

// ---------- fixtures (shape-matched to frontend/src/services/api.ts) ----------
const snapshot = (id, score, daysAgo) => ({
  id,
  username: USERNAME,
  displayName: null,
  overallScore: score,
  level: "Expert 💼",
  contributionRecency: 95, commitFrequency: 78, repositoryHealth: 64, repositoryQuality: 71,
  contributionConsistency: 82, languageDiversity: 69, collaboration: 74, openSourceImpact: 58,
  popularity: 77, maintenance: 66, totalStars: 210300, totalForks: 41200, totalRepositories: 8,
  languageCount: 8, languages: "C;Rust;Shell", createdAt: iso(daysAgo),
});
function iso(daysAgo) {
  return new Date(Date.now() - daysAgo * 86400000).toISOString();
}
const SNAPSHOTS = [snapshot(1, 84, 2), snapshot(2, 81, 9), snapshot(3, 79, 16), snapshot(4, 76, 30)];
const SCORE = {
  username: USERNAME, overallScore: 84, level: "Expert 💼",
  contributionRecency: 95, commitFrequency: 78, repositoryHealth: 64, repositoryQuality: 71,
  contributionConsistency: 82, languageDiversity: 69, collaboration: 74, openSourceImpact: 58,
  popularity: 77, maintenance: 66,
  contributionRecencyDetails: { score: 95, weight: 15, label: "Contribution Recency", description: "d", explanation: "e", improvementSuggestion: "i", trend: "up", icon: "activity" },
  commitFrequencyDetails: { score: 78, weight: 10, label: "Commit Frequency", description: "d", explanation: "e", improvementSuggestion: "i", trend: "up", icon: "git-commit" },
  repositoryHealthDetails: { score: 64, weight: 10, label: "Repository Health", description: "d", explanation: "e", improvementSuggestion: "i", trend: "stable", icon: "heart-pulse" },
  repositoryQualityDetails: { score: 71, weight: 15, label: "Repository Quality", description: "d", explanation: "e", improvementSuggestion: "i", trend: "up", icon: "shield-check" },
  contributionConsistencyDetails: { score: 82, weight: 10, label: "Contribution Consistency", description: "d", explanation: "e", improvementSuggestion: "i", trend: "up", icon: "equal-approximately" },
  languageDiversityDetails: { score: 69, weight: 10, label: "Language Diversity", description: "d", explanation: "e", improvementSuggestion: "i", trend: "stable", icon: "code-2" },
  collaborationDetails: { score: 74, weight: 10, label: "Collaboration", description: "d", explanation: "e", improvementSuggestion: "i", trend: "up", icon: "users" },
  openSourceImpactDetails: { score: 58, weight: 5, label: "Open Source Impact", description: "d", explanation: "e", improvementSuggestion: "i", trend: "up", icon: "globe" },
  popularityDetails: { score: 77, weight: 10, label: "Popularity", description: "d", explanation: "e", improvementSuggestion: "i", trend: "up", icon: "trending-up" },
  maintenanceDetails: { score: 66, weight: 5, label: "Maintenance", description: "d", explanation: "e", improvementSuggestion: "i", trend: "stable", icon: "wrench" },
  insights: {
    overallAssessment: "A prolific systems engineer whose influence on open source is hard to overstate.",
    strongestSkill: "Low-level systems programming (C)",
    weakestArea: "Documentation breadth",
    collaborationAnalysis: "Maintains one of the most reviewed kernels on GitHub.",
    openSourceImpact: "Foundational projects used across the industry.",
    technologyExpertise: "C, Rust, shell tooling.",
    activityTrend: "Consistent long-term activity.",
    repositoryQualityObs: "Small, focused repository set with very high quality.",
    recommendations: "Consider broader docs and onboarding material.",
  },
};
const PROFILE = {
  githubId: 1024025, username: USERNAME, name: "Linus Torvalds",
  avatarUrl: "data:image/svg+xml;utf8," + encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" width="128" height="128"><rect width="128" height="128" fill="#334155"/><text x="64" y="76" font-size="48" text-anchor="middle" fill="#e2e8f0">LT</text></svg>'),
  profileUrl: `https://github.com/${USERNAME}`, bio: "Creator of Linux and Git.",
  company: "Linux Foundation", location: "Portland, OR", website: null, email: null,
  twitterUsername: null, hireable: null, publicRepositories: 8, publicGists: 2,
  followers: 230000, following: 0, createdAt: "2011-09-03T00:00:00Z", updatedAt: "2026-09-01T00:00:00Z",
};
const REPOS = Array.from({ length: 10 }, (_, i) => ({
  githubId: 900000 + i,
  name: ["linux", "subsurface-for-dirk", "uemacs", "ptest", "libdc-for-dirk", "logfish", "dwarves", "sparse", "l2test", "histogram"][i],
  fullName: `${USERNAME}/${["linux", "subsurface-for-dirk", "uemacs", "ptest", "libdc-for-dirk", "logfish", "dwarves", "sparse", "l2test", "histogram"][i]}`,
  description: i % 2 ? "A long description line that should wrap across the column and exercise text layout in the PDF." : null,
  htmlUrl: `https://github.com/${USERNAME}/r${i}`, homepage: null,
  language: ["C", "C", "C", null, "C", "Shell", "C", "C", null, "Python"][i],
  fork: i % 3 === 0, defaultBranch: "master", stars: 180000 - i * 900, forks: 5200 - i * 37,
  openIssues: 12 + i, watchers: 100 + i, size: 1024 * (i + 3),
  topics: [], hasLicense: true, createdAt: "2011-09-03T00:00:00Z", updatedAt: "2026-08-01T00:00:00Z",
}));
const USER = { id: 1, email: "audit@example.com", name: "Audit User", avatarUrl: null, role: "USER", githubUsername: null, createdAt: "2026-01-01T00:00:00Z" };

// ---------- server routes ----------
async function mock(page) {
  const j = (o) => ({ contentType: "application/json", body: JSON.stringify(o) });
  await page.route("**/api/auth/me**", (r) => r.fulfill(j({ success: true, message: "ok", data: USER })));
  // NOTE: Playwright runs matching routes in reverse registration order (LIFO),
  // so the broad catch-all must be registered FIRST and specific routes last.
  await page.route(`**/api/reports/**`, (r) => r.fulfill(j({ success: true, message: "ok", data: [] })));
  await page.route("**/api/reports/stats**", (r) => r.fulfill(j({ success: true, message: "ok", data: { totalSnapshots: 4, uniqueUsers: 1, averageScore: 80 } })));
  await page.route(`**/api/reports/generate/${USERNAME}`, (r) =>
    r.fulfill(j({ success: true, message: "ok", data: { score: SCORE, profile: PROFILE, repos: REPOS, history: SNAPSHOTS, recorded: SNAPSHOTS[0] } })));
}

(async () => {
  const browser = await chromium.launch({ args: ["--no-sandbox"] });
  const ctx = await browser.newContext({
    viewport: { width: 1280, height: 900 },
    userAgent: `Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/${NW} Safari/537.36`,
    deviceScaleFactor: 1,
    colorScheme: "light",
  });
  const page = await ctx.newPage();
  page.on("pageerror", (e) => console.error("PAGE ERROR:", e.message));
  await mock(page);
  await page.goto(`${baseURL}/reports/${USERNAME}`, { waitUntil: "networkidle" });

  // wait for report content
  await page.waitForSelector("text=Score Snapshots", { timeout: 15000 });
  await page.waitForTimeout(700); // let framer-motion transitions settle

  // ---------- screen screenshot ----------
  await page.screenshot({ path: `${outPrefix}-screen.png`, fullPage: false });

  // ---------- print-media screenshot (pre-PDF layout state) ----------
  await page.emulateMedia({ media: "print" });
  await page.waitForTimeout(400);
  await page.screenshot({ path: `${outPrefix}-printmode.png`, fullPage: true });

  // ---------- metrics under print media ----------
  const metrics = await page.evaluate(() => {
    const out = {};
    const q = (s) => document.querySelector(s);
    const nav = q('nav');
    const footer = q("footer");
    const bodyW = document.documentElement.clientWidth;
    if (nav) { const r = nav.getBoundingClientRect(); out.navVisible = !!(r.width && r.height); }
    if (footer) { const r = footer.getBoundingClientRect(); out.footerVisible = !!(r.width && r.height); }

    // Probe the printable report container — supports both the legacy layout
    // (.print-report) and the fixed layout ([data-print-report="true"]).
    const report = q('[data-print-report="true"]') || q(".print-report");
    if (report) {
      const r = report.getBoundingClientRect();
      out.reportLeftPx = Math.round(r.left);
      out.reportWidthPx = Math.round(r.width);
      out.bodyWidthPx = bodyW;
      out.leftBlankPct = Math.round((r.left / bodyW) * 100);
      // inner content container (max-w-4xl) shows the effective text column
      const inner = report.querySelector(".max-w-4xl") || report;
      const ir = inner.getBoundingClientRect();
      out.innerLeftPx = Math.round(ir.left);
      out.innerWidthPx = Math.round(ir.width);
      out.innerLeftPct = Math.round((ir.left / bodyW) * 100);
    }
    out.reportFound = !!report;
    out.legacyPrintReportPresent = !!q(".print-report");

    // Buttons visible under print media (should be 0 after the fix)
    const btn = [...document.querySelectorAll("button")].filter((b) => {
      const r = b.getBoundingClientRect();
      return r.width && r.height && /Print|Export PDF|Refresh|Generate Report|Back/.test(b.textContent || "");
    });
    out.printButtonsVisible = btn.length;

    // Headings in print flow (screen chrome like "Developer Reports" and footer
    // "Product/Account/Resources" should disappear after the fix)
    out.headings = [...document.querySelectorAll("h1,h2,h3")].map((h) => (h.textContent || "").trim().replace(/\s+/g, " ")).slice(0, 16);

    // Report content completeness probes
    out.scoreRows = report ? report.querySelectorAll("span.w-44").length : 0; // score-row labels
    out.repoRows = report ? report.querySelectorAll(".bg-gray-100.rounded-lg").length : 0; // repo cards
    out.profileHeaderPresent = !!report && /Linus Torvalds/.test(report.textContent || "");
    out.aiInsightsPresent = !!report && /Strongest:/.test(report.textContent || "");
    // Does any score-history TABLE survive into print? (should NOT after fix)
    out.historyTableInPrint = [...document.querySelectorAll("table")].some((t) => {
      const r = t.getBoundingClientRect();
      return r.width && r.height;
    });
    return out;
  });
  fs.writeFileSync(`${outPrefix}.json`, JSON.stringify(metrics, null, 2));

  // ---------- print to PDF (Chrome print-to-PDF pipeline) ----------
  await page.pdf({
    path: `${outPrefix}.pdf`,
    format: "A4",
    printBackground: true,
    preferCSSPageSize: false,
  });
  await browser.close();
  console.log(JSON.stringify(metrics, null, 2));
})().catch((e) => { console.error(e); process.exit(1); });
