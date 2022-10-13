package run.halo.links;

import org.pf4j.PluginWrapper;
import org.springframework.stereotype.Component;
import run.halo.app.extension.SchemeManager;
import run.halo.app.plugin.BasePlugin;

/**
 * @author guqing
 * @since 2.0.0
 */
@Component
public class LinkPlugin extends BasePlugin {

    private final SchemeManager schemeManager;

    public LinkPlugin(PluginWrapper wrapper, SchemeManager schemeManager) {
        super(wrapper);
        this.schemeManager = schemeManager;
    }

    @Override
    public void start() {
        schemeManager.register(Link.class);
        schemeManager.register(LinkGroup.class);
    }

    @Override
    public void stop() {
        schemeManager.unregister(schemeManager.get(Link.class));
        schemeManager.unregister(schemeManager.get(LinkGroup.class));
    }
}
