package run.halo.links;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;

/**
 * @author guqing
 * @since 2.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@GVK(group = "core.halo.run", version = "v1alpha1",
        kind = "Link", plural = "links", singular = "link")
public class Link extends AbstractExtension {

    private LinkSpec spec;

    @Data
    public static class LinkSpec {
        @Schema(required = true)
        private String url;

        @Schema(required = true)
        private String displayName;

        private String logo;

        private String description;

        private String groupName;

        private Integer priority;
    }
}
