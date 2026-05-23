package run.halo.links.extension;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;

/**
 * @author guqing
 * @author ryanwang
 * @since 2.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "Link extension that represents one website entry displayed by the Links plugin.")
@GVK(group = "core.halo.run", version = "v1alpha1",
        kind = "Link", plural = "links", singular = "link")
public class Link extends AbstractExtension {

    @Schema(description = "Desired state of the link.", requiredMode = REQUIRED)
    private LinkSpec spec;

    @Schema(description = "Observed state of the link.", requiredMode = NOT_REQUIRED)
    private LinkStatus status;

    public LinkStatus getStatus() {
        if (status == null) {
            status = new LinkStatus();
        }
        return status;
    }

    @Data
    @Schema(description = "Configurable fields of a link.")
    public static class LinkSpec {
        @Schema(description = "Absolute URL that the link points to.", requiredMode = REQUIRED)
        private String url;

        @Schema(description = "Human-readable name displayed for the link.", requiredMode = REQUIRED)
        private String displayName;

        @Schema(description = "Logo or icon URL displayed with the link.")
        private String logo;

        @Schema(description = "Short text that introduces the linked website.")
        private String description;

        @Schema(description = "Sort order of the link; lower values appear earlier.")
        private Integer priority;

        @Schema(description = "Metadata name of the LinkGroup that this link belongs to.")
        private String groupName;

        @Schema(description = "RSS or Atom feed tracking settings for this link.")
        private RssSpec rss;
    }

    @Data
    @Schema(description = "RSS or Atom feed tracking settings.")
    public static class RssSpec {
        @Schema(description = "Whether RSS or Atom tracking is enabled for this link.")
        private Boolean enabled;

        @Schema(description = "Absolute HTTP or HTTPS URLs of the RSS or Atom feeds.")
        private List<String> feedUrls;
    }

    @Data
    @Schema(description = "Observed state of a link.")
    public static class LinkStatus {
        @Schema(description = "Observed RSS or Atom feed refresh state.")
        private RssStatus rss;
    }

    @Data
    @Schema(description = "Observed RSS or Atom feed refresh state.")
    public static class RssStatus {
        @Schema(description = "Last time a feed refresh was attempted.")
        private Instant lastFetchedAt;

        @Schema(description = "Last time a feed refresh completed successfully.")
        private Instant lastSuccessAt;

        @Schema(description = "Last feed refresh failure message.")
        private String lastError;

        @Schema(description = "Number of consecutive feed refresh failures.")
        private Integer failureCount;

        @Schema(description = "Latest feed item publication time observed for this link.")
        private Instant latestPublishedAt;

        @Schema(description = "Number of cached feed items for this link.")
        private Long itemCount;

        @Schema(description = "Observed refresh state for each configured feed URL.")
        private List<RssFeedStatus> feeds;
    }

    @Data
    @Schema(description = "Observed RSS or Atom refresh state for one configured feed URL.")
    public static class RssFeedStatus {
        @Schema(description = "Configured feed URL this status belongs to.")
        private String url;

        @Schema(description = "Last time this feed URL refresh was attempted.")
        private Instant lastFetchedAt;

        @Schema(description = "Last time this feed URL refresh completed successfully.")
        private Instant lastSuccessAt;

        @Schema(description = "Last refresh failure message for this feed URL.")
        private String lastError;

        @Schema(description = "Number of consecutive refresh failures for this feed URL.")
        private Integer failureCount;

        @Schema(description = "ETag returned by this feed server for conditional requests.")
        private String etag;

        @Schema(description = "Last-Modified value returned by this feed server.")
        private String lastModified;

        @Schema(description = "Latest feed item publication time observed for this feed URL.")
        private Instant latestPublishedAt;

        @Schema(description = "Number of cached feed items for this feed URL.")
        private Long itemCount;
    }
}
