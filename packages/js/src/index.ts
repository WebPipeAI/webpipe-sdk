/** webpipe-sdk — the official TypeScript/JavaScript SDK for WebPipe.ai.
 *
 * ```ts
 * import { Webpipe } from "webpipe-sdk";
 * const client = new Webpipe({ apiKey: "wc-..." });
 * const doc = await client.scrape("https://example.com", { formats: ["markdown"] });
 * ```
 */

export { Webpipe } from "./client.js";
export type { WebpipeConfig } from "./client.js";
export { BrowserResource, BrowserSession } from "./browser.js";
export {
  APIError,
  AuthenticationError,
  BadRequestError,
  CrawlError,
  ForbiddenError,
  NotFoundError,
  RateLimitError,
  ServerError,
  WebpipeError,
  WebpipeTimeoutError,
} from "./errors.js";
export type {
  BrowserAction,
  Cookie,
  CrawlAndWaitOptions,
  CrawlJob,
  CrawlJobStart,
  CrawlOptions,
  CrawlStatus,
  CreateSessionOptions,
  Document,
  Link,
  MapLink,
  MapOptions,
  Metadata,
  NavigateOptions,
  ScrapeFormat,
  ScrapeOptions,
  SessionHistoryEntry,
  SessionInfo,
  SessionStatus,
  SitemapPolicy,
  WaitUntilRunningOptions,
} from "./types.js";
