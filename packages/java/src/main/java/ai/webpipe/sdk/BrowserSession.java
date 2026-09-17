package ai.webpipe.sdk;

import ai.webpipe.sdk.exceptions.PollTimeoutException;
import ai.webpipe.sdk.exceptions.WebpipeException;
import ai.webpipe.sdk.internal.HttpClient;
import ai.webpipe.sdk.model.Cookie;
import ai.webpipe.sdk.model.Document;
import ai.webpipe.sdk.model.SessionInfo;
import ai.webpipe.sdk.options.NavigateOptions;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Handle to a persistent headless Chromium session on the server.
 *
 * <p>Server-side facts: sessions expire after 30 minutes idle; at most 2
 * concurrent running sessions per user. Always {@link #close()} when done.
 */
public final class BrowserSession {
    private final HttpClient http;
    private final String id;
    private String status;
    private String url;
    private String sessionName;
    private String expiresAt;

    BrowserSession(HttpClient http, SessionInfo info) {
        this.http = http;
        this.id = info.id();
        apply(info);
    }

    private void apply(SessionInfo info) {
        this.status = info.status();
        this.url = info.url();
        this.sessionName = info.sessionName();
        this.expiresAt = info.expiresAt();
    }

    public String getId() {
        return id;
    }

    public String getStatus() {
        return status;
    }

    public String getUrl() {
        return url;
    }

    private String base() {
        return "/api/v1/browser/" + id;
    }

    /** Fetch the latest session state from the server. */
    public BrowserSession refresh() {
        JsonNode res = http.get(base());
        apply(HttpClient.responseMapper().convertValue(res, SessionInfo.class));
        return this;
    }

    /** Block until the session is ready to accept commands.
     * @param pollInterval seconds between polls; @param timeout seconds (0 = 60) */
    public BrowserSession waitUntilRunning(double pollInterval, double timeout) {
        if (pollInterval <= 0) pollInterval = 1;
        if (timeout <= 0) timeout = 60;
        long deadline = System.currentTimeMillis() + (long) (timeout * 1000);
        while (true) {
            refresh();
            if (SessionInfo.STATUS_RUNNING.equals(status)) {
                return this;
            }
            if (SessionInfo.STATUS_STOPPED.equals(status)
                    || SessionInfo.STATUS_ERROR.equals(status)
                    || SessionInfo.STATUS_EXPIRED.equals(status)) {
                throw new WebpipeException(
                        "Browser session " + id + " entered terminal status '" + status + "'");
            }
            if (System.currentTimeMillis() >= deadline) {
                throw new PollTimeoutException(
                        "Browser session " + id + " not running after " + timeout + "s");
            }
            sleep(pollInterval);
        }
    }

    /** Stop the session and release server resources. */
    public void close() {
        http.delete(base());
        this.status = SessionInfo.STATUS_STOPPED;
    }

    /** Navigate to a new URL (with anti-bot handling) and scrape it. */
    public Document navigate(String url, NavigateOptions options) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("url", url);
        body.putAll(scrapeBody(options));
        JsonNode res = http.post(base() + "/navigate", body);
        return HttpClient.responseMapper().convertValue(res.path("data"), Document.class);
    }

    /** Scrape the current page without navigating (faster than navigate). */
    public Document scrape(NavigateOptions options) {
        JsonNode res = http.post(base() + "/scrape", scrapeBody(options));
        return HttpClient.responseMapper().convertValue(res.path("data"), Document.class);
    }

    /** Run a raw browser action. {@code params} must be snake_case already;
     * returns the raw API payload. */
    public Map<String, Object> action(String type, Map<String, Object> params) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", type);
        if (params != null) {
            body.putAll(params);
        }
        JsonNode res = http.post(base() + "/action", body);
        return HttpClient.responseMapper().convertValue(res, new TypeReference<>() {
        });
    }

    public Map<String, Object> click(String selector, Integer waitAfter) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("selector", selector);
        if (waitAfter != null) params.put("wait_after", waitAfter);
        return action("click", params);
    }

    public Map<String, Object> fill(String selector, String value) {
        return action("fill", Map.of("selector", selector, "value", value));
    }

    public Map<String, Object> select(String selector, String value) {
        return action("select", Map.of("selector", selector, "value", value));
    }

    public Map<String, Object> press(String key, String selector, Integer waitAfter) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("key", key);
        if (selector != null) params.put("selector", selector);
        if (waitAfter != null) params.put("wait_after", waitAfter);
        return action("press", params);
    }

    public Map<String, Object> scroll(String direction, Integer amount) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("direction", direction == null ? "down" : direction);
        if (amount != null) params.put("amount", amount);
        return action("scroll", params);
    }

    public Map<String, Object> wait(int ms) {
        return action("wait", Map.of("ms", ms));
    }

    public Map<String, Object> waitFor(String selector, Integer timeout) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("selector", selector);
        if (timeout != null) params.put("timeout", timeout);
        return action("wait_for", params);
    }

    /** Take a screenshot. The API returns a base64 PNG inside the payload. */
    public Map<String, Object> screenshot(boolean fullPage) {
        return action("screenshot", Map.of("full_page", fullPage));
    }

    /** Evaluate a JavaScript expression in the page (value in payload's "result"). */
    public Map<String, Object> evaluate(String expression) {
        return action("evaluate", Map.of("expression", expression));
    }

    public Map<String, Object> hover(String selector) {
        return action("hover", Map.of("selector", selector));
    }

    public Map<String, Object> check(String selector) {
        return action("check", Map.of("selector", selector));
    }

    /** Read the browser's current cookies. */
    public List<Cookie> getCookies() {
        JsonNode res = http.get(base() + "/cookies");
        JsonNode arr = res.path("cookies").isArray() ? res.path("cookies") : res.path("data");
        return HttpClient.responseMapper().convertValue(
                arr,
                HttpClient.responseMapper().getTypeFactory().constructCollectionType(List.class, Cookie.class));
    }

    /** Inject cookies into the browser context. */
    public Map<String, Object> setCookies(List<Cookie> cookies) {
        JsonNode res = http.post(base() + "/cookies", Map.of("op", "set", "cookies", cookies));
        return HttpClient.responseMapper().convertValue(res, new TypeReference<>() {
        });
    }

    public Map<String, Object> clearCookies() {
        JsonNode res = http.post(base() + "/cookies", Map.of("op", "clear"));
        return HttpClient.responseMapper().convertValue(res, new TypeReference<>() {
        });
    }

    /** Persist cookies/storage under this session's sessionName. */
    public Map<String, Object> saveSession() {
        JsonNode res = http.post(base() + "/save_session", null);
        return HttpClient.responseMapper().convertValue(res, new TypeReference<>() {
        });
    }

    private static Map<String, Object> scrapeBody(NavigateOptions options) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (options != null) {
            if (options.formats != null) body.put("formats", options.formats);
            if (options.scrollToBottom != null) body.put("scroll_to_bottom", options.scrollToBottom);
            if (options.maxScrolls != null) body.put("max_scrolls", options.maxScrolls);
            if (options.scrollWait != null) body.put("scroll_wait", options.scrollWait);
            if (options.scrollStep != null) body.put("scroll_step", options.scrollStep);
        }
        return body;
    }

    private static void sleep(double seconds) {
        try {
            Thread.sleep((long) (seconds * 1000));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PollTimeoutException("Interrupted while polling");
        }
    }

    // ------------------------------------------------------------ async API
    // Non-blocking variants with identical semantics. Sync methods above are
    // the default for simplicity; use these for concurrent/reactive code.

    /** Async variant of {@link #refresh()}. */
    public CompletableFuture<BrowserSession> refreshAsync() {
        return http.getAsync(base())
                .thenApply(res -> {
                    apply(HttpClient.responseMapper().convertValue(res, SessionInfo.class));
                    return this;
                });
    }

    /** Async variant of {@link #waitUntilRunning(double, double)}: polls via the
     * scheduler instead of blocking a thread. */
    public CompletableFuture<BrowserSession> waitUntilRunningAsync(double pollInterval, double timeout) {
        double pi = pollInterval <= 0 ? 1 : pollInterval;
        double to = timeout <= 0 ? 60 : timeout;
        long deadline = System.currentTimeMillis() + (long) (to * 1000);
        return pollUntilRunning(pi, deadline);
    }

    private CompletableFuture<BrowserSession> pollUntilRunning(double pollInterval, long deadline) {
        return refreshAsync().thenCompose(s -> {
            if (SessionInfo.STATUS_RUNNING.equals(status)) {
                return CompletableFuture.completedFuture(this);
            }
            if (SessionInfo.STATUS_STOPPED.equals(status)
                    || SessionInfo.STATUS_ERROR.equals(status)
                    || SessionInfo.STATUS_EXPIRED.equals(status)) {
                throw new CompletionException(new WebpipeException(
                        "Browser session " + id + " entered terminal status '" + status + "'"));
            }
            if (System.currentTimeMillis() >= deadline) {
                throw new CompletionException(new PollTimeoutException(
                        "Browser session " + id + " not running within the timeout"));
            }
            return HttpClient.delay((long) (pollInterval * 1000))
                    .thenCompose(v -> pollUntilRunning(pollInterval, deadline));
        });
    }

    /** Async variant of {@link #close()}. */
    public CompletableFuture<Void> closeAsync() {
        return http.deleteAsync(base())
                .thenAccept(res -> {
                    this.status = SessionInfo.STATUS_STOPPED;
                });
    }

    /** Async variant of {@link #navigate(String, NavigateOptions)}. */
    public CompletableFuture<Document> navigateAsync(String url, NavigateOptions options) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("url", url);
        body.putAll(scrapeBody(options));
        return http.postAsync(base() + "/navigate", body)
                .thenApply(res -> HttpClient.responseMapper().convertValue(res.path("data"), Document.class));
    }

    /** Async variant of {@link #scrape(NavigateOptions)}. */
    public CompletableFuture<Document> scrapeAsync(NavigateOptions options) {
        return http.postAsync(base() + "/scrape", scrapeBody(options))
                .thenApply(res -> HttpClient.responseMapper().convertValue(res.path("data"), Document.class));
    }

    /** Async variant of {@link #action(String, Map)}. */
    public CompletableFuture<Map<String, Object>> actionAsync(String type, Map<String, Object> params) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", type);
        if (params != null) {
            body.putAll(params);
        }
        return http.postAsync(base() + "/action", body)
                .thenApply(res -> HttpClient.responseMapper().convertValue(res, new TypeReference<>() {
                }));
    }

    public CompletableFuture<Map<String, Object>> clickAsync(String selector, Integer waitAfter) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("selector", selector);
        if (waitAfter != null) params.put("wait_after", waitAfter);
        return actionAsync("click", params);
    }

    public CompletableFuture<Map<String, Object>> fillAsync(String selector, String value) {
        return actionAsync("fill", Map.of("selector", selector, "value", value));
    }

    public CompletableFuture<Map<String, Object>> selectAsync(String selector, String value) {
        return actionAsync("select", Map.of("selector", selector, "value", value));
    }

    public CompletableFuture<Map<String, Object>> pressAsync(String key, String selector, Integer waitAfter) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("key", key);
        if (selector != null) params.put("selector", selector);
        if (waitAfter != null) params.put("wait_after", waitAfter);
        return actionAsync("press", params);
    }

    public CompletableFuture<Map<String, Object>> scrollAsync(String direction, Integer amount) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("direction", direction == null ? "down" : direction);
        if (amount != null) params.put("amount", amount);
        return actionAsync("scroll", params);
    }

    public CompletableFuture<Map<String, Object>> waitAsync(int ms) {
        return actionAsync("wait", Map.of("ms", ms));
    }

    public CompletableFuture<Map<String, Object>> waitForAsync(String selector, Integer timeout) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("selector", selector);
        if (timeout != null) params.put("timeout", timeout);
        return actionAsync("wait_for", params);
    }

    public CompletableFuture<Map<String, Object>> screenshotAsync(boolean fullPage) {
        return actionAsync("screenshot", Map.of("full_page", fullPage));
    }

    public CompletableFuture<Map<String, Object>> evaluateAsync(String expression) {
        return actionAsync("evaluate", Map.of("expression", expression));
    }

    public CompletableFuture<Map<String, Object>> hoverAsync(String selector) {
        return actionAsync("hover", Map.of("selector", selector));
    }

    public CompletableFuture<Map<String, Object>> checkAsync(String selector) {
        return actionAsync("check", Map.of("selector", selector));
    }

    /** Async variant of {@link #getCookies()}. */
    public CompletableFuture<List<Cookie>> getCookiesAsync() {
        return http.getAsync(base() + "/cookies")
                .thenApply(res -> {
                    JsonNode arr = res.path("cookies").isArray() ? res.path("cookies") : res.path("data");
                    return HttpClient.responseMapper().convertValue(
                            arr,
                            HttpClient.responseMapper()
                                    .getTypeFactory()
                                    .constructCollectionType(List.class, Cookie.class));
                });
    }

    /** Async variant of {@link #setCookies(List)}. */
    public CompletableFuture<Map<String, Object>> setCookiesAsync(List<Cookie> cookies) {
        return http.postAsync(base() + "/cookies", Map.of("op", "set", "cookies", cookies))
                .thenApply(res -> HttpClient.responseMapper().convertValue(res, new TypeReference<>() {
                }));
    }

    /** Async variant of {@link #clearCookies()}. */
    public CompletableFuture<Map<String, Object>> clearCookiesAsync() {
        return http.postAsync(base() + "/cookies", Map.of("op", "clear"))
                .thenApply(res -> HttpClient.responseMapper().convertValue(res, new TypeReference<>() {
                }));
    }

    /** Async variant of {@link #saveSession()}. */
    public CompletableFuture<Map<String, Object>> saveSessionAsync() {
        return http.postAsync(base() + "/save_session", null)
                .thenApply(res -> HttpClient.responseMapper().convertValue(res, new TypeReference<>() {
                }));
    }
}
