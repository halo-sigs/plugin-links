package run.halo.links.notification;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import run.halo.app.plugin.PluginConfigUpdatedEvent;

@Slf4j
@Component
@RequiredArgsConstructor
public class LinkApplicationNotificationConfigListener {

    private static final Duration RECONCILIATION_TIMEOUT = Duration.ofSeconds(10);

    private final LinkApplicationNotificationSubscriptionManager subscriptionManager;

    @Async
    @EventListener(PluginConfigUpdatedEvent.class)
    public void onConfigUpdated(PluginConfigUpdatedEvent event) {
        try {
            subscriptionManager.reconcile().block(RECONCILIATION_TIMEOUT);
        } catch (RuntimeException error) {
            log.warn("[plugin-links] Failed to reconcile notification subscriptions after "
                + "settings update", error);
        }
    }
}
