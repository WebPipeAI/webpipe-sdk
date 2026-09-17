/** Thin fetch wrapper: auth headers, retries with backoff, error mapping.
 * Private module — not exported from the package entrypoint. */

import {
  APIError,
  AuthenticationError,
  BadRequestError,
  ForbiddenError,
  NotFoundError,
  RateLimitError,
  ServerError,
} from "./errors.js";

const RETRYABLE_STATUSES = new Set([429, 500, 502, 503, 504]);

export interface HttpConfig {
  baseUrl: string;
  apiKey: string;
  /** Request timeout in seconds. */
  timeout: number;
  maxRetries: number;
  userAgent: string;
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function backoffMs(res: Response | null, attempt: number): number {
  const retryAfter = res?.headers.get("Retry-After");
  if (retryAfter) {
    const seconds = Number(retryAfter);
    if (!Number.isNaN(seconds)) return Math.max(0, seconds * 1000);
  }
  return 0.5 * 2 ** attempt * 1000;
}

async function throwForStatus(res: Response): Promise<never> {
  let message = res.statusText;
  let body: unknown;
  try {
    body = await res.json();
    if (body && typeof body === "object") {
      const err = (body as Record<string, unknown>).error;
      if (typeof err === "string" && err) message = err;
    }
  } catch {
    // non-JSON body: keep statusText
  }
  const cls =
    res.status >= 500
      ? ServerError
      : {
          400: BadRequestError,
          401: AuthenticationError,
          403: ForbiddenError,
          404: NotFoundError,
          429: RateLimitError,
        }[res.status] ?? APIError;
  throw new cls(message, res.status, body);
}

export class HttpClient {
  constructor(private readonly config: HttpConfig) {}

  async request(method: string, path: string, body?: unknown): Promise<any> {
    let attempt = 0;
    for (;;) {
      const controller = new AbortController();
      const timer = setTimeout(() => controller.abort(), this.config.timeout * 1000);
      let res: Response;
      try {
        res = await fetch(this.config.baseUrl + path, {
          method,
          headers: {
            Authorization: `Bearer ${this.config.apiKey}`,
            "Content-Type": "application/json",
            "User-Agent": this.config.userAgent,
          },
          body: body === undefined ? undefined : JSON.stringify(body),
          signal: controller.signal,
        });
      } catch (err) {
        clearTimeout(timer);
        if (attempt < this.config.maxRetries) {
          attempt += 1;
          await sleep(backoffMs(null, attempt));
          continue;
        }
        throw err;
      }
      clearTimeout(timer);

      if (RETRYABLE_STATUSES.has(res.status) && attempt < this.config.maxRetries) {
        attempt += 1;
        await sleep(backoffMs(res, attempt));
        continue;
      }
      if (res.status >= 400) await throwForStatus(res);
      if (res.status === 204) return {};
      const text = await res.text();
      if (!text) return {};
      const parsed: unknown = JSON.parse(text);
      return typeof parsed === "object" && parsed !== null ? parsed : { data: parsed };
    }
  }

  get(path: string): Promise<any> {
    return this.request("GET", path);
  }

  post(path: string, body?: unknown): Promise<any> {
    return this.request("POST", path, body);
  }

  delete(path: string): Promise<any> {
    return this.request("DELETE", path);
  }
}
