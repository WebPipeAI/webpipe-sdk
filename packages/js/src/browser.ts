/** Browser session resource.
 *
 * A `BrowserSession` is a handle to a persistent headless Chromium on the
 * server. Create via `client.browser.create(...)`, drive it, then `close()`.
 *
 * Server-side facts:
 * - sessions expire after 30 minutes idle; at most 2 concurrent `running`;
 * - commands sent while `starting` are queued server-side (up to 60s), but
 *   prefer `waitUntilRunning()` for a controlled experience.
 */

import type { HttpClient } from "./http.js";
import { WebpipeError, WebpipeTimeoutError } from "./errors.js";
import type {
  Cookie,
  CreateSessionOptions,
  Document,
  NavigateOptions,
  SessionInfo,
  SessionStatus,
  WaitUntilRunningOptions,
} from "./types.js";

const TERMINAL_STATUSES: ReadonlySet<SessionStatus> = new Set(["stopped", "error", "expired"]);

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

/** Explicit camelCase → snake_case mapping. Never transform recursively:
 * user-defined keys (e.g. inside jsonSchema) must pass through untouched. */
function scrapeBody(opts: NavigateOptions): Record<string, unknown> {
  const body: Record<string, unknown> = {};
  if (opts.formats !== undefined) body.formats = opts.formats;
  if (opts.scrollToBottom !== undefined) body.scroll_to_bottom = opts.scrollToBottom;
  if (opts.maxScrolls !== undefined) body.max_scrolls = opts.maxScrolls;
  if (opts.scrollWait !== undefined) body.scroll_wait = opts.scrollWait;
  if (opts.scrollStep !== undefined) body.scroll_step = opts.scrollStep;
  return body;
}

function toSnake(action: Record<string, unknown>): Record<string, unknown> {
  const out: Record<string, unknown> = { type: action.type };
  const map: Record<string, string> = {
    selector: "selector",
    value: "value",
    key: "key",
    waitAfter: "wait_after",
    direction: "direction",
    amount: "amount",
    ms: "ms",
    timeout: "timeout",
    fullPage: "full_page",
    expression: "expression",
  };
  for (const [camel, snake] of Object.entries(map)) {
    if (action[camel] !== undefined) out[snake] = action[camel];
  }
  return out;
}

export class BrowserSession {
  readonly id: string;
  status: SessionStatus;
  url?: string;
  sessionName?: string;
  expiresAt?: string;

  constructor(
    private readonly http: HttpClient,
    info: SessionInfo,
  ) {
    this.id = info.id;
    this.status = info.status;
    this.url = info.url;
    this.sessionName = info.sessionName;
    this.expiresAt = info.expiresAt;
  }

  private get base(): string {
    return `/api/v1/browser/${this.id}`;
  }

  private apply(info: SessionInfo): void {
    this.status = info.status;
    this.url = info.url;
    this.sessionName = info.sessionName;
    this.expiresAt = info.expiresAt;
  }

  /** Fetch the latest session state from the server. */
  async refresh(): Promise<this> {
    this.apply((await this.http.get(this.base)) as SessionInfo);
    return this;
  }

  /** Block until the session is ready to accept commands. */
  async waitUntilRunning(options: WaitUntilRunningOptions = {}): Promise<this> {
    const timeout = (options.timeout ?? 60) * 1000;
    const pollInterval = (options.pollInterval ?? 1) * 1000;
    const deadline = Date.now() + timeout;
    for (;;) {
      await this.refresh();
      if (this.status === "running") return this;
      if (TERMINAL_STATUSES.has(this.status)) {
        throw new WebpipeError(
          `Browser session ${this.id} entered terminal status '${this.status}'`,
        );
      }
      if (Date.now() >= deadline) {
        throw new WebpipeTimeoutError(
          `Browser session ${this.id} not running after ${timeout / 1000}s`,
        );
      }
      await sleep(pollInterval);
    }
  }

  /** Stop the session and release server resources. */
  async close(): Promise<void> {
    await this.http.delete(this.base);
    this.status = "stopped";
  }

  /** Navigate to a new URL (with anti-bot handling) and scrape it. */
  async navigate(url: string, options: NavigateOptions = {}): Promise<Document> {
    const body = { url, ...scrapeBody(options) };
    const res = await this.http.post(`${this.base}/navigate`, body);
    return (res.data ?? {}) as Document;
  }

  /** Scrape the current page without navigating (faster than `navigate`). */
  async scrape(options: NavigateOptions = {}): Promise<Document> {
    const res = await this.http.post(`${this.base}/scrape`, scrapeBody(options));
    return (res.data ?? {}) as Document;
  }

  // -- actions ---------------------------------------------------------------

  /** Run a raw browser action. Returns the raw API payload. */
  async action(action: { type: string } & Record<string, unknown>): Promise<any> {
    return this.http.post(`${this.base}/action`, toSnake(action));
  }

  click(selector: string, options: { waitAfter?: number } = {}): Promise<any> {
    return this.action({ type: "click", selector, ...options });
  }

  fill(selector: string, value: string): Promise<any> {
    return this.action({ type: "fill", selector, value });
  }

  select(selector: string, value: string): Promise<any> {
    return this.action({ type: "select", selector, value });
  }

  press(
    key: string,
    options: { selector?: string; waitAfter?: number } = {},
  ): Promise<any> {
    return this.action({ type: "press", key, ...options });
  }

  scroll(options: { direction?: "up" | "down"; amount?: number } = {}): Promise<any> {
    return this.action({ type: "scroll", ...options });
  }

  wait(ms = 1000): Promise<any> {
    return this.action({ type: "wait", ms });
  }

  waitFor(selector: string, options: { timeout?: number } = {}): Promise<any> {
    return this.action({ type: "wait_for", selector, ...options });
  }

  /** Take a screenshot. The API returns a base64 PNG inside the payload. */
  screenshot(options: { fullPage?: boolean } = {}): Promise<any> {
    return this.action({ type: "screenshot", ...options });
  }

  /** Evaluate a JavaScript expression in the page. */
  evaluate(expression: string): Promise<any> {
    return this.action({ type: "evaluate", expression });
  }

  hover(selector: string): Promise<any> {
    return this.action({ type: "hover", selector });
  }

  check(selector: string): Promise<any> {
    return this.action({ type: "check", selector });
  }

  // -- cookies & persistence ---------------------------------------------------

  async getCookies(): Promise<Cookie[]> {
    const res = await this.http.get(`${this.base}/cookies`);
    return (res.cookies ?? res.data ?? []) as Cookie[];
  }

  async setCookies(cookies: Cookie[]): Promise<any> {
    return this.http.post(`${this.base}/cookies`, { op: "set", cookies });
  }

  async clearCookies(): Promise<any> {
    return this.http.post(`${this.base}/cookies`, { op: "clear" });
  }

  /** Persist cookies/storage under this session's `sessionName`. */
  async saveSession(): Promise<any> {
    return this.http.post(`${this.base}/save_session`);
  }
}

/** Factory for browser sessions, exposed as `client.browser`. */
export class BrowserResource {
  constructor(private readonly http: HttpClient) {}

  /** Create a session. It starts asynchronously — call `waitUntilRunning()`. */
  async create(options: CreateSessionOptions = {}): Promise<BrowserSession> {
    const body: Record<string, unknown> = {};
    if (options.url !== undefined) body.url = options.url;
    if (options.cookie !== undefined) body.cookie = options.cookie;
    if (options.proxy !== undefined) body.proxy = options.proxy;
    if (options.sessionName !== undefined) body.session_name = options.sessionName;
    const res = await this.http.post("/api/v1/browser", body);
    return new BrowserSession(this.http, res as SessionInfo);
  }
}
