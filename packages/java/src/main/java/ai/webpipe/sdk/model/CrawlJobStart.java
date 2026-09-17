package ai.webpipe.sdk.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Handle returned when a crawl job is submitted. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CrawlJobStart(String id, String url) {
}
