package ai.webpipe.sdk.exceptions;

/** 401 — API key missing, invalid or deleted. */
public class AuthenticationException extends ApiException {
    public AuthenticationException(String message, int statusCode, Object body) {
        super(message, statusCode, body);
    }
}
