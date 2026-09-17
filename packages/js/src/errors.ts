/** Exception hierarchy for the WebPipe SDK. */

export class WebpipeError extends Error {
  constructor(message: string) {
    super(message);
    this.name = new.target.name;
    // Maintain a proper prototype chain on downlevel targets.
    Object.setPrototypeOf(this, new.target.prototype);
  }
}

/** An error response returned by the WebPipe API. */
export class APIError extends WebpipeError {
  readonly statusCode: number;
  readonly body: unknown;

  constructor(message: string, statusCode: number, body?: unknown) {
    super(`[${statusCode}] ${message}`);
    this.statusCode = statusCode;
    this.body = body;
  }
}

/** 400 — invalid parameters (missing url, bad format, ...). */
export class BadRequestError extends APIError {}

/** 401 — API key missing, invalid or deleted. */
export class AuthenticationError extends APIError {}

/** 403 — accessing a resource owned by another user. */
export class ForbiddenError extends APIError {}

/** 404 — job/session does not exist or has expired. */
export class NotFoundError extends APIError {}

/** 429 — rate limit exceeded, or too many concurrent browser sessions. */
export class RateLimitError extends APIError {}

/** 5xx — scrape timeout, target site unreachable, ... */
export class ServerError extends APIError {}

/** A crawl job reached status `failed`. */
export class CrawlError extends WebpipeError {}

/** Polling did not finish within the allotted timeout. */
export class WebpipeTimeoutError extends WebpipeError {}
