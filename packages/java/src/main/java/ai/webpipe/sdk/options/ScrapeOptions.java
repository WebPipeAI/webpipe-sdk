package ai.webpipe.sdk.options;

import java.util.List;
import java.util.Map;

/** Optional parameters of {@code scrape(...)}. Fluent setters return {@code this}.
 * Serialized to snake_case; {@code jsonSchema} map keys pass through untouched. */
public class ScrapeOptions {
    /** Output formats: "markdown" (default), "html", "rawHtml", "links", "metadata", "json". */
    public List<String> formats;
    /** JSON Schema for structured extraction (requires "json" in formats). */
    public Map<String, Object> jsonSchema;
    /** Use the server-side proxy pool. */
    public Boolean useProxy;
    /** Custom proxy URL, takes precedence over the proxy pool. */
    public String proxy;
    /** Cookie header string, e.g. "session=abc; user=123". */
    public String cookie;

    public ScrapeOptions formats(List<String> v) {
        this.formats = v;
        return this;
    }

    public ScrapeOptions jsonSchema(Map<String, Object> v) {
        this.jsonSchema = v;
        return this;
    }

    public ScrapeOptions useProxy(Boolean v) {
        this.useProxy = v;
        return this;
    }

    public ScrapeOptions proxy(String v) {
        this.proxy = v;
        return this;
    }

    public ScrapeOptions cookie(String v) {
        this.cookie = v;
        return this;
    }
}
