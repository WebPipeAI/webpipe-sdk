# frozen_string_literal: true

module Webpipe
  # Factory for browser sessions, exposed as +client.browser+.
  # @api private (constructed via Client)
  class BrowserResource
    def initialize(http)
      @http = http
    end

    # Create a session. It starts asynchronously — call +wait_until_running+
    # before sending commands for a controlled experience.
    def create(url: nil, cookie: nil, proxy: nil, session_name: nil)
      body = drop_nil(url: url, cookie: cookie, proxy: proxy, session_name: session_name)
      info = @http.post("/api/v1/browser", body)
      BrowserSession.new(@http, id: info["id"], status: info["status"], url: info["url"],
                                session_name: info["session_name"], expires_at: info["expires_at"])
    end

    private

    def drop_nil(hash) = hash.reject { |_, v| v.nil? }
  end

  # Handle to a persistent headless Chromium session on the server.
  #
  # Server-side facts: sessions expire after 30 minutes idle; at most 2
  # concurrent running sessions per user. Always +close+ when done.
  class BrowserSession
    attr_reader :id, :status, :url, :session_name, :expires_at

    TERMINAL_STATUSES = %w[stopped error expired].freeze

    def initialize(http, id:, status:, url: nil, session_name: nil, expires_at: nil)
      @http = http
      @id = id
      @status = status
      @url = url
      @session_name = session_name
      @expires_at = expires_at
    end

    # Fetch the latest session state from the server.
    def refresh
      info = @http.get(base)
      @status = info["status"]
      @url = info["url"]
      @session_name = info["session_name"]
      @expires_at = info["expires_at"]
      self
    end

    # Block until the session is ready to accept commands.
    def wait_until_running(timeout: 60, poll_interval: 1)
      deadline = Time.now + timeout
      loop do
        refresh
        return self if @status == "running"
        raise Error, "Browser session #{@id} entered terminal status '#{@status}'" if TERMINAL_STATUSES.include?(@status)
        raise PollTimeoutError, "Browser session #{@id} not running after #{timeout}s" if Time.now >= deadline

        sleep poll_interval
      end
    end

    # Stop the session and release server resources.
    def close
      @http.delete(base)
      @status = "stopped"
      nil
    end

    # Navigate to a new URL (with anti-bot handling) and scrape it.
    def navigate(url, formats: nil, scroll_to_bottom: nil, max_scrolls: nil, scroll_wait: nil, scroll_step: nil)
      body = { url: url }.merge(scrape_body(formats:, scroll_to_bottom:, max_scrolls:, scroll_wait:, scroll_step:))
      Document.from_hash(@http.post("#{base}/navigate", body)["data"])
    end

    # Scrape the current page without navigating (faster than +navigate+).
    def scrape(formats: nil, scroll_to_bottom: nil, max_scrolls: nil, scroll_wait: nil, scroll_step: nil)
      body = scrape_body(formats:, scroll_to_bottom:, max_scrolls:, scroll_wait:, scroll_step:)
      Document.from_hash(@http.post("#{base}/scrape", body)["data"])
    end

    # Run a raw browser action. Returns the raw API payload Hash.
    def action(type, **params)
      @http.post("#{base}/action", { type: type }.merge(params))
    end

    def click(selector, wait_after: nil) = action("click", **drop_nil(selector:, wait_after:))
    def fill(selector, value) = action("fill", selector:, value:)
    def select(selector, value) = action("select", selector:, value:)

    def press(key, selector: nil, wait_after: nil)
      action("press", **drop_nil(key:, selector:, wait_after:))
    end

    def scroll(direction: "down", amount: nil) = action("scroll", **drop_nil(direction:, amount:))
    def wait(ms = 1000) = action("wait", ms:)
    def wait_for(selector, timeout: nil) = action("wait_for", **drop_nil(selector:, timeout:))

    # Take a screenshot. The API returns a base64 PNG inside the payload.
    def screenshot(full_page: false) = action("screenshot", full_page:)

    # Evaluate a JavaScript expression in the page (value in payload's "result").
    def evaluate(expression) = action("evaluate", expression:)
    def hover(selector) = action("hover", selector:)
    def check(selector) = action("check", selector:)

    def get_cookies
      data = @http.get("#{base}/cookies")
      data["cookies"] || data["data"] || []
    end

    def set_cookies(cookies) = @http.post("#{base}/cookies", { op: "set", cookies: cookies })
    def clear_cookies = @http.post("#{base}/cookies", { op: "clear" })

    # Persist cookies/storage under this session's +session_name+.
    def save_session = @http.post("#{base}/save_session")

    private

    def base = "/api/v1/browser/#{@id}"

    def scrape_body(formats:, scroll_to_bottom:, max_scrolls:, scroll_wait:, scroll_step:)
      drop_nil(
        formats: formats, scroll_to_bottom: scroll_to_bottom,
        max_scrolls: max_scrolls, scroll_wait: scroll_wait, scroll_step: scroll_step
      )
    end

    def drop_nil(hash) = hash.reject { |_, v| v.nil? }
  end
end
