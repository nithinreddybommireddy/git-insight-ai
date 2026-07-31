#!/usr/bin/env bun
/**
 * verify-footer.mjs
 * -----------------
 * Click every link in the <footer> with headless Chromium, verify each one
 * routes to the correct page, AND assert that exactly ONE <footer> renders
 * on every visited page (no duplicate footers).
 *
 * Usage (from frontend/):
 *   bun scripts/verify-footer.mjs
 *
 * Env overrides:
 *   BASE_URL   – app base URL (default http://localhost:5173)
 *   CHROME_BIN – explicit chromium binary path (default: playwright's bundled build)
 */
import { chromium } from "playwright";

const BASE = process.env.BASE_URL || "http://localhost:5173";
const CHROME = process.env.CHROME_BIN || "";
const PROTECTED = new Set(["/dashboard", "/recruiter", "/reports", "/reports/:username"]);

const results = [];
const pageFooters = {}; // pathname -> count of <footer> elements observed
let failed = 0;

const browser = await chromium.launch({
  headless: true,
  ...(CHROME ? { executablePath: CHROME } : {}),
  args: ["--no-sandbox", "--disable-gpu", "--disable-dev-shm-usage"],
});
const page = await browser.newPage();

async function footerCount() {
  return page.$$eval("footer", (els) => els.length);
}

async function recordFooter(pathname) {
  const count = await footerCount();
  pageFooters[pathname] = (pageFooters[pathname] || 0) + count;
  return count;
}

async function goHome() {
  await page.goto(BASE, { waitUntil: "domcontentloaded", timeout: 30000 });
  await page.waitForSelector("footer", { timeout: 15000 });
  await recordFooter("/");
}

async function collectLinks() {
  return page.$$eval("footer a", (els) =>
    els.map((a) => ({
      text: (a.textContent || "").trim().replace(/\s+/g, " "),
      href: a.getAttribute("href"),
      target: a.getAttribute("target") || "",
    }))
  );
}

function expectedPath(href) {
  const path = new URL(href, BASE).pathname;
  if (PROTECTED.has(path)) return "/login"; // RequireAuth redirect for signed-out users
  return path;
}

await goHome();
const links = await collectLinks();

for (const l of links) {
  const isExternal = l.target === "_blank" || /^https?:\/\//.test(l.href || "");
  const label = l.text || l.href || "(logo link)";

  try {
    if (isExternal) {
      // External links open a new tab (target=_blank)
      const [popup] = await Promise.all([
        page.waitForEvent("popup", { timeout: 10000 }),
        page.locator(`footer a[href="${l.href}"]`).first().click(),
      ]);
      await popup.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => {});
      await page.waitForTimeout(500);
      const popupUrl = popup.url();
      const ok = popupUrl.startsWith(l.href.split("#")[0].split("?")[0]);
      const note = popupUrl.startsWith("chrome-error") || popupUrl === "about:blank"
        ? " (tab opened; github.com unreachable from sandbox)"
        : "";
      results.push({ mark: ok ? "✅" : "⚠️", label, href: l.href, ok: ok || note !== "", detail: popupUrl + note });
      if (!ok) failed++;
      await popup.close().catch(() => {});
      await goHome();
    } else {
      await goHome();
      const exp = expectedPath(l.href);
      await page.locator(`footer a[href="${l.href}"]`).first().click({ timeout: 5000 });
      const landed = await page
        .waitForURL((u) => new URL(u).pathname === exp, { timeout: 8000 })
        .then(() => true)
        .catch(() => false);
      const finalPath = new URL(page.url()).pathname;
      await recordFooter(finalPath);
      const ok = landed && finalPath === exp;
      results.push({
        mark: ok ? "✅" : "❌",
        label,
        href: l.href,
        ok,
        detail: `expected ${exp} → got ${finalPath}`,
      });
      if (!ok) failed++;
    }
  } catch (e) {
    results.push({ mark: "❌", label, href: l.href, ok: false, detail: String(e).slice(0, 160) });
    failed++;
    await goHome().catch(() => {});
  }
}

await browser.close();

let out = "\nFOOTER LINK ROUTING VERIFICATION\n" + "=".repeat(72) + "\n";
for (const r of results) {
  out += `${r.mark} ${r.label}\n`;
  out += `     href: ${r.href}\n`;
  out += `     ${r.detail}\n`;
}
out += "-".repeat(72) + "\n";

// ── Single-footer assertion per page ────────────────────────────────
out += "SINGLE-FOOTER CHECK (exactly 1 <footer> per page)\n";
let footerOk = true;
for (const [path, total] of Object.entries(pageFooters).sort()) {
  const ok = total === 1;
  if (!ok) footerOk = false;
  out += `${ok ? "✅" : "❌"} ${path || "/"}: observed ${total} <footer> total across visits\n`;
}
if (Object.keys(pageFooters).length === 0) {
  footerOk = false;
  out += "❌ no pages were visited\n";
}
out += "-".repeat(72) + "\n";
out += `RESULT: ${failed === 0 && footerOk ? "ALL PASS ✓" : "FAILURES DETECTED"}  (${results.length} links, ${Object.keys(pageFooters).length} pages checked)\n`;
console.log(out);
process.exit(failed === 0 && footerOk ? 0 : 1);
