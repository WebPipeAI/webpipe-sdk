/** The WebPipe API client. */

import { BrowserResource } from "./browser.js";
import { CrawlError, WebpipeTimeoutError } from "./errors.js";
import { HttpClient } from "./http.js";
import type {
  CrawlAndWaitOptions,
  CrawlJob,
  CrawlJobStart,
  CrawlOptions,
  Document,
  MapLink,
  MapOptions,
  ScrapeOptions,
} from "./types.js";

const VERSION = "0.1.0";

export interface WebpipeConfig {
  /** API key (`wc-...`). Falls back to the `WEBPIPE_API_KEY` env var. */
  apiKey?: string;
  /** Falls back to `WEBPIPE_API_URL`, then `https://api.web2json.ai`. */
  baseUrl?: string;
  /** Request timeout in seconds (default 60; browser commands can be slow). */
  timeout?: number;
  /** Retries for 429/5xx with exponential backoff (default 2). */
  maxRetries?: number;
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

/* Explicit camelCase → snake_case request builders. Never transform
 * recursively — jsonSchema.properties holds user-defined field names. */

function scrapeBody(url: string, opts: ScrapeOptions): Record<string, unknown> {
  const body: Record<string, unknown> = { url };
  if (opts.formats !== undefined) body.formats = opts.formats;
  if (opts.jsonSchema !== undefined) body.json_schema = opts.jsonSchema;
  if (opts.useProxy !== undefined) body.use_proxy = opts.useProxy;
  if (opts.proxy !== undefined) body.proxy = opts.proxy;
  if (opts.cookie !== undefined) body.cookie = opts.cookie;
  return body;
}

function mapBody(url: string, opts: MapOptions): Record<string, unknown> {
  const body: Record<string, unknown> = { url };
  if (opts.limit !== undefined) body.limit = opts.limit;
  if (opts.search !== undefined) body.search = opts.search;
  if (opts.sitemap !== undefined) body.sitemap = opts.sitemap;
  if (opts.sameDomain !== undefined) body.same_domain = opts.sameDomain;
  if (opts.useProxy !== undefined) body.use_proxy = opts.useProxy;
  if (opts.proxy !== undefined) body.proxy = opts.proxy;
  if (opts.cookie !== undefined) body.cookie = opts.cookie;
  return body;
}

function crawlBody(url: string, opts: CrawlOptions): Record<string, unknown> {
  const body: Record<string, unknown> = { url };
  if (opts.limit !== undefined) body.limit = opts.limit;
  if (opts.maxDiscoveryDepth !== undefined) body.max_discovery_depth = opts.maxDiscoveryDepth;
  if (opts.includePaths !== undefined) body.include_paths = opts.includePaths;
  if (opts.excludePaths !== undefined) body.exclude_paths = opts.excludePaths;
  if (opts.allowSubdomains !== undefined) body.allow_subdomains = opts.allowSubdomains;
  if (opts.allowExternalLinks !== undefined) body.allow_external_links = opts.allowExternalLinks;
  if (opts.crawlEntireDomain !== undefined) body.crawl_entire_domain = opts.crawlEntireDomain;
  if (opts.ignoreQueryParams !== undefined) body.ignore_query_params = opts.ignoreQueryParams;
  if (opts.sitemap !== undefined) body.sitemap = opts.sitemap;
  if (opts.delay !== undefined) body.delay = opts.delay;
  if (opts.maxConcurrency !== undefined) body.max_concurrency = opts.maxConcurrency;
  if (opts.scrapeOptions !== undefined) {
    // Reuse the scrape builder (minus url) so nested keys are mapped too.
    const { url: _url, ...scrape } = scrapeBody("", opts.scrapeOptions);
    body.scrape_options = scrape;
  }
  if (opts.useProxy !== undefined) body.use_proxy = opts.useProxy;
  if (opts.proxy !== undefined) body.proxy = opts.proxy;
  if (opts.cookie !== undefined) body.cookie = opts.cookie;
  return body;
}

/** Top-level crawl status payload uses snake_case for `expires_at`. */
function toCrawlJob(raw: Record<string, any>): CrawlJob {
  return {
    status: raw.status,
    total: raw.total ?? 0,
    completed: raw.completed ?? 0,
    data: raw.data ?? [],
    errors: raw.errors ?? [],
    expiresAt: raw.expires_at,
  };
}

export class Webpipe {
  private readonly http: HttpClient;
  readonly browser: BrowserResource;

  constructor(config: WebpipeConfig = {}) {
    const apiKey = config.apiKey ?? process.env.WEBPIPE_API_KEY;
    if (!apiKey) {
      throw new Error(
        "Missing API key. Pass `apiKey` or set the WEBPIPE_API_KEY environment " +
          "variable. Create one at https://webpipe.ai/api-keys.html",
      );
    }
    const baseUrl = (
      config.baseUrl ??
      process.env.WEBPIPE_API_URL ??
      "https://api.web2json.ai"
    ).replace(/\/+$/, "");
    this.http = new HttpClient({
      baseUrl,
      apiKey,
      timeout: config.timeout ?? 60,
      maxRetries: config.maxRetries ?? 2,
      userAgent: `webpipe-sdk-js/${VERSION}`,
    });
    this.browser = new BrowserResource(this.http);
  }

  /** Scrape a single page into markdown/html/links/metadata/json. */
  async scrape(url: string, options: ScrapeOptions = {}): Promise<Document> {
    const res = await this.http.post("/api/v1/scrape", scrapeBody(url, options));
    return (res.data ?? {}) as Document;
  }

  /** Discover URLs of a website (robots.txt → sitemap → in-page links). */
  async map(url: string, options: MapOptions = {}): Promise<MapLink[]> {
    const res = await this.http.post("/api/v1/map", mapBody(url, options));
    return (res.links ?? []) as MapLink[];
  }

  /** Submit an async crawl job and return its handle immediately. */
  async startCrawl(url: string, options: CrawlOptions = {}): Promise<CrawlJobStart> {
    const res = await this.http.post("/api/v1/crawl", crawlBody(url, options));
    return { id: res.id, url: res.url };
  }

  /** Fetch the current status and partial results of a crawl job. */
  async getCrawlStatus(jobId: string): Promise<CrawlJob> {
    return toCrawlJob(await this.http.get(`/api/v1/crawl/${jobId}`));
  }

  /** Submit a crawl job and poll until it completes.
   *
   * Throws {@link CrawlError} if the job fails, {@link WebpipeTimeoutError}
   * if `options.timeout` (seconds) is exceeded.
   */
  async crawl(
    url: string,
    options: CrawlOptions & CrawlAndWaitOptions = {},
  ): Promise<CrawlJob> {
    const { pollInterval = 2, timeout, ...crawlOptions } = options;
    const job = await this.startCrawl(url, crawlOptions);
    const deadline = timeout === undefined ? undefined : Date.now() + timeout * 1000;
    for (;;) {
      const status = await this.getCrawlStatus(job.id);
      if (status.status === "completed") return status;
      if (status.status === "failed") {
        throw new CrawlError(`Crawl job ${job.id} failed: ${JSON.stringify(status.errors)}`);
      }
      if (deadline !== undefined && Date.now() >= deadline) {
        throw new WebpipeTimeoutError(
          `Crawl job ${job.id} did not complete within ${timeout}s`,
        );
      }
      await sleep(pollInterval * 1000);
    }
  }
}
