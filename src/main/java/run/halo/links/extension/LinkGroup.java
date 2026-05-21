package run.halo.links.extension;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.LinkedHashSet;
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
@Schema(description = "Link group extension that groups links for display and sorting.")
@GVK(group = "core.halo.run", version = "v1alpha1", kind = "LinkGroup", plural = "linkgroups", singular = "linkgroup")
public class LinkGroup extends AbstractExtension {

    @Schema(description = "Desired state of the link group.", requiredMode = REQUIRED)
    private LinkGroupSpec spec;

    @Data
    @Schema(description = "Configurable fields of a link group.")
    public static class LinkGroupSpec {
        @Schema(description = "Human-readable name displayed for the link group.", requiredMode = REQUIRED)
        private String displayName;

        @Schema(description = "Sort order of the link group; lower values appear earlier.")
        private Integer priority;

        @Deprecated(since = "1.2.0", forRemoval = true)
        @Schema(description = "Deprecated names of links below this group; use Link.spec.groupName instead.")
        @ArraySchema(arraySchema = @Schema(description = "Deprecated links of this group."),
            schema = @Schema(description = "Metadata name of a Link."))
        private LinkedHashSet<String> links;
    }
}
