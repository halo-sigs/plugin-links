package run.halo.links.extension;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
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
    }
}
