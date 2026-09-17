package ai.webpipe.sdk.exceptions;

/** 403 — accessing a resource owned by another user. */
public class ForbiddenException extends ApiException {
    public ForbiddenException(String message, int statusCode, Object body) {
        super(message, statusCode, body);
    }
}
