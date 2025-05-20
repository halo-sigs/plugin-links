package run.halo.links.vo;

import lombok.Builder;
import lombok.Value;
import run.halo.app.extension.MetadataOperator;
import run.halo.app.theme.finders.vo.ExtensionVoOperator;
import run.halo.links.Link;

/**
 * @author guqing
 * @since 2.0.0
 */
@Value
@Builder
public class LinkVo implements ExtensionVoOperator {

    MetadataOperator metadata;

    Link.LinkSpec spec;

    public static LinkVo from(Link link) {
        return LinkVo.builder()
            .metadata(link.getMetadata())
            .spec(link.getSpec())
            .build();
    }
}
