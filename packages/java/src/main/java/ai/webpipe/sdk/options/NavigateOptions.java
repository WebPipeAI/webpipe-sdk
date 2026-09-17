package ai.webpipe.sdk.options;

import java.util.List;

/** Optional parameters of {@code BrowserSession.navigate(...)} and
 * {@code BrowserSession.scrape(...)}. Fluent setters return {@code this}. */
public class NavigateOptions {
    public List<String> formats;
    /** Scroll to the bottom to trigger lazy-loaded content. */
    public Boolean scrollToBottom;
    /** Max scroll rounds (1-30, default 10). */
    public Integer maxScrolls;
    /** Wait after each scroll, ms (500-8000, default 2000). */
    public Integer scrollWait;
    /** Pixels per scroll (200-5000, default 1200). */
    public Integer scrollStep;

    public NavigateOptions formats(List<String> v) {
        this.formats = v;
        return this;
    }

    public NavigateOptions scrollToBottom(Boolean v) {
        this.scrollToBottom = v;
        return this;
    }

    public NavigateOptions maxScrolls(Integer v) {
        this.maxScrolls = v;
        return this;
    }

    public NavigateOptions scrollWait(Integer v) {
        this.scrollWait = v;
        return this;
    }

    public NavigateOptions scrollStep(Integer v) {
        this.scrollStep = v;
        return this;
    }
}
