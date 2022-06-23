package run.halo.links;

import org.pf4j.PluginWrapper;
import run.halo.app.extension.SchemeManager;
import run.halo.app.plugin.BasePlugin;

/**
 * @author guqing
 * @since 2.0.0
 */
public class LinkPlugin extends BasePlugin {

    private final SchemeManager schemeManager;

    public LinkPlugin(PluginWrapper wrapper) {
        super(wrapper);
        schemeManager = getApplicationContext().getBean(SchemeManager.class);
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
