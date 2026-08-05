#!/usr/bin/env bun
/**
 * verify-home.mjs
 * ---------------
 * Load the home page in headless Chromium and verify it renders correctly:
 *   1. No console errors / page errors
 *   2. All landing sections present (hero, stats, features, how-it-works, CTA)
 *   3. No layout anomalies (horizontal overflow, zero-height sections)
 *   4. Full-page screenshot saved to /tmp/gitinsight-home.png
 *
 * Usage (from frontend/):
 *   CHROME_BIN=... bun scripts/verify-home.mjs
 * Env: BASE_URL (default http://localhost:5173), CHROME_BIN
 */
import { chromium } from "playwright";

const BASE = process.env.BASE_URL || "http://localhost:5173";
const CHROME = process.env.CHROME_BIN || "";

const consoleErrors = [];
const pageErrors = [];
let failed = 0;

const browser = await chromium.launch({
  headless: true,
  ...(CHROME ? { executablePath: CHROME } : {}),
  args: ["--no-sandbox", "--disable-gpu", "--disable-dev-shm-usage"],
});
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });

page.on("console", (msg) => {
  if (msg.type() === "error") consoleErrors.push(msg.text());
});
page.on("pageerror", (err) => pageErrors.push(String(err)));

const out = [];
const check = (ok, label, detail = "") => {
  out.push(`${ok ? "✅" : "❌"} ${label}${detail ? ` — ${detail}` : ""}`);
  if (!ok) failed++;
};

try {
  const resp = await page.goto(BASE, { waitUntil: "networkidle", timeout: 45000 });
  check(resp && resp.status() < 400, "Page loads", `HTTP ${resp?.status()}`);
  await page.waitForTimeout(1200); // let entrance animations finish

  // 1. Errors
  check(consoleErrors.length === 0, "No console errors", consoleErrors.slice(0, 3).join(" | "));
  check(pageErrors.length === 0, "No page errors", pageErrors.slice(0, 3).join(" | "));

  // 2. Sections present
  const sections = [
    ["Hero heading", "Understand Any Developer's GitHub Profile"],
    ["Hero search form", "Enter a GitHub username"],
    ["Stats cards", "Profiles Analyzed"],
    ["Features heading", "Everything You Need to"],
    ["Feature card", "GitHub Profile Analysis"],
    ["Feature card", "Compare Developers"],
    ["How It Works", "How It"],
    ["CTA heading", "Ready to Explore Developer Analytics?"],
    ["Navbar", "GitInsight"],
    ["Single footer", null], // counted separately below
  ];
  for (const [label, text] of sections) {
    if (label === "Single footer") {
      const n = await page.$$eval("footer", (els) => els.length);
      check(n === 1, "Single footer", `found ${n}`);
      continue;
    }
    const found = text
      ? await page.locator(`body :text("${text}")`).first().count().then((c) => c > 0)
      : true;
    check(found, `${label} visible`, text);
  }

  // 3. Layout anomalies
  const layout = await page.evaluate(() => {
    const doc = document.documentElement;
    const overflowX = doc.scrollWidth > doc.clientWidth + 1;
    const zeroSections = [...document.querySelectorAll("section")]
      .filter((s) => s.getBoundingClientRect().height < 5)
      .length;
    const imgs = [...document.querySelectorAll("img")];
    const brokenImgs = imgs.filter((i) => !i.complete || i.naturalWidth === 0).length;
    return { overflowX, zeroSections, totalSections: document.querySelectorAll("section").length, brokenImgs };
  });
  check(!layout.overflowX, "No horizontal overflow", `scroll ${docW} vs ${layout.scrollW}`);
  check(layout.zeroSections === 0, "No zero-height sections", `${layout.zeroSections} collapsed`);
  check(layout.brokenImgs === 0, "No broken images", `${layout.brokenImgs} broken`);

  await page.screenshot({ path: "/tmp/gitinsight-home.png", fullPage: true });
  out.push(`📸 Screenshot saved: /tmp/gitinsight-home.png (${layout.totalSections} sections)`);

  out.push("-".repeat(60));
  out.push(`RESULT: ${failed === 0 ? "HOME PAGE OK ✓" : "ISSUES DETECTED"} (${failed} problems)`);
} catch (e) {
  out.push(`❌ Script error: ${String(e).slice(0, 200)}`);
  failed++;
} finally {
  await browser.close();
}

console.log(out.join("\n"));
process.exit(failed === 0 ? 0 : 1);
