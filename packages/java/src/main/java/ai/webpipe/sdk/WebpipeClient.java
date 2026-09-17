package ai.webpipe.sdk;

import ai.webpipe.sdk.exceptions.CrawlException;
import ai.webpipe.sdk.exceptions.PollTimeoutException;
import ai.webpipe.sdk.internal.HttpClient;
import ai.webpipe.sdk.model.CrawlJob;
import ai.webpipe.sdk.model.CrawlJobStart;
import ai.webpipe.sdk.model.Document;
import ai.webpipe.sdk.model.MapLink;
import ai.webpipe.sdk.options.CrawlOptions;
import ai.webpipe.sdk.options.MapOptions;
import ai.webpipe.sdk.options.ScrapeOptions;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * The WebPipe API client.
 *
 * <pre>
 * var client = WebpipeClient.create(); // reads WEBPIPE_API_KEY
 * var doc = client.scrape("https://example.com",
 *         new ScrapeOptions().formats(List.of("markdown")));
 * System.out.println(doc.markdown());
 * </pre>
 */
public final class WebpipeClient {
    public static final String VERSION = "0.1.0";
    public static final String DEFAULT_BASE_URL = "https://api.web2json.ai";

    private final HttpClient http;
    public final BrowserResource browser;

    private WebpipeClient(Builder builder) {
        String apiKey = builder.apiKey != null ? builder.apiKey : System.getenv("WEBPIPE_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalArgumentException(
                    "Missing API key. Use builder().apiKey(...) or set the WEBPIPE_API_KEY "
                            + "environment variable. Create one at https://webpipe.ai/api-keys.html");
        }
        String baseUrl = builder.baseUrl != null
                ? builder.baseUrl
                : System.getenv().getOrDefault("WEBPIPE_API_URL", DEFAULT_BASE_URL);
        this.http = new HttpClient(
                baseUrl.replaceAll("/+$", ""),
                apiKey,
                builder.timeout,
                builder.maxRetries,
                "webpipe-sdk-java/" + VERSION);
        this.browser = new BrowserResource(this.http);
    }

    /** Create a client from environment variables (WEBPIPE_API_KEY, WEBPIPE_API_URL). */
    public static WebpipeClient create() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String apiKey;
        private String baseUrl;
        private Duration timeout = Duration.ofSeconds(60);
        private int maxRetries = 2;

        public Builder apiKey(String v) {
            this.apiKey = v;
            return this;
        }

        public Builder baseUrl(String v) {
            this.baseUrl = v;
            return this;
        }

        public Builder timeout(Duration v) {
            this.timeout = v;
            return this;
        }

        public Builder maxRetries(int v) {
            this.maxRetries = v;
            return this;
        }

        public WebpipeClient build() {
            return new WebpipeClient(this);
        }
    }

    // ------------------------------------------------------------------ Scrape

    /** Scrape a single page into markdown/html/links/metadata/json. */
    public Document scrape(String url, ScrapeOptions options) {
        JsonNode res = http.post("/api/v1/scrape", http.bodyWithUrl(url, options));
        return HttpClient.responseMapper().convertValue(res.path("data"), Document.class);
    }

    /** Scrape with default options (markdown). */
    public Document scrape(String url) {
        return scrape(url, null);
    }

    // --------------------------------------------------------------------- Map

    /** Discover URLs of a website (robots.txt → sitemap → in-page links). */
    public List<MapLink> map(String url, MapOptions options) {
        JsonNode res = http.post("/api/v1/map", http.bodyWithUrl(url, options));
        return HttpClient.responseMapper().convertValue(
                res.path("links"),
                HttpClient.responseMapper().getTypeFactory().constructCollectionType(List.class, MapLink.class));
    }

    // ------------------------------------------------------------------- Crawl

    /** Submit an async crawl job and return its handle immediately. */
    public CrawlJobStart startCrawl(String url, CrawlOptions options) {
        JsonNode res = http.post("/api/v1/crawl", http.bodyWithUrl(url, options));
        return HttpClient.responseMapper().convertValue(res, CrawlJobStart.class);
    }

    /** Fetch the current status and partial results of a crawl job. */
    public CrawlJob getCrawlStatus(String jobId) {
        return HttpClient.responseMapper().convertValue(http.get("/api/v1/crawl/" + jobId), CrawlJob.class);
    }

    /**
     * Submit a crawl job and poll until it completes.
     *
     * @param pollInterval seconds between status polls
     * @param timeout      max seconds to wait (0 or negative = no limit)
     * @throws CrawlException        if the job fails
     * @throws PollTimeoutException  if timeout is exceeded
     */
    public CrawlJob crawl(String url, CrawlOptions options, double pollInterval, double timeout) {
        CrawlJobStart job = startCrawl(url, options);
        long deadlineMillis = timeout > 0 ? System.currentTimeMillis() + (long) (timeout * 1000) : Long.MAX_VALUE;
        while (true) {
            CrawlJob status = getCrawlStatus(job.id());
            switch (status.status()) {
                case CrawlJob.STATUS_COMPLETED -> {
                    return status;
                }
                case CrawlJob.STATUS_FAILED -> throw new CrawlException(
                        "Crawl job " + job.id() + " failed: " + status.errors());
                default -> {
                    if (System.currentTimeMillis() >= deadlineMillis) {
                        throw new PollTimeoutException(
                                "Crawl job " + job.id() + " did not complete within " + timeout + "s");
                    }
                    sleep(pollInterval);
                }
            }
        }
    }

    /** Crawl with default polling (2s interval, no timeout). */
    public CrawlJob crawl(String url, CrawlOptions options) {
        return crawl(url, options, 2, 0);
    }

    // ------------------------------------------------------------ async API
    // Non-blocking variants with identical semantics, for reactive/concurrent
    // use. They share the sync methods' retry and error-mapping behavior.

    /** Async variant of {@link #scrape(String, ScrapeOptions)}. */
    public CompletableFuture<Document> scrapeAsync(String url, ScrapeOptions options) {
        return http.postAsync("/api/v1/scrape", http.bodyWithUrl(url, options))
                .thenApply(res -> HttpClient.responseMapper().convertValue(res.path("data"), Document.class));
    }

    /** Async variant of {@link #map(String, MapOptions)}. */
    public CompletableFuture<List<MapLink>> mapAsync(String url, MapOptions options) {
        return http.postAsync("/api/v1/map", http.bodyWithUrl(url, options))
                .thenApply(res -> HttpClient.responseMapper().convertValue(
                        res.path("links"),
                        HttpClient.responseMapper()
                                .getTypeFactory()
                                .constructCollectionType(List.class, MapLink.class)));
    }

    /** Async variant of {@link #startCrawl(String, CrawlOptions)}. */
    public CompletableFuture<CrawlJobStart> startCrawlAsync(String url, CrawlOptions options) {
        return http.postAsync("/api/v1/crawl", http.bodyWithUrl(url, options))
                .thenApply(res -> HttpClient.responseMapper().convertValue(res, CrawlJobStart.class));
    }

    /** Async variant of {@link #getCrawlStatus(String)}. */
    public CompletableFuture<CrawlJob> getCrawlStatusAsync(String jobId) {
        return http.getAsync("/api/v1/crawl/" + jobId)
                .thenApply(res -> HttpClient.responseMapper().convertValue(res, CrawlJob.class));
    }

    /** Async variant of {@link #crawl}: polls via the scheduler instead of
     * blocking a thread. */
    public CompletableFuture<CrawlJob> crawlAsync(
            String url, CrawlOptions options, double pollInterval, double timeout) {
        return startCrawlAsync(url, options)
                .thenCompose(job -> pollCrawl(job.id(), pollInterval,
                        timeout > 0 ? System.currentTimeMillis() + (long) (timeout * 1000) : Long.MAX_VALUE));
    }

    private CompletableFuture<CrawlJob> pollCrawl(String jobId, double pollInterval, long deadlineMillis) {
        return getCrawlStatusAsync(jobId).thenCompose(status -> {
            switch (status.status()) {
                case CrawlJob.STATUS_COMPLETED -> {
                    return CompletableFuture.completedFuture(status);
                }
                case CrawlJob.STATUS_FAILED -> throw new CompletionException(new CrawlException(
                        "Crawl job " + jobId + " failed: " + status.errors()));
                default -> {
                    if (System.currentTimeMillis() >= deadlineMillis) {
                        throw new CompletionException(new PollTimeoutException(
                                "Crawl job " + jobId + " did not complete within the timeout"));
                    }
                    return HttpClient.delay((long) (pollInterval * 1000))
                            .thenCompose(v -> pollCrawl(jobId, pollInterval, deadlineMillis));
                }
            }
        });
    }

    private static void sleep(double seconds) {
        try {
            Thread.sleep((long) (seconds * 1000));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PollTimeoutException("Interrupted while polling");
        }
    }
}
