package run.halo.links;

import static run.halo.app.extension.index.IndexAttributeFactory.simpleAttribute;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import run.halo.app.extension.Scheme;
import run.halo.app.extension.SchemeManager;
import run.halo.app.extension.index.IndexSpec;
import run.halo.app.plugin.BasePlugin;
import run.halo.app.plugin.PluginContext;

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
            indexSpecs.add(new IndexSpec()
                .setName("spec.displayName")
                .setIndexFunc(simpleAttribute(Link.class, link -> link.getSpec().getDisplayName()))
            );
            indexSpecs.add(new IndexSpec()
                .setName("spec.description")
                .setIndexFunc(simpleAttribute(Link.class, link -> link.getSpec().getDescription()))
            );
            indexSpecs.add(new IndexSpec()
                .setName("spec.url")
                .setIndexFunc(simpleAttribute(Link.class, link -> link.getSpec().getUrl()))
            );
            indexSpecs.add(new IndexSpec()
                .setName("spec.groupName")
                .setIndexFunc(simpleAttribute(Link.class, link -> {
                    var group = link.getSpec().getGroupName();
                    return StringUtils.isBlank(group) ? null : group;
                }))
            );
            indexSpecs.add(new IndexSpec()
                .setName("spec.priority")
                .setIndexFunc(simpleAttribute(Link.class, link -> String.valueOf(link.getSpec().getPriority())))
            );
            indexSpecs.add(new IndexSpec()
                .setName("spec.hidden")
                .setIndexFunc(simpleAttribute(Link.class, link -> String.valueOf(link.getSpec().getHidden())))
            );
        });
        schemeManager.register(LinkGroup.class, indexSpecs -> {
            indexSpecs.add(new IndexSpec()
                .setName("spec.priority")
                .setIndexFunc(simpleAttribute(LinkGroup.class, group -> String.valueOf(group.getSpec().getPriority())))
            );
            indexSpecs.add(new IndexSpec()
                .setName("spec.hidden")
                .setIndexFunc(simpleAttribute(LinkGroup.class, group -> String.valueOf(group.getSpec().getHidden())))
            );
        });
    }

    @Override
    public void stop() {
        schemeManager.unregister(Scheme.buildFromType(Link.class));
        schemeManager.unregister(Scheme.buildFromType(LinkGroup.class));
    }
}
