package ai.webpipe.sdk.exceptions;

/** 400 — invalid parameters (missing url, bad format, ...). */
public class BadRequestException extends ApiException {
    public BadRequestException(String message, int statusCode, Object body) {
        super(message, statusCode, body);
    }
}
