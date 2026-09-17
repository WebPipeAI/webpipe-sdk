package ai.webpipe.sdk.exceptions;

/** 429 — rate limit exceeded, or too many concurrent browser sessions. */
public class RateLimitException extends ApiException {
    public RateLimitException(String message, int statusCode, Object body) {
        super(message, statusCode, body);
    }
}
