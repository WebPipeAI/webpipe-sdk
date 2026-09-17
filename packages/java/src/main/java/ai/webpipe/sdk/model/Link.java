package ai.webpipe.sdk.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** A link extracted from a page. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Link(String text, String href) {
}
