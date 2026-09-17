# frozen_string_literal: true

module Webpipe
  # A link extracted from a page.
  Link = Struct.new(:text, :href) do
    def self.from_hash(hash)
      new(hash["text"], hash["href"])
    end
  end

  # Result of scraping a single page (scrape / browser / crawl data item).
  # `metadata` is a plain Hash — the API adds extra keys (og:*, sourceURL, ...).
  class Document
    attr_reader :markdown, :html, :raw_html, :links, :metadata, :json

    def initialize(markdown: nil, html: nil, raw_html: nil, links: nil, metadata: nil, json: nil)
      @markdown = markdown
      @html = html
      @raw_html = raw_html
      @links = links
      @metadata = metadata
      @json = json
    end

    def self.from_hash(hash)
      hash ||= {}
      new(
        markdown: hash["markdown"],
        html: hash["html"],
        raw_html: hash["rawHtml"],
        links: hash["links"]&.map { |l| Link.from_hash(l) },
        metadata: hash["metadata"],
        json: hash["json"]
      )
    end
  end

  # A URL discovered by the Map endpoint.
  MapLink = Struct.new(:url, :title, :description) do
    def self.from_hash(hash)
      new(hash["url"], hash["title"], hash["description"])
    end
  end

  # Handle returned when a crawl job is submitted.
  CrawlJobStart = Struct.new(:id, :url)

  # Status and (partial or final) results of a crawl job.
  # Results expire 24h after completion (see +expires_at+).
  class CrawlJob
    STATUS_SCRAPING = "scraping"
    STATUS_COMPLETED = "completed"
    STATUS_FAILED = "failed"

    attr_reader :status, :total, :completed, :data, :errors, :expires_at

    def initialize(status:, total: 0, completed: 0, data: [], errors: [], expires_at: nil)
      @status = status
      @total = total
      @completed = completed
      @data = data
      @errors = errors
      @expires_at = expires_at
    end

    def self.from_hash(hash)
      new(
        status: hash["status"],
        total: hash["total"] || 0,
        completed: hash["completed"] || 0,
        data: (hash["data"] || []).map { |d| Document.from_hash(d) },
        errors: hash["errors"] || [],
        expires_at: hash["expires_at"]
      )
    end
  end
end
