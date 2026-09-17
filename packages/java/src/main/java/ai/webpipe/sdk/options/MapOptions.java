package ai.webpipe.sdk.options;

/** Optional parameters of {@code map(...)}. Fluent setters return {@code this}. */
public class MapOptions {
    /** Max URLs to return (default 5000, max 50000). */
    public Integer limit;
    /** Keyword filter over url/title/description, ranked by relevance. */
    public String search;
    /** "include" (default) | "only" (sitemap only) | "exclude" (links only). */
    public String sitemap;
    /** Restrict to the same root domain (server default true). */
    public Boolean sameDomain;
    public Boolean useProxy;
    public String proxy;
    public String cookie;

    public MapOptions limit(Integer v) {
        this.limit = v;
        return this;
    }

    public MapOptions search(String v) {
        this.search = v;
        return this;
    }

    public MapOptions sitemap(String v) {
        this.sitemap = v;
        return this;
    }

    public MapOptions sameDomain(Boolean v) {
        this.sameDomain = v;
        return this;
    }

    public MapOptions useProxy(Boolean v) {
        this.useProxy = v;
        return this;
    }

    public MapOptions proxy(String v) {
        this.proxy = v;
        return this;
    }

    public MapOptions cookie(String v) {
        this.cookie = v;
        return this;
    }
}
