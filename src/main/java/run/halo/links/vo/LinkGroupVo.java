package run.halo.links.vo;

import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.With;
import run.halo.app.extension.MetadataOperator;
import run.halo.links.LinkGroup;

/**
 * @author guqing
 * @since 2.0.0
 */
@Value
@Builder
public class LinkGroupVo {

    MetadataOperator metadata;

    LinkGroup.LinkGroupSpec spec;

    @With
    List<LinkVo> links;

    public static LinkGroupVo from(LinkGroup linkGroup) {
        return LinkGroupVo.builder()
            .metadata(linkGroup.getMetadata())
            .spec(linkGroup.getSpec())
            .links(List.of())
            .build();
    }
}
