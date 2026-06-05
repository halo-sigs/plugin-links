package run.halo.links.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import run.halo.links.rss.LinkFeedItem;
import java.time.Instant;

@Data
@SuperBuilder
@ToString
@Schema(description = "Public view of a link feed returned by theme-facing APIs and finder APIs.")
public class LinkFeedItemVo {

    String id;
    String linkName;
    String url;
    String title;
    String summary;
    String author;
    String authorUrl;
    String authorLogo;
    Instant publishedAt;
    Instant fetchedAt;
    Instant updatedAt;

    public static LinkFeedItemVo from(LinkFeedItem linkFeedItem) {
        return LinkFeedItemVo.builder()
            .id(linkFeedItem.getId())
            .linkName(linkFeedItem.getLinkName())
            .url(linkFeedItem.getUrl())
            .title(linkFeedItem.getTitle())
            .summary(linkFeedItem.getSummary())
            .author(linkFeedItem.getAuthor())
            .publishedAt(linkFeedItem.getPublishedAt())
            .fetchedAt(linkFeedItem.getFetchedAt())
            .updatedAt(linkFeedItem.getUpdatedAt())
            .build();
    }
}
