package run.halo.links.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.With;
import run.halo.app.extension.MetadataOperator;
import run.halo.app.theme.finders.vo.ExtensionVoOperator;
import run.halo.links.extension.LinkGroup;

/**
 * @author guqing
 * @since 2.0.0
 */
@Value
@Builder
@Schema(description = "Public view of a link group returned by theme-facing APIs and finder APIs.")
public class LinkGroupVo implements ExtensionVoOperator {

    @Schema(description = "Extension metadata of the link group.")
    MetadataOperator metadata;

    @Schema(description = "Configurable fields of the link group.")
    LinkGroup.LinkGroupSpec spec;

    @With
    @Schema(description = "Links that belong to this group when the API response includes grouped links.")
    List<LinkVo> links;

    public static LinkGroupVo from(LinkGroup linkGroup) {
        return LinkGroupVo.builder()
            .metadata(linkGroup.getMetadata())
            .spec(linkGroup.getSpec())
            .links(List.of())
            .build();
    }
}
