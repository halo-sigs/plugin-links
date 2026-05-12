package run.halo.links;

import run.halo.app.extension.index.IndexSpecs;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import run.halo.app.extension.Scheme;
import run.halo.app.extension.SchemeManager;
import run.halo.app.plugin.BasePlugin;
import run.halo.app.plugin.PluginContext;
import run.halo.links.extension.Link;
import run.halo.links.extension.LinkGroup;

/**
 * @author guqing
 * @since 2.0.0
 */
@Component
public class LinkPlugin extends BasePlugin {

    private final SchemeManager schemeManager;

    public LinkPlugin(PluginContext pluginContext, SchemeManager schemeManager) {
        super(pluginContext);
        this.schemeManager = schemeManager;
    }

    @Override
    public void start() {
        schemeManager.register(Link.class, indexSpecs -> {
            indexSpecs.add(IndexSpecs.<Link, String>single("spec.displayName", String.class)
                .indexFunc(link -> link.getSpec().getDisplayName())
            );
            indexSpecs.add(IndexSpecs.<Link, String>single("spec.description", String.class)
                .indexFunc(link -> link.getSpec().getDescription())
            );
            indexSpecs.add(IndexSpecs.<Link, String>single("spec.url", String.class)
                .indexFunc(link -> link.getSpec().getUrl())
            );
            indexSpecs.add(IndexSpecs.<Link, String>single("spec.groupName", String.class)
                .indexFunc(link -> {
                    var group = link.getSpec().getGroupName();
                    return StringUtils.isBlank(group) ? null : group;
                })
            );
            indexSpecs.add(IndexSpecs.<Link, Integer>single("spec.priority", Integer.class)
                .indexFunc(link -> link.getSpec().getPriority())
            );
        });
        schemeManager.register(LinkGroup.class, indexSpecs -> {
            indexSpecs.add(IndexSpecs.<LinkGroup, Integer>single("spec.priority", Integer.class)
                .indexFunc(group -> group.getSpec().getPriority())
            );
        });
    }

    @Override
    public void stop() {
        schemeManager.unregister(Scheme.buildFromType(Link.class));
        schemeManager.unregister(Scheme.buildFromType(LinkGroup.class));
    }
}
