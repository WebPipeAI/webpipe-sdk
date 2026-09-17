package ai.webpipe.sdk.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** A URL discovered by the Map endpoint. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MapLink(String url, String title, String description) {
}
