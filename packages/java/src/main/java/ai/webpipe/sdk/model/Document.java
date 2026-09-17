package ai.webpipe.sdk.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/** Result of scraping a single page (scrape / browser / crawl data item).
 * {@code metadata} is a plain map — the API adds extra keys (og:*, sourceURL, ...). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Document(
        String markdown,
        String html,
        @JsonProperty("rawHtml") String rawHtml,
        List<Link> links,
        Map<String, Object> metadata,
        Object json) {
}
