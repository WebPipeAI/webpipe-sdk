/** Types for WebPipe API payloads. */

export type ScrapeFormat = "markdown" | "html" | "rawHtml" | "links" | "metadata" | "json";

export interface ScrapeOptions {
  /** Output formats. Default: `["markdown"]`. */
  formats?: ScrapeFormat[];
  /** JSON Schema for structured extraction (requires `json` in `formats`). */
  jsonSchema?: Record<string, unknown>;
  /** Use the server-side proxy pool. */
  useProxy?: boolean;
  /** Custom proxy URL, takes precedence over the proxy pool. */
  proxy?: string;
  /** Cookie header string, e.g. `"session=abc; user=123"`. */
  cookie?: string;
}

export interface Link {
  text?: string;
  href?: string;
}

export interface Metadata {
  title?: string;
  description?: string;
  sourceURL?: string;
  statusCode?: number;
  [key: string]: unknown;
}

/** Result of scraping a single page (scrape / browser / crawl data item). */
export interface Document {
  markdown?: string;
  html?: string;
  rawHtml?: string;
  links?: Link[];
  metadata?: Metadata;
  json?: unknown;
}

export type SitemapPolicy = "include" | "only" | "exclude" | "skip";

export interface MapOptions {
  /** Max URLs to return (default 5000, max 50000). */
  limit?: number;
  /** Keyword filter over url/title/description, ranked by relevance. */
  search?: string;
  /** `"include"` (default) | `"only"` (sitemap only) | `"exclude"` (links only). */
  sitemap?: SitemapPolicy;
  /** Restrict to the same root domain (default true). */
  sameDomain?: boolean;
  useProxy?: boolean;
  proxy?: string;
  cookie?: string;
}

export interface MapLink {
  url: string;
  title?: string;
  description?: string;
}

export interface CrawlOptions {
  /** Max pages to crawl (default 100, max 10000). */
  limit?: number;
  /** Max link-discovery depth. 0 = start page only. */
  maxDiscoveryDepth?: number;
  /** Regex whitelist for URL paths. */
  includePaths?: string[];
  /** Regex blacklist for URL paths. */
  excludePaths?: string[];
  allowSubdomains?: boolean;
  allowExternalLinks?: boolean;
  /** Crawl the whole domain, not just the start URL's subtree. */
  crawlEntireDomain?: boolean;
  /** Deduplicate URLs ignoring query params. */
  ignoreQueryParams?: boolean;
  sitemap?: SitemapPolicy;
  /** Seconds between page requests. */
  delay?: number;
  /** Concurrent requests, 1–10 (default 3). */
  maxConcurrency?: number;
  /** Per-page scrape options (formats, json_schema, ...). */
  scrapeOptions?: ScrapeOptions;
  useProxy?: boolean;
  proxy?: string;
  cookie?: string;
}

/** Handle returned when a crawl job is submitted. */
export interface CrawlJobStart {
  id: string;
  url?: string;
}

export type CrawlStatus = "scraping" | "completed" | "failed";

/** Status and results of a crawl job. Data expires 24h after completion. */
export interface CrawlJob {
  status: CrawlStatus;
  total: number;
  completed: number;
  data: Document[];
  errors: unknown[];
  expiresAt?: string;
}

export interface CrawlAndWaitOptions {
  /** Seconds between status polls (default 2). */
  pollInterval?: number;
  /** Max seconds to wait before throwing {@link WebpipeTimeoutError}. */
  timeout?: number;
}

// ---------------------------------------------------------------------------
// Browser session
// ---------------------------------------------------------------------------

export interface CreateSessionOptions {
  /** Navigate to this URL right after the browser starts. */
  url?: string;
  /** Initial cookie header string injected into the browser context. */
  cookie?: string;
  proxy?: string;
  /** Named sessions share persisted login state (see `saveSession`). */
  sessionName?: string;
}

export type SessionStatus = "starting" | "running" | "stopped" | "error" | "expired";

export interface SessionHistoryEntry {
  cmd?: string;
  type?: string;
  url?: string;
  ok?: boolean;
  [key: string]: unknown;
}

export interface SessionInfo {
  id: string;
  status: SessionStatus;
  url?: string;
  title?: string;
  sessionName?: string;
  expiresAt?: string;
  lastCmdAt?: string;
  history?: SessionHistoryEntry[];
  lastData?: Record<string, unknown>;
}

export interface NavigateOptions {
  formats?: ScrapeFormat[];
  /** Scroll to the bottom to trigger lazy-loaded content. */
  scrollToBottom?: boolean;
  /** Max scroll rounds (1–30, default 10). */
  maxScrolls?: number;
  /** Wait after each scroll, ms (500–8000, default 2000). */
  scrollWait?: number;
  /** Pixels per scroll (200–5000, default 1200). */
  scrollStep?: number;
}

export interface WaitUntilRunningOptions {
  /** Max seconds to wait (default 60). */
  timeout?: number;
  /** Seconds between status polls (default 1). */
  pollInterval?: number;
}

/** A raw browser action payload. Prefer the typed helpers on `BrowserSession`. */
export type BrowserAction =
  | { type: "click"; selector: string; waitAfter?: number }
  | { type: "fill"; selector: string; value: string }
  | { type: "select"; selector: string; value: string }
  | { type: "press"; key: string; selector?: string; waitAfter?: number }
  | { type: "scroll"; direction?: "up" | "down"; amount?: number }
  | { type: "wait"; ms?: number }
  | { type: "wait_for"; selector: string; timeout?: number }
  | { type: "screenshot"; fullPage?: boolean }
  | { type: "evaluate"; expression: string }
  | { type: "hover"; selector: string }
  | { type: "check"; selector: string };

export interface Cookie {
  name: string;
  value: string;
  domain?: string;
  path?: string;
  [key: string]: unknown;
}
