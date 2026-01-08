package run.halo.links;

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
@GVK(group = "core.halo.run", version = "v1alpha1", kind = "LinkGroup",
    plural = "linkgroups", singular = "linkgroup")
public class LinkGroup extends AbstractExtension {

    private LinkGroupSpec spec;

    @Data
    public static class LinkGroupSpec {
        @Schema(required = true)
        private String displayName;

        private Integer priority;

        @Deprecated(since = "1.2.0", forRemoval = true)
        @Schema(description = "Names of links below this group.")
        @ArraySchema(arraySchema = @Schema(description = "Links of this group."),
            schema = @Schema(description = "Name of link."))
        private LinkedHashSet<String> links;
    }
}
