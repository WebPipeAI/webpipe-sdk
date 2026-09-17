package ai.webpipe.sdk.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** A browser cookie. domain/path are needed when injecting cookies. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Cookie(String name, String value, String domain, String path) {
    public Cookie(String name, String value) {
        this(name, value, null, null);
    }
}
