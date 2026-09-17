package ai.webpipe.sdk.exceptions;

/** A crawl job reached status "failed". */
public class CrawlException extends WebpipeException {
    public CrawlException(String message) {
        super(message);
    }
}
