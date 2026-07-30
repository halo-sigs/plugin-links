package run.halo.links;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import run.halo.app.extension.SchemeManager;
import run.halo.app.plugin.PluginContext;
import run.halo.links.notification.LinkApplicationNotificationSubscriptionManager;

@ExtendWith(MockitoExtension.class)
class LinkPluginNotificationLifecycleTest {

    @Mock
    PluginContext pluginContext;

    @Mock
    SchemeManager schemeManager;

    @Mock
    LinkApplicationNotificationSubscriptionManager subscriptionManager;

    @Test
    void shouldReconcileOnStartAndCleanupOnStop() {
        when(subscriptionManager.reconcile()).thenReturn(Mono.just(true));
        when(subscriptionManager.cleanup()).thenReturn(Mono.empty());
        var plugin = new LinkPlugin(pluginContext, schemeManager, subscriptionManager);

        plugin.start();
        plugin.stop();

        verify(subscriptionManager).reconcile();
        verify(subscriptionManager).cleanup();
    }

    @Test
    void shouldKeepLifecycleAvailableWhenNotificationCleanupFails() {
        when(subscriptionManager.reconcile())
            .thenReturn(Mono.error(new IllegalStateException("start failed")));
        when(subscriptionManager.cleanup())
            .thenReturn(Mono.error(new IllegalStateException("stop failed")));
        var plugin = new LinkPlugin(pluginContext, schemeManager, subscriptionManager);

        assertThatCode(plugin::start).doesNotThrowAnyException();
        assertThatCode(plugin::stop).doesNotThrowAnyException();
    }

    @Test
    void shouldBoundNotificationCleanupWaitOnStop() {
        when(subscriptionManager.cleanup()).thenReturn(Mono.never());
        var plugin = new LinkPlugin(pluginContext, schemeManager, subscriptionManager,
            Duration.ofMillis(10));

        assertTimeoutPreemptively(Duration.ofSeconds(1), plugin::stop);
    }
}
