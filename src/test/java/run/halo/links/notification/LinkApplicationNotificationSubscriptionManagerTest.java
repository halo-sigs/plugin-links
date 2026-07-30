package run.halo.links.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.app.core.extension.User;
import run.halo.app.core.extension.notification.Subscription;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.notification.NotificationCenter;
import run.halo.links.dto.LinkApplicationSettings;
import run.halo.links.endpoint.LinkApplicationSettingsFetcher;
import run.halo.links.service.LinkApplicationCreationCoordinator;

@ExtendWith(MockitoExtension.class)
class LinkApplicationNotificationSubscriptionManagerTest {

    @Mock
    ReactiveExtensionClient client;

    @Mock
    NotificationCenter notificationCenter;

    @Mock
    LinkApplicationSettingsFetcher settingsFetcher;

    LinkApplicationNotificationSubscriptionManager manager;

    @BeforeEach
    void setUp() {
        manager = new LinkApplicationNotificationSubscriptionManager(client, notificationCenter,
            settingsFetcher, new LinkApplicationCreationCoordinator());
    }

    @Test
    void shouldReconcileAddedRemovedAndDuplicateSubscriptions() {
        when(settingsFetcher.fetch()).thenReturn(Mono.just(settings(true,
            List.of("admin", "reviewer"))));
        when(client.listAll(eq(Subscription.class), any(ListOptions.class), any(Sort.class)))
            .thenReturn(Flux.just(
                subscription("admin"),
                subscription("admin"),
                subscription("removed")
            ));
        givenUser("admin", true);
        givenUser("reviewer", true);
        when(notificationCenter.unsubscribe(any(), any())).thenReturn(Mono.empty());
        when(notificationCenter.subscribe(any(), any()))
            .thenAnswer(invocation -> Mono.just(subscription(
                invocation.<Subscription.Subscriber>getArgument(0).getName())));

        StepVerifier.create(manager.reconcile())
            .expectNext(true)
            .verifyComplete();

        var removedSubscriber = ArgumentCaptor.forClass(Subscription.Subscriber.class);
        verify(notificationCenter).unsubscribe(removedSubscriber.capture(), any());
        assertThat(removedSubscriber.getValue().getName()).isEqualTo("removed");
        var addedSubscribers = ArgumentCaptor.forClass(Subscription.Subscriber.class);
        var reasons = ArgumentCaptor.forClass(Subscription.InterestReason.class);
        verify(notificationCenter, times(2))
            .subscribe(addedSubscribers.capture(), reasons.capture());
        assertThat(addedSubscribers.getAllValues())
            .extracting(Subscription.Subscriber::getName)
            .containsExactlyInAnyOrder("admin", "reviewer");
        assertThat(reasons.getAllValues()).allSatisfy(reason -> {
            assertThat(reason.getReasonType())
                .isEqualTo("plugin-links-new-link-application");
            assertThat(reason.getExpression()).isNull();
            assertThat(reason.getSubject().getApiVersion())
                .isEqualTo("core.halo.run/v1alpha1");
            assertThat(reason.getSubject().getKind()).isEqualTo("LinkApplication");
            assertThat(reason.getSubject().getName()).isNull();
        });
    }

    @Test
    void shouldSkipMissingUsersAndRemoveTheirStaleSubscription() {
        when(settingsFetcher.fetch()).thenReturn(Mono.just(settings(true,
            List.of("missing", "reviewer"))));
        when(client.listAll(eq(Subscription.class), any(ListOptions.class), any(Sort.class)))
            .thenReturn(Flux.just(subscription("missing")));
        givenUser("missing", false);
        givenUser("reviewer", true);
        when(notificationCenter.unsubscribe(any(), any())).thenReturn(Mono.empty());
        when(notificationCenter.subscribe(any(), any()))
            .thenReturn(Mono.just(subscription("reviewer")));

        StepVerifier.create(manager.reconcile())
            .expectNext(true)
            .verifyComplete();

        var removed = ArgumentCaptor.forClass(Subscription.Subscriber.class);
        verify(notificationCenter).unsubscribe(removed.capture(), any());
        assertThat(removed.getValue().getName()).isEqualTo("missing");
        var added = ArgumentCaptor.forClass(Subscription.Subscriber.class);
        verify(notificationCenter).subscribe(added.capture(), any());
        assertThat(added.getValue().getName()).isEqualTo("reviewer");
    }

    @Test
    void shouldCleanSubscriptionsWhenDisabledAndRemainIdempotentWhenAligned() {
        when(settingsFetcher.fetch()).thenReturn(
            Mono.just(settings(false, List.of("admin"))),
            Mono.just(settings(true, List.of("admin")))
        );
        when(client.listAll(eq(Subscription.class), any(ListOptions.class), any(Sort.class)))
            .thenReturn(Flux.just(subscription("admin")));
        givenUser("admin", true);
        when(notificationCenter.unsubscribe(any(), any())).thenReturn(Mono.empty());

        StepVerifier.create(manager.reconcile())
            .expectNext(false)
            .verifyComplete();
        StepVerifier.create(manager.reconcile())
            .expectNext(true)
            .verifyComplete();

        verify(notificationCenter, times(1)).unsubscribe(any(), any());
        verify(notificationCenter, never()).subscribe(any(), any());
    }

    @Test
    void shouldSerializeConcurrentReconciliation() {
        when(settingsFetcher.fetch()).thenReturn(Mono.just(settings(true, List.of("admin"))));
        when(client.listAll(eq(Subscription.class), any(ListOptions.class), any(Sort.class)))
            .thenReturn(Flux.empty());
        givenUser("admin", true);
        var active = new AtomicInteger();
        var maximumActive = new AtomicInteger();
        when(notificationCenter.subscribe(any(), any())).thenAnswer(ignored -> Mono.defer(() -> {
            int current = active.incrementAndGet();
            maximumActive.accumulateAndGet(current, Math::max);
            return Mono.delay(Duration.ofMillis(25))
                .then(Mono.fromCallable(() -> {
                    active.decrementAndGet();
                    return subscription("admin");
                }));
        }));

        StepVerifier.create(Flux.merge(manager.reconcile(), manager.reconcile()).collectList())
            .assertNext(results -> assertThat(results).containsExactlyInAnyOrder(true, true))
            .verifyComplete();

        assertThat(maximumActive).hasValue(1);
    }

    @Test
    void shouldCleanupOnlyManagedSubscriptions() {
        when(client.listAll(eq(Subscription.class), any(ListOptions.class), any(Sort.class)))
            .thenReturn(Flux.just(subscription("admin")));
        when(notificationCenter.unsubscribe(any(), any())).thenReturn(Mono.empty());

        StepVerifier.create(manager.cleanup()).verifyComplete();

        verify(notificationCenter).unsubscribe(any(), any());
        verify(client, never()).delete(any());
    }

    private void givenUser(String username, boolean exists) {
        when(client.fetch(User.class, username))
            .thenReturn(exists ? Mono.just(user(username)) : Mono.empty());
    }

    private static LinkApplicationSettings settings(boolean notificationEnabled,
        List<String> recipients) {
        var settings = new LinkApplicationSettings();
        settings.setEnabled(true);
        var notification = new LinkApplicationSettings.Notification();
        notification.setEnabled(notificationEnabled);
        notification.setRecipients(recipients);
        settings.setNotification(notification);
        return settings.normalized();
    }

    private static User user(String username) {
        var user = new User();
        var metadata = new Metadata();
        metadata.setName(username);
        user.setMetadata(metadata);
        return user;
    }

    private static Subscription subscription(String username) {
        var subscription = new Subscription();
        subscription.setMetadata(new Metadata());
        var spec = new Subscription.Spec();
        var subscriber = new Subscription.Subscriber();
        subscriber.setName(username);
        spec.setSubscriber(subscriber);
        var reason = new Subscription.InterestReason();
        reason.setReasonType("plugin-links-new-link-application");
        reason.setSubject(Subscription.ReasonSubject.builder()
            .apiVersion("core.halo.run/v1alpha1")
            .kind("LinkApplication")
            .build());
        spec.setReason(reason);
        subscription.setSpec(spec);
        return subscription;
    }
}
