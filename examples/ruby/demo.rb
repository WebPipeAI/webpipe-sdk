#!/usr/bin/env ruby
# frozen_string_literal: true

# WebPipe SDK quickstart demo (Ruby ≥ 3.0).
#
# API key (checked in this order — get one at https://webpipe.ai/api-keys.html):
#   1. --api-key wc-...            flag
#   2. WEBPIPE_API_KEY=wc-...      environment variable
#   3. interactive prompt / pipe:  echo wc-... | ruby demo.rb scrape
#
# Usage (every command has a built-in demo URL, or pass your own):
#   ruby demo.rb scrape [url] [--api-key wc-...]    # default https://example.com
#   ruby demo.rb map [url] [--api-key wc-...]       # default https://nginx.org
#   ruby demo.rb crawl [url] [--api-key wc-...]     # default https://quotes.toscrape.com
#   ruby demo.rb browser [--api-key wc-...]         # login-flow demo on quotes.toscrape.com

require_relative "../../packages/ruby/lib/webpipe"

DEFAULT_URLS = {
  "scrape" => "https://example.com",
  "map" => "https://nginx.org",
  "crawl" => "https://quotes.toscrape.com"
}.freeze

def resolve_api_key(flag)
  return flag if flag

  env = ENV["WEBPIPE_API_KEY"]
  return env if env && !env.empty?

  puts "API key not found in --api-key or WEBPIPE_API_KEY."
  puts "Get one at https://webpipe.ai/api-keys.html"
  print "Enter your API key (wc-...): " if $stdin.tty?
  key = $stdin.gets&.strip
  return key if key && !key.empty?

  warn "error: no API key provided"
  exit 1
end

def cmd_scrape(client, url)
  puts "→ Scraping #{url} ..."
  doc = client.scrape(url, formats: %w[markdown metadata links])
  puts "  title:  #{doc.metadata&.dig("title")}"
  puts "  status: #{doc.metadata&.dig("statusCode")}"
  puts "  links:  #{doc.links&.size || 0}"
  puts "--- markdown (first 400 chars) ---"
  puts doc.markdown.to_s[0, 400]
end

def cmd_map(client, url)
  puts "→ Mapping #{url} ..."
  links = client.map(url, limit: 50)
  puts "  found #{links.size} URLs (limit 50):"
  links.first(10).each { |l| puts "  - #{l.url}" }
  puts "  ... and #{links.size - 10} more" if links.size > 10
end

def cmd_crawl(client, url)
  puts "→ Crawling #{url} (limit 5, polling until done) ..."
  # Ruby blocks are the idiomatic callback: crawl yields the job after each
  # poll, perfect for progress output.
  job = client.crawl(url, limit: 5, scrape_options: { formats: %w[markdown metadata] },
                          timeout: 300) do |status|
    print "\r  progress: #{status.completed}/#{status.total} pages scraped ..."
  end
  puts "\r  done:     #{job.completed}/#{job.total} pages          "
  job.data.each do |page|
    puts "  - #{page.metadata&.dig("sourceURL")} | #{page.metadata&.dig("title")}"
  end
end

def cmd_browser(client)
  url = "https://quotes.toscrape.com/login"
  puts "→ Browser login demo on #{url} (any credentials work) ..."
  session = client.browser.create(url: url)
  begin
    session.wait_until_running(timeout: 90)
    puts "  session running, filling the login form ..."
    session.fill("input[name=username]", "demo")
    session.fill("input[name=password]", "demo")
    session.click("input[type=submit]", wait_after: 2000)
    doc = session.scrape(formats: ["markdown"])
    ok = doc.markdown.to_s.include?("Logout")
    puts ok ? "  login succeeded ✓ (Logout link found)" : "  login — check output below"
    puts "--- markdown after login (first 300 chars) ---"
    puts doc.markdown.to_s[0, 300]
  ensure
    session.close
  end
  puts "  session closed."
end

# Parse args: positionals = <command> [url], plus --api-key <key>
positionals = []
api_key_flag = nil
i = 0
while i < ARGV.size
  if ARGV[i] == "--api-key" && i + 1 < ARGV.size
    api_key_flag = ARGV[i + 1]
    i += 2
  else
    positionals << ARGV[i]
    i += 1
  end
end
command = positionals[0]
if command.nil? || (!DEFAULT_URLS.key?(command) && command != "browser")
  warn "usage: ruby demo.rb <scrape|map|crawl|browser> [url] [--api-key wc-...]"
  exit 1
end

begin
  client = Webpipe::Client.new(api_key: resolve_api_key(api_key_flag))
  if command == "browser"
    cmd_browser(client)
  else
    url = positionals[1] || DEFAULT_URLS[command]
    case command
    when "scrape" then cmd_scrape(client, url)
    when "map" then cmd_map(client, url)
    when "crawl" then cmd_crawl(client, url)
    end
  end
rescue Webpipe::Error, ArgumentError => e
  warn "error: #{e.message}"
  exit 1
end
