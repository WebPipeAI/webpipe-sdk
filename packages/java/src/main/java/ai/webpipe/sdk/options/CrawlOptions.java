package ai.webpipe.sdk.options;

import java.util.List;

/** Optional parameters of {@code startCrawl(...)} / {@code crawl(...)}.
 * Fluent setters return {@code this}. */
public class CrawlOptions {
    /** Max pages to crawl (default 100, max 10000). */
    public Integer limit;
    /** Max link-discovery depth. 0 = start page only. */
    public Integer maxDiscoveryDepth;
    /** Regex whitelist for URL paths. */
    public List<String> includePaths;
    /** Regex blacklist for URL paths. */
    public List<String> excludePaths;
    public Boolean allowSubdomains;
    public Boolean allowExternalLinks;
    /** Crawl the whole domain, not just the start URL's subtree. */
    public Boolean crawlEntireDomain;
    /** Deduplicate URLs ignoring query params. */
    public Boolean ignoreQueryParams;
    /** "include" (default) | "skip" | "only". */
    public String sitemap;
    /** Seconds between page requests. */
    public Double delay;
    /** Concurrent requests, 1-10 (default 3). */
    public Integer maxConcurrency;
    /** Per-page scrape options. */
    public ScrapeOptions scrapeOptions;
    public Boolean useProxy;
    public String proxy;
    public String cookie;

    public CrawlOptions limit(Integer v) {
        this.limit = v;
        return this;
    }

    public CrawlOptions maxDiscoveryDepth(Integer v) {
        this.maxDiscoveryDepth = v;
        return this;
    }

    public CrawlOptions includePaths(List<String> v) {
        this.includePaths = v;
        return this;
    }

    public CrawlOptions excludePaths(List<String> v) {
        this.excludePaths = v;
        return this;
    }

    public CrawlOptions allowSubdomains(Boolean v) {
        this.allowSubdomains = v;
        return this;
    }

    public CrawlOptions allowExternalLinks(Boolean v) {
        this.allowExternalLinks = v;
        return this;
    }

    public CrawlOptions crawlEntireDomain(Boolean v) {
        this.crawlEntireDomain = v;
        return this;
    }

    public CrawlOptions ignoreQueryParams(Boolean v) {
        this.ignoreQueryParams = v;
        return this;
    }

    public CrawlOptions sitemap(String v) {
        this.sitemap = v;
        return this;
    }

    public CrawlOptions delay(Double v) {
        this.delay = v;
        return this;
    }

    public CrawlOptions maxConcurrency(Integer v) {
        this.maxConcurrency = v;
        return this;
    }

    public CrawlOptions scrapeOptions(ScrapeOptions v) {
        this.scrapeOptions = v;
        return this;
    }

    public CrawlOptions useProxy(Boolean v) {
        this.useProxy = v;
        return this;
    }

    public CrawlOptions proxy(String v) {
        this.proxy = v;
        return this;
    }

    public CrawlOptions cookie(String v) {
        this.cookie = v;
        return this;
    }
}
