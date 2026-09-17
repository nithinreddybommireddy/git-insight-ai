#!/usr/bin/env node
/**
 * Self-terminating print-layout verification harness.
 *
 * Serves the production build (frontend/dist) on a throwaway loopback port,
 * runs scripts/print-audit.cjs against it (Chromium print-to-PDF + metrics),
 * then shuts down and exits. Never leaves a server running.
 *
 * Usage: node scripts/print-verify.cjs [/tmp/out/prefix] [username]
 */
const http = require("http");
const fs = require("fs");
const path = require("path");
const { spawn } = require("child_process");

const outPrefix = process.argv[2] || "/tmp/printfix/after";
const username = process.argv[3] || "torvalds";
const PORT = 4173;
const ROOT = path.join(__dirname, "..", "dist");

const MIME = {
  ".html": "text/html",
  ".js": "text/javascript",
  ".css": "text/css",
  ".svg": "image/svg+xml",
  ".png": "image/png",
  ".json": "application/json",
  ".webmanifest": "application/manifest+json",
  ".woff2": "font/woff2",
};

const server = http.createServer((req, res) => {
  let urlPath = decodeURIComponent(req.url.split("?")[0]);
  if (urlPath.endsWith("/")) urlPath += "index.html";
  let file = path.join(ROOT, urlPath);
  if (!file.startsWith(ROOT) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) {
    file = path.join(ROOT, "index.html"); // SPA fallback
  }
  res.writeHead(200, { "Content-Type": MIME[path.extname(file)] || "application/octet-stream" });
  fs.createReadStream(file).pipe(res);
});

server.listen(PORT, "127.0.0.1", () => {
  const child = spawn(
    process.execPath,
    [path.join(__dirname, "print-audit.cjs"), `http://127.0.0.1:${PORT}`, outPrefix, username],
    { stdio: "inherit" }
  );
  child.on("exit", (code) => server.close(() => process.exit(code)));
  child.on("error", (e) => {
    console.error(e);
    server.close(() => process.exit(1));
  });
});
