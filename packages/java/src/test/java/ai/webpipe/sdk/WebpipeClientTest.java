package ai.webpipe.sdk;

import static org.junit.jupiter.api.Assertions.*;

import ai.webpipe.sdk.exceptions.AuthenticationException;
import ai.webpipe.sdk.exceptions.CrawlException;
import ai.webpipe.sdk.exceptions.NotFoundException;
import ai.webpipe.sdk.exceptions.PollTimeoutException;
import ai.webpipe.sdk.model.CrawlJob;
import ai.webpipe.sdk.model.Document;
import ai.webpipe.sdk.model.MapLink;
import ai.webpipe.sdk.options.CrawlOptions;
import ai.webpipe.sdk.options.MapOptions;
import ai.webpipe.sdk.options.ScrapeOptions;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WebpipeClientTest {

    private WebpipeClient client(MockServer server) {
        return WebpipeClient.builder()
                .apiKey("wc-test-key")
                .baseUrl(server.url())
                .maxRetries(0)
                .build();
    }

    @Test
    void scrapeSuccess() throws Exception {
        try (MockServer server = new MockServer()) {
            server.enqueue("POST", "/api/v1/scrape", 200, """
                    {"success":true,"data":{"markdown":"# Example",
                      "metadata":{"title":"Example Domain","sourceURL":"https://example.com","statusCode":200}}}""");
            Document doc = client(server).scrape("https://example.com",
                    new ScrapeOptions().formats(List.of("markdown", "metadata")));

            assertEquals("# Example", doc.markdown());
            assertEquals("Example Domain", doc.metadata().get("title"));
            assertEquals(200, doc.metadata().get("statusCode"));

            MockServer.Request req = server.lastRequest();
            assertEquals("Bearer wc-test-key", req.headers().get("Authorization"));
            assertTrue(req.headers().get("User-agent").startsWith("webpipe-sdk-java/"));
            assertEquals("https://example.com", req.body().get("url").asText());
            assertEquals("markdown", req.body().get("formats").get(0).asText());
            assertFalse(req.body().has("use_proxy"), "unset options must be omitted");
        }
    }

    @Test
    void scrapeSnakeCaseMapping() throws Exception {
        try (MockServer server = new MockServer()) {
            server.enqueue("POST", "/api/v1/scrape", 200, "{\"success\":true,\"data\":{\"json\":{}}}");
            client(server).scrape("https://example.com",
                    new ScrapeOptions()
                            .formats(List.of("json"))
                            .jsonSchema(Map.of("type", "object",
                                    "properties", Map.of("myField", Map.of("type", "string"))))
                            .useProxy(true));

            var body = server.lastRequest().body();
            assertTrue(body.get("use_proxy").asBoolean());
            assertTrue(body.get("json_schema").get("properties").has("myField"),
                    "user keys inside jsonSchema must pass through untouched");
        }
    }

    @Test
    void scrape401() throws Exception {
        try (MockServer server = new MockServer()) {
            server.enqueue("POST", "/api/v1/scrape", 401, "{\"success\":false,\"error\":\"invalid key\"}");
            AuthenticationException e = assertThrows(AuthenticationException.class,
                    () -> client(server).scrape("https://example.com"));
            assertEquals(401, e.getStatusCode());
            assertTrue(e.getMessage().contains("invalid key"));
        }
    }

    @Test
    void retriesOn429() throws Exception {
        try (MockServer server = new MockServer()) {
            server.enqueue("POST", "/api/v1/scrape", 429, "{\"success\":false,\"error\":\"slow down\"}");
            server.enqueue("POST", "/api/v1/scrape", 200, "{\"success\":true,\"data\":{\"markdown\":\"ok\"}}");
            WebpipeClient c = WebpipeClient.builder()
                    .apiKey("wc-test-key").baseUrl(server.url()).maxRetries(1).build();
            assertEquals("ok", c.scrape("https://example.com").markdown());
            assertEquals(2, server.requests.size());
        }
    }

    @Test
    void mapReturnsLinks() throws Exception {
        try (MockServer server = new MockServer()) {
            server.enqueue("POST", "/api/v1/map", 200, """
                    {"success":true,"links":[{"url":"https://example.com/about","title":"About"},
                     {"url":"https://example.com/blog"}]}""");
            List<MapLink> links = client(server).map("https://example.com",
                    new MapOptions().limit(100).sameDomain(false));
            assertEquals(2, links.size());
            assertEquals("https://example.com/about", links.get(0).url());
            assertFalse(server.lastRequest().body().get("same_domain").asBoolean());
        }
    }

    @Test
    void crawlPollsUntilCompleted() throws Exception {
        try (MockServer server = new MockServer()) {
            server.enqueue("POST", "/api/v1/crawl", 200, "{\"success\":true,\"id\":\"job-1\"}");
            server.enqueue("GET", "/api/v1/crawl/job-1", 200,
                    "{\"success\":true,\"status\":\"scraping\",\"total\":2,\"completed\":1}");
            server.enqueue("GET", "/api/v1/crawl/job-1", 200, """
                    {"success":true,"status":"completed","total":2,"completed":2,
                     "data":[{"markdown":"# A"},{"markdown":"# B"}],"errors":[],
                     "expires_at":"2026-07-28T00:00:00+00:00"}""");
            CrawlJob job = client(server).crawl("https://x",
                    new CrawlOptions().limit(2), 0.01, 0);
            assertEquals("completed", job.status());
            assertEquals(2, job.data().size());
            assertEquals("# B", job.data().get(1).markdown());
            assertEquals("2026-07-28T00:00:00+00:00", job.expiresAt());
        }
    }

    @Test
    void crawlFailed() throws Exception {
        try (MockServer server = new MockServer()) {
            server.enqueue("POST", "/api/v1/crawl", 200, "{\"success\":true,\"id\":\"job-2\"}");
            server.enqueue("GET", "/api/v1/crawl/job-2", 200,
                    "{\"success\":true,\"status\":\"failed\",\"errors\":[\"boom\"]}");
            assertThrows(CrawlException.class,
                    () -> client(server).crawl("https://x", new CrawlOptions(), 0.01, 0));
        }
    }

    @Test
    void crawlTimeout() throws Exception {
        try (MockServer server = new MockServer()) {
            server.enqueue("POST", "/api/v1/crawl", 200, "{\"success\":true,\"id\":\"job-3\"}");
            for (int i = 0; i < 20; i++) {
                server.enqueue("GET", "/api/v1/crawl/job-3", 200,
                        "{\"success\":true,\"status\":\"scraping\"}");
            }
            assertThrows(PollTimeoutException.class,
                    () -> client(server).crawl("https://x", new CrawlOptions(), 0.01, 0.05));
        }
    }

    @Test
    void crawlStatus404() throws Exception {
        try (MockServer server = new MockServer()) {
            server.enqueue("GET", "/api/v1/crawl/gone", 404, "{\"success\":false,\"error\":\"gone\"}");
            assertThrows(NotFoundException.class, () -> client(server).getCrawlStatus("gone"));
        }
    }

    // -------------------------------------------------------------- async

    @Test
    void scrapeAsyncSuccess() throws Exception {
        try (MockServer server = new MockServer()) {
            server.enqueue("POST", "/api/v1/scrape", 200,
                    "{\"success\":true,\"data\":{\"markdown\":\"# Async\"}}");
            Document doc = client(server).scrapeAsync("https://example.com",
                    new ScrapeOptions().formats(List.of("markdown"))).join();
            assertEquals("# Async", doc.markdown());
        }
    }

    @Test
    void scrapeAsync401() throws Exception {
        try (MockServer server = new MockServer()) {
            server.enqueue("POST", "/api/v1/scrape", 401, "{\"success\":false,\"error\":\"invalid key\"}");
            var future = client(server).scrapeAsync("https://example.com", null);
            var thrown = assertThrows(java.util.concurrent.CompletionException.class, future::join);
            assertTrue(thrown.getCause() instanceof AuthenticationException);
            assertEquals("invalid key",
                    ((AuthenticationException) thrown.getCause()).getMessage().contains("invalid key")
                            ? "invalid key" : "");
        }
    }

    @Test
    void crawlAsyncPollsUntilCompleted() throws Exception {
        try (MockServer server = new MockServer()) {
            server.enqueue("POST", "/api/v1/crawl", 200, "{\"success\":true,\"id\":\"job-a1\"}");
            server.enqueue("GET", "/api/v1/crawl/job-a1", 200,
                    "{\"success\":true,\"status\":\"scraping\",\"total\":2,\"completed\":1}");
            server.enqueue("GET", "/api/v1/crawl/job-a1", 200,
                    "{\"success\":true,\"status\":\"completed\",\"total\":2,\"completed\":2,"
                            + "\"data\":[{\"markdown\":\"# A\"}],\"errors\":[]}");
            CrawlJob job = client(server).crawlAsync("https://x",
                    new CrawlOptions().limit(2), 0.01, 0).join();
            assertEquals("completed", job.status());
            assertEquals(1, job.data().size());
        }
    }
}
