package ai.webpipe.sdk.exceptions;

/** Polling did not finish within the allotted timeout. */
public class PollTimeoutException extends WebpipeException {
    public PollTimeoutException(String message) {
        super(message);
    }
}
