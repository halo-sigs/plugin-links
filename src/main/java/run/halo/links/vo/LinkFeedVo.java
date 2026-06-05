package run.halo.links.vo;

import lombok.Data;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import run.halo.app.extension.MetadataOperator;
import run.halo.app.theme.finders.vo.ExtensionVoOperator;
import run.halo.links.extension.Link;

import java.util.List;
import java.util.Optional;


@Data
@SuperBuilder
@ToString
public class LinkFeedVo implements ExtensionVoOperator {

    MetadataOperator metadata;

    Link.LinkSpec spec;

    Link.LinkStatus status;

    List<LinkFeedItemVo> feeds;

    public static LinkFeedVo from(Link link) {
        return LinkFeedVo.builder()
            .metadata(link.getMetadata())
            .spec(publicSpec(link.getSpec()))
            .status(publicStatus(link.getStatus()))
            .build();
    }

    private static Link.LinkSpec publicSpec(Link.LinkSpec spec) {
        if (spec == null) {
            return null;
        }
        Link.LinkSpec publicSpec = new Link.LinkSpec();
        publicSpec.setUrl(spec.getUrl());
        publicSpec.setDisplayName(spec.getDisplayName());
        publicSpec.setLogo(spec.getLogo());
        publicSpec.setDescription(spec.getDescription());
        publicSpec.setPriority(spec.getPriority());
        publicSpec.setGroupName(spec.getGroupName());
        publicSpec.setRss(publicRssSpec(spec.getRss()));
        publicSpec.setVerification(spec.getVerification());
        return publicSpec;
    }

    private static Link.RssSpec publicRssSpec(Link.RssSpec rss) {
        if (rss == null) {
            return null;
        }
        Link.RssSpec publicRss = new Link.RssSpec();
        publicRss.setEnabled(rss.getEnabled());
        return publicRss;
    }

    private static Link.LinkStatus publicStatus(Link.LinkStatus status) {
        if (status == null) {
            return null;
        }
        Link.LinkStatus publicStatus = new Link.LinkStatus();
        publicStatus.setRss(publicRssStatus(status.getRss()));
        publicStatus.setVerification(status.getVerification());
        return publicStatus;
    }

    private static Link.RssStatus publicRssStatus(Link.RssStatus rss) {
        if (rss == null) {
            return null;
        }
        Link.RssStatus publicRss = new Link.RssStatus();
        publicRss.setLastFetchedAt(rss.getLastFetchedAt());
        publicRss.setLastSuccessAt(rss.getLastSuccessAt());
        publicRss.setLastError(rss.getLastError());
        publicRss.setFailureCount(rss.getFailureCount());
        publicRss.setLatestPublishedAt(rss.getLatestPublishedAt());
        publicRss.setItemCount(rss.getItemCount());
        publicRss.setFeeds(Optional.ofNullable(rss.getFeeds())
            .map(feeds -> feeds.stream()
                .map(LinkFeedVo::publicRssFeedStatus)
                .toList())
            .orElse(null));
        return publicRss;
    }

    private static Link.RssFeedStatus publicRssFeedStatus(Link.RssFeedStatus feedStatus) {
        Link.RssFeedStatus publicFeedStatus = new Link.RssFeedStatus();
        publicFeedStatus.setLastFetchedAt(feedStatus.getLastFetchedAt());
        publicFeedStatus.setLastSuccessAt(feedStatus.getLastSuccessAt());
        publicFeedStatus.setLastError(feedStatus.getLastError());
        publicFeedStatus.setFailureCount(feedStatus.getFailureCount());
        publicFeedStatus.setEtag(feedStatus.getEtag());
        publicFeedStatus.setLastModified(feedStatus.getLastModified());
        publicFeedStatus.setValidatorUpdatedAt(feedStatus.getValidatorUpdatedAt());
        publicFeedStatus.setLatestPublishedAt(feedStatus.getLatestPublishedAt());
        publicFeedStatus.setItemCount(feedStatus.getItemCount());
        return publicFeedStatus;
    }
}
