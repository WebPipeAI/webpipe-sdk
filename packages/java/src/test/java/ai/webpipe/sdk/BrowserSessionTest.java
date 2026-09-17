package ai.webpipe.sdk;

import static org.junit.jupiter.api.Assertions.*;

import ai.webpipe.sdk.model.Document;
import ai.webpipe.sdk.options.CreateSessionOptions;
import ai.webpipe.sdk.options.NavigateOptions;
import java.util.List;
import org.junit.jupiter.api.Test;

class BrowserSessionTest {

    private WebpipeClient client(MockServer server) {
        return WebpipeClient.builder()
                .apiKey("wc-test-key")
                .baseUrl(server.url())
                .maxRetries(0)
                .build();
    }

    @Test
    void sessionLifecycle() throws Exception {
        try (MockServer server = new MockServer()) {
            server.enqueue("POST", "/api/v1/browser", 200,
                    "{\"success\":true,\"id\":\"sess-1\",\"status\":\"starting\"}");
            server.enqueue("GET", "/api/v1/browser/sess-1", 200,
                    "{\"success\":true,\"id\":\"sess-1\",\"status\":\"starting\"}");
            server.enqueue("GET", "/api/v1/browser/sess-1", 200,
                    "{\"success\":true,\"id\":\"sess-1\",\"status\":\"running\"}");
            server.enqueue("POST", "/api/v1/browser/sess-1/scrape", 200,
                    "{\"success\":true,\"data\":{\"markdown\":\"# Hi\"}}");
            server.enqueue("POST", "/api/v1/browser/sess-1/action", 200,
                    "{\"success\":true,\"ok\":true}");
            server.enqueue("DELETE", "/api/v1/browser/sess-1", 200, "{\"success\":true}");

            BrowserSession session = client(server).browser.create(
                    new CreateSessionOptions().url("https://example.com").sessionName("demo"));
            assertEquals("sess-1", session.getId());
            assertEquals("starting", session.getStatus());
            assertEquals("demo", server.lastRequest().body().get("session_name").asText());

            session.waitUntilRunning(0.01, 5);
            assertEquals("running", session.getStatus());

            Document doc = session.scrape(new NavigateOptions().formats(List.of("markdown")));
            assertEquals("# Hi", doc.markdown());

            session.click("button.more", 500);
            var actionBody = server.lastRequest().body();
            assertEquals("click", actionBody.get("type").asText());
            assertEquals("button.more", actionBody.get("selector").asText());
            assertEquals(500, actionBody.get("wait_after").asInt());

            session.close();
            assertEquals("stopped", session.getStatus());
            assertEquals("DELETE", server.lastRequest().method());
        }
    }

    @Test
    void cookies() throws Exception {
        try (MockServer server = new MockServer()) {
            server.enqueue("POST", "/api/v1/browser", 200,
                    "{\"success\":true,\"id\":\"sess-2\",\"status\":\"running\"}");
            server.enqueue("GET", "/api/v1/browser/sess-2/cookies", 200,
                    "{\"success\":true,\"cookies\":[{\"name\":\"session\",\"value\":\"abc\"}]}");
            server.enqueue("POST", "/api/v1/browser/sess-2/cookies", 200, "{\"success\":true}");
            server.enqueue("POST", "/api/v1/browser/sess-2/cookies", 200, "{\"success\":true}");

            BrowserSession session = client(server).browser.create(null);
            assertEquals("session", session.getCookies().get(0).name());

            session.setCookies(List.of(new ai.webpipe.sdk.model.Cookie("x", "1", "example.com", "/")));
            assertEquals("set", server.lastRequest().body().get("op").asText());

            session.clearCookies();
            assertEquals("clear", server.lastRequest().body().get("op").asText());
        }
    }

    @Test
    void asyncSessionLifecycle() throws Exception {
        try (MockServer server = new MockServer()) {
            server.enqueue("POST", "/api/v1/browser", 200,
                    "{\"success\":true,\"id\":\"sess-a1\",\"status\":\"starting\"}");
            server.enqueue("GET", "/api/v1/browser/sess-a1", 200,
                    "{\"success\":true,\"id\":\"sess-a1\",\"status\":\"running\"}");
            server.enqueue("POST", "/api/v1/browser/sess-a1/scrape", 200,
                    "{\"success\":true,\"data\":{\"markdown\":\"# Async\"}}");
            server.enqueue("POST", "/api/v1/browser/sess-a1/action", 200,
                    "{\"success\":true,\"ok\":true}");
            server.enqueue("DELETE", "/api/v1/browser/sess-a1", 200, "{\"success\":true}");

            BrowserSession session = client(server).browser.create(
                    new CreateSessionOptions().url("https://example.com"));

            session.waitUntilRunningAsync(0.01, 5).join();
            assertEquals("running", session.getStatus());

            Document doc = session.scrapeAsync(new NavigateOptions().formats(List.of("markdown"))).join();
            assertEquals("# Async", doc.markdown());

            session.clickAsync("button.more", 500).join();
            assertEquals("click", server.lastRequest().body().get("type").asText());

            session.closeAsync().join();
            assertEquals("stopped", session.getStatus());
        }
    }
}
