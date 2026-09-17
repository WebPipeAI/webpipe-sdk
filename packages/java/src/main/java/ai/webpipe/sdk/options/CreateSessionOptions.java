package ai.webpipe.sdk.options;

/** Optional parameters of {@code BrowserResource.create(...)}.
 * Fluent setters return {@code this}. */
public class CreateSessionOptions {
    /** Navigate to this URL right after the browser starts. */
    public String url;
    /** Initial cookie header string injected into the browser context. */
    public String cookie;
    public String proxy;
    /** Named sessions share persisted login state (see {@code saveSession()}). */
    public String sessionName;

    public CreateSessionOptions url(String v) {
        this.url = v;
        return this;
    }

    public CreateSessionOptions cookie(String v) {
        this.cookie = v;
        return this;
    }

    public CreateSessionOptions proxy(String v) {
        this.proxy = v;
        return this;
    }

    public CreateSessionOptions sessionName(String v) {
        this.sessionName = v;
        return this;
    }
}
