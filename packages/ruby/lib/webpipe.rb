# frozen_string_literal: true

# webpipe-sdk — the official Ruby SDK for WebPipe.ai.
#
#   require "webpipe"
#   client = Webpipe::Client.new(api_key: "wc-...")
#   doc = client.scrape("https://example.com", formats: ["markdown"])
#   puts doc.markdown

require_relative "webpipe/version"
require_relative "webpipe/errors"
require_relative "webpipe/types"
require_relative "webpipe/http_client"
require_relative "webpipe/browser"
require_relative "webpipe/client"
