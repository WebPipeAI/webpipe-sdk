package ai.webpipe.sdk.internal;

import ai.webpipe.sdk.exceptions.ApiException;
import ai.webpipe.sdk.exceptions.WebpipeException;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

/**
 * Thin java.net.http wrapper: auth headers, retries with backoff, error mapping.
 * <strong>Internal — never use from outside the SDK.</strong>
 */
public final class HttpClient {
    private static final Set<Integer> RETRYABLE_STATUSES = Set.of(429, 500, 502, 503, 504);

    private final String baseUrl;
    private final String apiKey;
    private final Duration timeout;
    private final int maxRetries;
    private final String userAgent;
    private final java.net.http.HttpClient client;

    /** Snake_case naming for request bodies; NON_NULL so unset fields are omitted.
     * Map keys (e.g. inside jsonSchema) are never transformed. */
    private static final ObjectMapper REQUEST_MAPPER = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private static final ObjectMapper RESPONSE_MAPPER = new ObjectMapper();

    public HttpClient(String baseUrl, String apiKey, Duration timeout, int maxRetries, String userAgent) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.timeout = timeout;
        this.maxRetries = maxRetries;
        this.userAgent = userAgent;
        this.client = java.net.http.HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();
    }

    public static ObjectMapper responseMapper() {
        return RESPONSE_MAPPER;
    }

    public JsonNode request(String method, String path, Object body) {
        int attempt = 0;
        while (true) {
            HttpResponse<String> response;
            try {
                response = client.send(build(method, path, body), HttpResponse.BodyHandlers.ofString());
            } catch (IOException e) {
                if (attempt >= maxRetries) {
                    throw new WebpipeException("webpipe: " + method + " " + path + " failed: " + e.getMessage(), e);
                }
                attempt++;
                sleep(backoffMillis(null, attempt));
                continue;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new WebpipeException("webpipe: interrupted", e);
            }

            int status = response.statusCode();
            if (RETRYABLE_STATUSES.contains(status) && attempt < maxRetries) {
                attempt++;
                sleep(backoffMillis(response.headers().firstValue("Retry-After").orElse(null), attempt));
                continue;
            }

            JsonNode payload = parse(response.body());
            if (status >= 400) {
                String message = payload != null && payload.hasNonNull("error")
                        ? payload.get("error").asText()
                        : response.body();
                throw ApiException.forStatus(status, message, payload);
            }
            return payload;
        }
    }

    public JsonNode get(String path) {
        return request("GET", path, null);
    }

    public JsonNode post(String path, Object body) {
        return request("POST", path, body);
    }

    public JsonNode delete(String path) {
        return request("DELETE", path, null);
    }

    // ------------------------------------------------------------ async API

    /** Async variant of {@link #request}: non-blocking, same retry/error semantics. */
    public CompletableFuture<JsonNode> requestAsync(String method, String path, Object body) {
        return sendWithRetry(method, path, body, 0);
    }

    public CompletableFuture<JsonNode> getAsync(String path) {
        return requestAsync("GET", path, null);
    }

    public CompletableFuture<JsonNode> postAsync(String path, Object body) {
        return requestAsync("POST", path, body);
    }

    public CompletableFuture<JsonNode> deleteAsync(String path) {
        return requestAsync("DELETE", path, null);
    }

    private CompletableFuture<JsonNode> sendWithRetry(String method, String path, Object body, int attempt) {
        return client
                .sendAsync(build(method, path, body), HttpResponse.BodyHandlers.ofString())
                .handle((response, error) -> {
                    if (error != null) {
                        if (attempt < maxRetries) {
                            return delay(backoffMillis(null, attempt + 1))
                                    .thenCompose(v -> sendWithRetry(method, path, body, attempt + 1));
                        }
                        throw new CompletionException(new WebpipeException(
                                "webpipe: " + method + " " + path + " failed: " + error.getMessage(), error));
                    }
                    int status = response.statusCode();
                    if (RETRYABLE_STATUSES.contains(status) && attempt < maxRetries) {
                        long wait = backoffMillis(
                                response.headers().firstValue("Retry-After").orElse(null), attempt + 1);
                        return delay(wait).thenCompose(v -> sendWithRetry(method, path, body, attempt + 1));
                    }
                    JsonNode payload = parse(response.body());
                    if (status >= 400) {
                        String message = payload != null && payload.hasNonNull("error")
                                ? payload.get("error").asText()
                                : response.body();
                        throw new CompletionException(ApiException.forStatus(status, message, payload));
                    }
                    return CompletableFuture.completedFuture(payload);
                })
                .thenCompose(future -> future);
    }

    /** Delays completion by the given milliseconds (used for async retry backoff
     * and polling). */
    public static CompletableFuture<Void> delay(long millis) {
        CompletableFuture<Void> d = new CompletableFuture<>();
        CompletableFuture.delayedExecutor(millis, TimeUnit.MILLISECONDS)
                .execute(() -> d.complete(null));
        return d;
    }

    /** Flatten an options object into a snake_case map, with {@code url} first.
     * Null fields are omitted; nested Map keys (e.g. jsonSchema) pass through. */
    public Map<String, Object> bodyWithUrl(String url, Object options) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("url", url);
        if (options != null) {
            body.putAll(REQUEST_MAPPER.convertValue(options, new TypeReference<>() {
            }));
        }
        return body;
    }

    public byte[] toJson(Object body) {
        try {
            return REQUEST_MAPPER.writeValueAsBytes(body);
        } catch (IOException e) {
            throw new WebpipeException("webpipe: encode request failed", e);
        }
    }

    private HttpRequest build(String method, String path, Object body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(timeout)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("User-Agent", userAgent);
        if (body != null) {
            builder.method(method, HttpRequest.BodyPublishers.ofByteArray(toJson(body)));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }
        return builder.build();
    }

    private static JsonNode parse(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            return RESPONSE_MAPPER.readTree(raw);
        } catch (IOException e) {
            return null;
        }
    }

    private static long backoffMillis(String retryAfter, int attempt) {
        if (retryAfter != null) {
            try {
                return (long) (Double.parseDouble(retryAfter) * 1000);
            } catch (NumberFormatException ignored) {
                // fall through to exponential backoff
            }
        }
        return (long) (0.5 * Math.pow(2, attempt) * 1000);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WebpipeException("webpipe: interrupted", e);
        }
    }
}
