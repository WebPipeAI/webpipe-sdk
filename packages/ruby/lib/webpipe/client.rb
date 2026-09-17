# frozen_string_literal: true

module Webpipe
  # The WebPipe API client.
  #
  #   client = Webpipe::Client.new # reads WEBPIPE_API_KEY
  #   doc = client.scrape("https://example.com", formats: ["markdown"])
  #   puts doc.markdown
  class Client
    DEFAULT_BASE_URL = "https://api.web2json.ai"

    attr_reader :browser

    def initialize(api_key: nil, base_url: nil, timeout: 60, max_retries: 2)
      api_key ||= ENV["WEBPIPE_API_KEY"]
      if api_key.nil? || api_key.empty?
        raise ArgumentError,
              "Missing API key. Pass api_key: or set the WEBPIPE_API_KEY environment " \
              "variable. Create one at https://webpipe.ai/api-keys.html"
      end

      base_url ||= ENV["WEBPIPE_API_URL"] || DEFAULT_BASE_URL
      @http = HttpClient.new(
        base_url: base_url.sub(%r{/+$}, ""),
        api_key: api_key,
        timeout: timeout,
        max_retries: max_retries,
        user_agent: "webpipe-sdk-ruby/#{Webpipe::VERSION}"
      )
      @browser = BrowserResource.new(@http)
    end

    # Scrape a single page into markdown/html/links/metadata/json.
    def scrape(url, formats: nil, json_schema: nil, use_proxy: nil, proxy: nil, cookie: nil)
      body = drop_nil(url:, formats:, json_schema:, use_proxy:, proxy:, cookie:)
      Document.from_hash(@http.post("/api/v1/scrape", body)["data"])
    end

    # Discover URLs of a website (robots.txt → sitemap → in-page links).
    def map(url, limit: nil, search: nil, sitemap: nil, same_domain: nil,
            use_proxy: nil, proxy: nil, cookie: nil)
      body = drop_nil(url:, limit:, search:, sitemap:, same_domain:, use_proxy:, proxy:, cookie:)
      (@http.post("/api/v1/map", body)["links"] || []).map { |l| MapLink.from_hash(l) }
    end

    # Submit an async crawl job and return its handle immediately.
    def start_crawl(url, limit: nil, max_discovery_depth: nil, include_paths: nil,
                    exclude_paths: nil, allow_subdomains: nil, allow_external_links: nil,
                    crawl_entire_domain: nil, ignore_query_params: nil, sitemap: nil,
                    delay: nil, max_concurrency: nil, scrape_options: nil,
                    use_proxy: nil, proxy: nil, cookie: nil)
      body = drop_nil(
        url:, limit:, max_discovery_depth:, include_paths:, exclude_paths:,
        allow_subdomains:, allow_external_links:, crawl_entire_domain:,
        ignore_query_params:, sitemap:, delay:, max_concurrency:,
        scrape_options:, use_proxy:, proxy:, cookie:
      )
      res = @http.post("/api/v1/crawl", body)
      CrawlJobStart.new(res.fetch("id"), res["url"])
    end

    # Fetch the current status and partial results of a crawl job.
    def get_crawl_status(job_id)
      CrawlJob.from_hash(@http.get("/api/v1/crawl/#{job_id}"))
    end

    # Submit a crawl job and poll until it completes.
    #
    # Yields the current CrawlJob after each poll when a block is given —
    # the idiomatic Ruby way to observe a long crawl (progress bars, logging).
    #
    # Raises CrawlError if the job fails, PollTimeoutError if +timeout+ is
    # exceeded. Other keywords are the same as +start_crawl+.
    def crawl(url, poll_interval: 2, timeout: nil, **options)
      job = start_crawl(url, **options)
      deadline = timeout && Time.now + timeout
      loop do
        status = get_crawl_status(job.id)
        return status if status.status == CrawlJob::STATUS_COMPLETED
        raise CrawlError, "Crawl job #{job.id} failed: #{status.errors}" if status.status == CrawlJob::STATUS_FAILED
        yield status if block_given?
        if deadline && Time.now >= deadline
          raise PollTimeoutError, "Crawl job #{job.id} did not complete within #{timeout}s"
        end

        sleep poll_interval
      end
    end

    private

    def drop_nil(hash) = hash.reject { |_, v| v.nil? }
  end
end
