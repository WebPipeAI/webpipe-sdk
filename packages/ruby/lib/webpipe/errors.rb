# frozen_string_literal: true

module Webpipe
  # Base class for all webpipe-sdk errors.
  class Error < StandardError; end

  # An error response returned by the WebPipe API. Always carries the server's
  # original error message and the HTTP status code.
  class ApiError < Error
    attr_reader :status_code, :body

    def initialize(message, status_code:, body: nil)
      super("[#{status_code}] #{message}")
      @status_code = status_code
      @body = body
    end
  end

  # 400 — invalid parameters (missing url, bad format, ...).
  class BadRequestError < ApiError; end
  # 401 — API key missing, invalid or deleted.
  class AuthenticationError < ApiError; end
  # 403 — accessing a resource owned by another user.
  class ForbiddenError < ApiError; end
  # 404 — job/session does not exist or has expired.
  class NotFoundError < ApiError; end
  # 429 — rate limit exceeded, or too many concurrent browser sessions.
  class RateLimitError < ApiError; end
  # 5xx — scrape timeout, target site unreachable, ...
  class ServerError < ApiError; end

  # A crawl job reached status "failed".
  class CrawlError < Error; end

  # Polling did not finish within the allotted timeout.
  class PollTimeoutError < Error; end
end
