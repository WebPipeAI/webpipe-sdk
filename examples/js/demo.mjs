#!/usr/bin/env node
/** WebPipe SDK quickstart demo (Node.js ≥ 18).
 *
 * Setup:
 *   cd ../../packages/js && npm install && npm run build   # build the SDK once
 *   cd - && npm install                                    # links the local SDK
 *
 * API key (checked in this order — get one at https://webpipe.ai/api-keys.html):
 *   1. --api-key wc-...            flag
 *   2. WEBPIPE_API_KEY=wc-...      environment variable
 *   3. interactive prompt / pipe:  echo wc-... | node demo.mjs scrape
 *
 * Usage (every command has a built-in demo URL, or pass your own):
 *   node demo.mjs scrape [url] [--api-key wc-...]    # default https://example.com
 *   node demo.mjs map [url] [--api-key wc-...]       # default https://nginx.org
 *   node demo.mjs crawl [url] [--api-key wc-...]     # default https://quotes.toscrape.com
 *   node demo.mjs browser [--api-key wc-...]         # login-flow demo on quotes.toscrape.com
 */

import readline from "node:readline";
import { Webpipe, WebpipeError } from "webpipe-sdk";

const DEFAULT_URLS = {
  scrape: "https://example.com",
  map: "https://nginx.org",
  crawl: "https://quotes.toscrape.com",
};

async function cmdScrape(client, url) {
  console.log(`→ Scraping ${url} ...`);
  const doc = await client.scrape(url, { formats: ["markdown", "metadata", "links"] });
  console.log(`  title:  ${doc.metadata?.title}`);
  console.log(`  status: ${doc.metadata?.statusCode}`);
  console.log(`  links:  ${doc.links?.length ?? 0}`);
  console.log("--- markdown (first 400 chars) ---");
  console.log((doc.markdown ?? "").slice(0, 400));
}

async function cmdMap(client, url) {
  console.log(`→ Mapping ${url} ...`);
  const links = await client.map(url, { limit: 50 });
  console.log(`  found ${links.length} URLs (limit 50):`);
  for (const l of links.slice(0, 10)) console.log(`  - ${l.url}`);
  if (links.length > 10) console.log(`  ... and ${links.length - 10} more`);
}

async function cmdCrawl(client, url) {
  console.log(`→ Crawling ${url} (limit 5, polling until done) ...`);
  const job = await client.crawl(url, {
    limit: 5,
    scrapeOptions: { formats: ["markdown", "metadata"] },
    timeout: 300,
  });
  console.log(`  done: ${job.completed}/${job.total} pages`);
  for (const page of job.data) {
    console.log(`  - ${page.metadata?.sourceURL} | ${page.metadata?.title ?? ""}`);
  }
}

async function cmdBrowser(client) {
  const url = "https://quotes.toscrape.com/login";
  console.log(`→ Browser login demo on ${url} (any credentials work) ...`);
  const session = await client.browser.create({ url });
  try {
    await session.waitUntilRunning({ timeout: 90 });
    console.log("  session running, filling the login form ...");
    await session.fill("input[name=username]", "demo");
    await session.fill("input[name=password]", "demo");
    await session.click("input[type=submit]", { waitAfter: 2000 });
    const doc = await session.scrape({ formats: ["markdown"] });
    const ok = (doc.markdown ?? "").includes("Logout");
    console.log(`  login ${ok ? "succeeded ✓ (Logout link found)" : "— check output below"}`);
    console.log("--- markdown after login (first 300 chars) ---");
    console.log((doc.markdown ?? "").slice(0, 300));
  } finally {
    await session.close();
  }
  console.log("  session closed.");
}

/** Resolve the API key: --api-key flag > env var > prompt/pipe. */
async function resolveApiKey(flag) {
  if (flag) return flag;
  const env = process.env.WEBPIPE_API_KEY;
  if (env) return env;
  console.log("API key not found in --api-key or WEBPIPE_API_KEY.");
  console.log("Get one at https://webpipe.ai/api-keys.html");
  if (process.stdin.isTTY) {
    const rl = readline.createInterface({ input: process.stdin, output: process.stdout });
    const answer = await new Promise((resolve) =>
      rl.question("Enter your API key (wc-...): ", resolve),
    );
    rl.close();
    return answer.trim();
  }
  // Piped input, e.g. `echo wc-... | node demo.mjs scrape`
  let data = "";
  for await (const chunk of process.stdin) data += chunk;
  const piped = data.trim();
  if (piped) return piped;
  console.error("error: no API key provided");
  process.exit(1);
}

// Parse argv: positionals = <command> [url], plus --api-key <key>
const argv = process.argv.slice(2);
const positionals = [];
let apiKeyFlag;
for (let i = 0; i < argv.length; i++) {
  if (argv[i] === "--api-key" && i + 1 < argv.length) {
    apiKeyFlag = argv[++i];
  } else {
    positionals.push(argv[i]);
  }
}
const [command, url] = positionals;
const usage = "usage: node demo.mjs <scrape|map|crawl|browser> [url] [--api-key wc-...]";
if (!command || (!(command in DEFAULT_URLS) && command !== "browser")) {
  console.error(usage);
  process.exit(1);
}

let client;
try {
  client = new Webpipe({ apiKey: await resolveApiKey(apiKeyFlag) });
} catch (e) {
  console.error(`error: ${e.message}`);
  process.exit(1);
}

try {
  if (command === "browser") await cmdBrowser(client);
  else if (command === "scrape") await cmdScrape(client, url ?? DEFAULT_URLS.scrape);
  else if (command === "map") await cmdMap(client, url ?? DEFAULT_URLS.map);
  else await cmdCrawl(client, url ?? DEFAULT_URLS.crawl);
} catch (e) {
  console.error(e instanceof WebpipeError ? `webpipe error: ${e.message}` : e);
  process.exit(1);
}
