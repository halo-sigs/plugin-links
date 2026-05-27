package run.halo.links.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;
import run.halo.app.extension.MetadataOperator;
import run.halo.app.theme.finders.vo.ExtensionVoOperator;
import run.halo.links.extension.Link;

/**
 * @author guqing
 * @since 2.0.0
 */
@Value
@Builder
@Schema(description = "Public view of a link returned by theme-facing APIs and finder APIs.")
public class LinkVo implements ExtensionVoOperator {

    @Schema(description = "Extension metadata of the link.")
    MetadataOperator metadata;

    @Schema(description = "Configurable fields of the link.")
    Link.LinkSpec spec;

    @Schema(description = "Observed state of the link.")
    Link.LinkStatus status;

    public static LinkVo from(Link link) {
        return LinkVo.builder()
            .metadata(link.getMetadata())
            .spec(link.getSpec())
            .status(link.getStatus())
            .build();
    }
}
