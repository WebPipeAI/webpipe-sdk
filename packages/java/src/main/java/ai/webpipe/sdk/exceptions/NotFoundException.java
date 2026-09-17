package ai.webpipe.sdk.exceptions;

/** 404 — job/session does not exist or has expired. */
public class NotFoundException extends ApiException {
    public NotFoundException(String message, int statusCode, Object body) {
        super(message, statusCode, body);
    }
}
