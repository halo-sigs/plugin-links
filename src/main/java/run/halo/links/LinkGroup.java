package run.halo.links;

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
    kind = "LinkGroup", plural = "linkgroups", singular = "linkgroup")
public class LinkGroup extends AbstractExtension {

    private Integer priority;
}
