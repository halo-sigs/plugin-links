package run.halo.links;

import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.app.extension.Scheme;
import run.halo.app.extension.SchemeManager;
import run.halo.app.extension.index.IndexSpecs;
import run.halo.app.plugin.BasePlugin;
import run.halo.app.plugin.PluginContext;
import run.halo.links.extension.Link;
import run.halo.links.extension.LinkApplication;
import run.halo.links.extension.LinkGroup;
import run.halo.links.notification.LinkApplicationNotificationSubscriptionManager;

/**
 * @author guqing
 * @since 2.0.0
 */
@Component
@EnableScheduling
@Slf4j
public class LinkPlugin extends BasePlugin {

    private static final Duration NOTIFICATION_LIFECYCLE_TIMEOUT = Duration.ofSeconds(10);

    private final SchemeManager schemeManager;

    private final LinkApplicationNotificationSubscriptionManager notificationSubscriptionManager;

    private final Duration notificationLifecycleTimeout;

    @Autowired
    public LinkPlugin(PluginContext pluginContext, SchemeManager schemeManager,
        LinkApplicationNotificationSubscriptionManager notificationSubscriptionManager) {
        this(pluginContext, schemeManager, notificationSubscriptionManager,
            NOTIFICATION_LIFECYCLE_TIMEOUT);
    }

    LinkPlugin(PluginContext pluginContext, SchemeManager schemeManager,
        LinkApplicationNotificationSubscriptionManager notificationSubscriptionManager,
        Duration notificationLifecycleTimeout) {
        super(pluginContext);
        this.schemeManager = schemeManager;
        this.notificationSubscriptionManager = notificationSubscriptionManager;
        this.notificationLifecycleTimeout = notificationLifecycleTimeout;
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
            indexSpecs.add(IndexSpecs.<Link, Boolean>single("spec.rss.enabled", Boolean.class)
                .indexFunc(link -> {
                    var rss = link.getSpec().getRss();
                    return rss == null ? null : rss.getEnabled();
                })
            );
        });
        schemeManager.register(LinkGroup.class, indexSpecs -> {
            indexSpecs.add(IndexSpecs.<LinkGroup, Integer>single("spec.priority", Integer.class)
                .indexFunc(group -> group.getSpec().getPriority())
            );
        });
        schemeManager.register(LinkApplication.class, indexSpecs -> {
            indexSpecs.add(IndexSpecs.<LinkApplication, String>single("spec.url", String.class)
                .indexFunc(app -> app.getSpec().getUrl())
            );
            indexSpecs.add(IndexSpecs.<LinkApplication, String>single("spec.displayName", String.class)
                .indexFunc(app -> app.getSpec().getDisplayName())
            );
            indexSpecs.add(IndexSpecs.<LinkApplication, String>single("spec.status", String.class)
                .indexFunc(app -> app.getSpec().getStatus() != null
                    ? app.getSpec().getStatus().name() : null)
            );
            indexSpecs.add(IndexSpecs.<LinkApplication, String>single("spec.origin.type",
                    String.class)
                .indexFunc(app -> {
                    var origin = app.getSpec().getOrigin();
                    return origin != null && origin.getType() != null
                        ? origin.getType().name() : null;
                })
            );
            indexSpecs.add(IndexSpecs.<LinkApplication, String>single("spec.origin.comment.name",
                    String.class)
                .indexFunc(app -> {
                    var origin = app.getSpec().getOrigin();
                    return origin == null || origin.getComment() == null
                        ? null : origin.getComment().getName();
                })
            );
        });
        runNotificationLifecycle(
            notificationSubscriptionManager.reconcile().then(),
            "restore notification subscriptions"
        );
    }

    @Override
    public void stop() {
        runNotificationLifecycle(
            notificationSubscriptionManager.cleanup(),
            "clean up notification subscriptions"
        );
        schemeManager.unregister(Scheme.buildFromType(Link.class));
        schemeManager.unregister(Scheme.buildFromType(LinkGroup.class));
        schemeManager.unregister(Scheme.buildFromType(LinkApplication.class));
    }

    private void runNotificationLifecycle(Mono<Void> operation, String description) {
        try {
            operation.block(notificationLifecycleTimeout);
        } catch (RuntimeException error) {
            log.warn("[plugin-links] Failed to {}", description, error);
        }
    }
}
