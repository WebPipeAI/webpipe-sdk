# frozen_string_literal: true

require_relative "test_helper"

class ClientTest < Minitest::Test
  def test_missing_api_key_raises
    ENV.delete("WEBPIPE_API_KEY")
    error = assert_raises(ArgumentError) { Webpipe::Client.new }
    assert_match(/WEBPIPE_API_KEY/, error.message)
  end

  def test_scrape_success
    server = MockServer.new(
      "POST /api/v1/scrape" => [200, {
        "success" => true,
        "data" => {
          "markdown" => "# Example",
          "metadata" => { "title" => "Example Domain", "sourceURL" => "https://example.com", "statusCode" => 200 }
        }
      }]
    )
    client = Webpipe::Client.new(api_key: "wc-test-key", base_url: server.url, max_retries: 0)

    doc = client.scrape("https://example.com", formats: %w[markdown metadata])

    assert_equal "# Example", doc.markdown
    assert_equal "Example Domain", doc.metadata["title"]
    assert_equal 200, doc.metadata["statusCode"]
    assert_equal "Bearer wc-test-key", server.last_request[:headers]["authorization"].first
    assert_equal({ "url" => "https://example.com", "formats" => %w[markdown metadata] },
                 server.last_request[:body])
  ensure
    server&.shutdown
  end

  def test_scrape_401_raises_authentication_error
    server = MockServer.new(
      "POST /api/v1/scrape" => [401, { "success" => false, "error" => "invalid key" }]
    )
    client = Webpipe::Client.new(api_key: "wc-test-key", base_url: server.url, max_retries: 0)

    error = assert_raises(Webpipe::AuthenticationError) { client.scrape("https://example.com") }
    assert_equal 401, error.status_code
    assert_match(/invalid key/, error.message)
  ensure
    server&.shutdown
  end

  def test_scrape_retries_on_429
    server = MockServer.new(
      "POST /api/v1/scrape" => [
        [429, { "success" => false, "error" => "slow down" }],
        [200, { "success" => true, "data" => { "markdown" => "ok" } }]
      ]
    )
    client = Webpipe::Client.new(api_key: "wc-test-key", base_url: server.url, max_retries: 1)

    doc = client.scrape("https://example.com")
    assert_equal "ok", doc.markdown
    assert_equal 2, server.requests.size
  ensure
    server&.shutdown
  end

  def test_map_returns_links
    server = MockServer.new(
      "POST /api/v1/map" => [200, {
        "success" => true,
        "links" => [
          { "url" => "https://example.com/about", "title" => "About" },
          { "url" => "https://example.com/blog" }
        ]
      }]
    )
    client = Webpipe::Client.new(api_key: "wc-test-key", base_url: server.url, max_retries: 0)

    links = client.map("https://example.com", limit: 100, same_domain: false)

    assert_equal 2, links.size
    assert_equal "https://example.com/about", links[0].url
    assert_equal false, server.last_request[:body]["same_domain"]
  ensure
    server&.shutdown
  end

  def test_crawl_polls_until_completed
    server = MockServer.new(
      "POST /api/v1/crawl" => [200, { "success" => true, "id" => "job-1" }],
      "GET /api/v1/crawl/job-1" => [
        [200, { "success" => true, "status" => "scraping", "total" => 2, "completed" => 1 }],
        [200, { "success" => true, "status" => "completed", "total" => 2, "completed" => 2,
                "data" => [{ "markdown" => "# A" }, { "markdown" => "# B" }],
                "errors" => [], "expires_at" => "2026-07-28T00:00:00+00:00" }]
      ]
    )
    client = Webpipe::Client.new(api_key: "wc-test-key", base_url: server.url, max_retries: 0)

    job = client.crawl("https://x", limit: 2, poll_interval: 0.01)

    assert_equal "completed", job.status
    assert_equal 2, job.data.size
    assert_equal "# B", job.data[1].markdown
    assert_equal "2026-07-28T00:00:00+00:00", job.expires_at
  ensure
    server&.shutdown
  end

  def test_crawl_failed_raises
    server = MockServer.new(
      "POST /api/v1/crawl" => [200, { "success" => true, "id" => "job-2" }],
      "GET /api/v1/crawl/job-2" => [200, { "success" => true, "status" => "failed", "errors" => ["boom"] }]
    )
    client = Webpipe::Client.new(api_key: "wc-test-key", base_url: server.url, max_retries: 0)

    assert_raises(Webpipe::CrawlError) { client.crawl("https://x", poll_interval: 0.01) }
  ensure
    server&.shutdown
  end

  def test_get_crawl_status_404
    server = MockServer.new(
      "GET /api/v1/crawl/gone" => [404, { "success" => false, "error" => "gone" }]
    )
    client = Webpipe::Client.new(api_key: "wc-test-key", base_url: server.url, max_retries: 0)

    assert_raises(Webpipe::NotFoundError) { client.get_crawl_status("gone") }
  ensure
    server&.shutdown
  end

  def test_crawl_yields_progress_to_block
    server = MockServer.new(
      "POST /api/v1/crawl" => [200, { "success" => true, "id" => "job-9" }],
      "GET /api/v1/crawl/job-9" => [
        [200, { "success" => true, "status" => "scraping", "total" => 3, "completed" => 1 }],
        [200, { "success" => true, "status" => "scraping", "total" => 3, "completed" => 2 }],
        [200, { "success" => true, "status" => "completed", "total" => 3, "completed" => 3, "data" => [], "errors" => [] }]
      ]
    )
    client = Webpipe::Client.new(api_key: "wc-test-key", base_url: server.url, max_retries: 0)

    progress = []
    job = client.crawl("https://x", limit: 3, poll_interval: 0.01) do |status|
      progress << "#{status.completed}/#{status.total}"
    end

    assert_equal "completed", job.status
    assert_equal ["1/3", "2/3"], progress
  ensure
    server&.shutdown
  end
end
