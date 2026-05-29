package run.halo.links.vo;

import lombok.Builder;
import lombok.Value;
import lombok.With;
import run.halo.app.extension.MetadataOperator;
import run.halo.app.theme.finders.vo.ExtensionVoOperator;
import run.halo.links.extension.LinkGroup;

import java.util.List;


@Value
@Builder
public class LinkFeedGroupVo implements ExtensionVoOperator {

    MetadataOperator metadata;

    LinkGroup.LinkGroupSpec spec;

    @With
    List<LinkFeedVo> links;

    public static LinkFeedGroupVo from(LinkGroup linkGroup) {
        return LinkFeedGroupVo.builder()
            .metadata(linkGroup.getMetadata())
            .spec(linkGroup.getSpec())
            .links(List.of())
            .build();
    }
}
