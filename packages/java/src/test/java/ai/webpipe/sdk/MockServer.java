package ai.webpipe.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** JDK built-in mock HTTP server. Responses are queued per "METHOD /path". */
final class MockServer implements AutoCloseable {
    record Request(String method, String path, JsonNode body, Map<String, String> headers) {
    }

    private final HttpServer server;
    private final Map<String, Deque<int[]>> statuses = new HashMap<>();
    private final Map<String, Deque<String>> bodies = new HashMap<>();
    final List<Request> requests = new ArrayList<>();

    MockServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    /** Queue a response: enqueue("POST", "/api/v1/scrape", 200, json). */
    MockServer enqueue(String method, String path, int status, String jsonBody) {
        String key = method + " " + path;
        statuses.computeIfAbsent(key, k -> new ArrayDeque<>()).add(new int[] {status});
        bodies.computeIfAbsent(key, k -> new ArrayDeque<>()).add(jsonBody);
        return this;
    }

    String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    Request lastRequest() {
        return requests.get(requests.size() - 1);
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private void handle(HttpExchange exchange) throws IOException {
        String key = exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath();
        int status = 404;
        String body = "{\"error\":\"no mock for " + key + "\"}";
        Deque<int[]> statusQueue = statuses.get(key);
        Deque<String> bodyQueue = bodies.get(key);
        if (statusQueue != null && !statusQueue.isEmpty()) {
            status = statusQueue.poll()[0];
            body = bodyQueue.poll();
        }

        String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> headers = new HashMap<>();
        exchange.getRequestHeaders().forEach((k, v) -> headers.put(k, String.join(",", v)));
        JsonNode parsed = null;
        try {
            parsed = requestBody.isEmpty() ? null : MAPPER.readTree(requestBody);
        } catch (Exception ignored) {
        }
        requests.add(new Request(exchange.getRequestMethod(), exchange.getRequestURI().getPath(), parsed, headers));

        byte[] out = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, out.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(out);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
