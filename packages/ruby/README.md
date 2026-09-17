# webpipe-sdk (Ruby)

Official Ruby SDK for [WebPipe.ai](https://webpipe.ai/) — turn any webpage into clean, structured data.

## Install

```bash
gem install webpipe-sdk
```

Requires Ruby ≥ 3.0. **Zero runtime dependencies** — stdlib `net/http` only.

## Quick Start

```ruby
require "webpipe"

client = Webpipe::Client.new # reads WEBPIPE_API_KEY, or Webpipe::Client.new(api_key: "wc-...")

# Scrape a page
doc = client.scrape("https://example.com", formats: ["markdown", "metadata"])
puts doc.markdown
puts doc.metadata["title"]
```

### Crawl a whole site (polls automatically)

```ruby
job = client.crawl("https://docs.example.com", limit: 50,
                   scrape_options: { formats: ["markdown"] }, timeout: 600)
job.data.each do |page|
  puts "#{page.metadata["sourceURL"]} #{page.metadata["title"]}"
end
```

Need control? `job = client.start_crawl(url, limit: 50)` returns immediately; poll with `client.get_crawl_status(job.id)`.

### Browser session (JS-rendered / logged-in pages)

```ruby
session = client.browser.create(url: "https://example.com/login")
begin
  session.wait_until_running
  session.fill("input[name=email]", "you@example.com")
  session.fill("input[name=password]", "secret")
  session.press("Enter", wait_after: 3000)

  doc = session.navigate("https://example.com/dashboard", formats: ["markdown"])
  puts doc.markdown
  session.save_session # reuse login state next time via session_name
ensure
  session.close
end
```

### Map (discover all URLs)

```ruby
client.map("https://example.com", limit: 100, search: "blog").each do |link|
  puts "#{link.url} #{link.title}"
end
```

## Errors

```ruby
begin
  client.scrape("https://example.com")
rescue Webpipe::AuthenticationError
  # bad API key
rescue Webpipe::RateLimitError => e
  # e.status_code == 429; e.message keeps the server's error text
rescue Webpipe::Error
  # anything else
end
```

`429` and `5xx` responses are retried automatically with exponential backoff (`max_retries: 2` by default).

## Configuration

| Param | Env var | Default |
|---|---|---|
| `api_key` | `WEBPIPE_API_KEY` | — (required) |
| `base_url` | `WEBPIPE_API_URL` | `https://api.web2json.ai` |
| `timeout` | — | 60s |
| `max_retries` | — | 2 |

## Development

```bash
bundle install
ruby -Ilib -Itest test/client_test.rb
ruby -Ilib -Itest test/browser_test.rb
```
