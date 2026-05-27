package run.halo.links.vo;

import lombok.Data;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import run.halo.app.extension.MetadataOperator;
import run.halo.app.theme.finders.vo.ExtensionVoOperator;
import run.halo.links.extension.Link;

import java.util.List;


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
            .spec(link.getSpec())
            .status(link.getStatus())
            .build();
    }
}
