package run.halo.links.notification;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import run.halo.app.plugin.PluginConfigUpdatedEvent;

@ExtendWith(MockitoExtension.class)
class LinkApplicationNotificationConfigListenerTest {

    @Mock
    LinkApplicationNotificationSubscriptionManager subscriptionManager;

    @Mock
    PluginConfigUpdatedEvent event;

    @Test
    void shouldReconcileWhenPluginSettingsChange() {
        when(subscriptionManager.reconcile()).thenReturn(Mono.just(true));
        var listener = new LinkApplicationNotificationConfigListener(subscriptionManager);

        listener.onConfigUpdated(event);

        verify(subscriptionManager).reconcile();
    }

    @Test
    void shouldIsolateReconciliationFailureFromSettingsUpdate() {
        when(subscriptionManager.reconcile())
            .thenReturn(Mono.error(new IllegalStateException("failed")));
        var listener = new LinkApplicationNotificationConfigListener(subscriptionManager);

        assertThatCode(() -> listener.onConfigUpdated(event)).doesNotThrowAnyException();
    }
}
