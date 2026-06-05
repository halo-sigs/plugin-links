package run.halo.links.vo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import run.halo.app.extension.Metadata;
import run.halo.links.extension.Link;

class LinkFeedVoTest {

    @Test
    void shouldHideRssFeedUrlsFromPublicFeedVo() {
        Link link = new Link();
        Metadata metadata = new Metadata();
        metadata.setName("link-a");
        link.setMetadata(metadata);
        Link.LinkSpec spec = new Link.LinkSpec();
        spec.setUrl("https://example.com");
        Link.RssSpec rss = new Link.RssSpec();
        rss.setEnabled(true);
        rss.setFeedUrls(List.of("https://example.com/private.xml?token=secret"));
        spec.setRss(rss);
        link.setSpec(spec);
        Link.LinkStatus status = new Link.LinkStatus();
        Link.RssStatus rssStatus = new Link.RssStatus();
        rssStatus.setLastFetchedAt(Instant.parse("2026-05-20T10:00:00Z"));
        Link.RssFeedStatus feedStatus = new Link.RssFeedStatus();
        feedStatus.setUrl("https://example.com/private.xml?token=secret");
        rssStatus.setFeeds(List.of(feedStatus));
        status.setRss(rssStatus);
        link.setStatus(status);

        LinkFeedVo vo = LinkFeedVo.from(link);

        assertThat(vo.getSpec().getUrl()).isEqualTo("https://example.com");
        assertThat(vo.getSpec().getRss().getEnabled()).isTrue();
        assertThat(vo.getSpec().getRss().getFeedUrls()).isNull();
        assertThat(vo.getStatus().getRss().getLastFetchedAt())
            .isEqualTo(Instant.parse("2026-05-20T10:00:00Z"));
        assertThat(vo.getStatus().getRss().getFeeds())
            .singleElement()
            .satisfies(publicFeedStatus -> assertThat(publicFeedStatus.getUrl()).isNull());
    }
}
