package ai.webpipe.sdk.exceptions;

/** Base class for all webpipe-sdk exceptions. */
public class WebpipeException extends RuntimeException {
    public WebpipeException(String message) {
        super(message);
    }

    public WebpipeException(String message, Throwable cause) {
        super(message, cause);
    }
}
