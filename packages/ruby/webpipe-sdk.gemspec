# frozen_string_literal: true

require_relative "lib/webpipe/version"

Gem::Specification.new do |spec|
  spec.name = "webpipe-sdk"
  spec.version = Webpipe::VERSION
  spec.authors = ["WebPipe.ai"]
  spec.summary = "Official Ruby SDK for WebPipe.ai — turn any webpage into clean, structured data"
  spec.description = "Scrape, Map, Crawl and persistent Browser Sessions via the WebPipe.ai API. " \
                     "Zero runtime dependencies (stdlib net/http)."
  spec.homepage = "https://webpipe.ai"
  spec.license = "MIT"
  spec.required_ruby_version = ">= 3.0"

  spec.files = Dir["lib/**/*.rb", "README.md"]
  spec.require_paths = ["lib"]
end
