package ai.webpipe.sdk;

import ai.webpipe.sdk.internal.HttpClient;
import ai.webpipe.sdk.model.SessionInfo;
import ai.webpipe.sdk.options.CreateSessionOptions;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;

/** Factory for browser sessions, exposed as {@code client.browser}. */
public final class BrowserResource {
    private final HttpClient http;

    BrowserResource(HttpClient http) {
        this.http = http;
    }

    /** Create a session. It starts asynchronously — call
     * {@link BrowserSession#waitUntilRunning(double, double)} before sending
     * commands for a controlled experience. */
    public BrowserSession create(CreateSessionOptions options) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (options != null) {
            if (options.url != null) body.put("url", options.url);
            if (options.cookie != null) body.put("cookie", options.cookie);
            if (options.proxy != null) body.put("proxy", options.proxy);
            if (options.sessionName != null) body.put("session_name", options.sessionName);
        }
        JsonNode res = http.post("/api/v1/browser", body);
        SessionInfo info = HttpClient.responseMapper().convertValue(res, SessionInfo.class);
        return new BrowserSession(http, info);
    }
}
