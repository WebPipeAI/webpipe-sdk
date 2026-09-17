# frozen_string_literal: true

require_relative "test_helper"

class BrowserTest < Minitest::Test
  def test_session_lifecycle
    server = MockServer.new(
      "POST /api/v1/browser" => [200, { "success" => true, "id" => "sess-1", "status" => "starting" }],
      "GET /api/v1/browser/sess-1" => [
        [200, { "success" => true, "id" => "sess-1", "status" => "starting" }],
        [200, { "success" => true, "id" => "sess-1", "status" => "running" }]
      ],
      "POST /api/v1/browser/sess-1/scrape" => [200, { "success" => true, "data" => { "markdown" => "# Hi" } }],
      "POST /api/v1/browser/sess-1/action" => [200, { "success" => true, "ok" => true }],
      "DELETE /api/v1/browser/sess-1" => [200, { "success" => true }]
    )
    client = Webpipe::Client.new(api_key: "wc-test-key", base_url: server.url, max_retries: 0)

    session = client.browser.create(url: "https://example.com", session_name: "demo")
    assert_equal "sess-1", session.id
    assert_equal "starting", session.status

    create_body = server.requests[0][:body]
    assert_equal "demo", create_body["session_name"]

    session.wait_until_running(timeout: 5, poll_interval: 0.01)
    assert_equal "running", session.status

    doc = session.scrape(formats: ["markdown"])
    assert_equal "# Hi", doc.markdown

    session.click("button.more", wait_after: 500)
    action_body = server.last_request[:body]
    assert_equal({ "type" => "click", "selector" => "button.more", "wait_after" => 500 }, action_body)

    session.close
    assert_equal "stopped", session.status
    assert_equal "DELETE", server.last_request[:method]
  ensure
    server&.shutdown
  end

  def test_cookies
    server = MockServer.new(
      "POST /api/v1/browser" => [200, { "success" => true, "id" => "sess-2", "status" => "running" }],
      "GET /api/v1/browser/sess-2/cookies" => [200, { "success" => true, "cookies" => [{ "name" => "session", "value" => "abc" }] }],
      "POST /api/v1/browser/sess-2/cookies" => [[200, { "success" => true }], [200, { "success" => true }]]
    )
    client = Webpipe::Client.new(api_key: "wc-test-key", base_url: server.url, max_retries: 0)

    session = client.browser.create
    assert_equal "session", session.get_cookies[0]["name"]

    session.set_cookies([{ "name" => "x", "value" => "1", "domain" => "example.com" }])
    assert_equal "set", server.last_request[:body]["op"]

    session.clear_cookies
    assert_equal({ "op" => "clear" }, server.last_request[:body])
  ensure
    server&.shutdown
  end
end
