package run.halo.links.notification;

import static run.halo.app.extension.index.query.Queries.equal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.User;
import run.halo.app.core.extension.notification.Subscription;
import run.halo.app.extension.ExtensionUtil;
import run.halo.app.extension.GroupVersionKind;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.notification.NotificationCenter;
import run.halo.links.endpoint.LinkApplicationSettingsFetcher;
import run.halo.links.extension.LinkApplication;
import run.halo.links.service.LinkApplicationCreationCoordinator;

@Slf4j
@Component
@RequiredArgsConstructor
public class LinkApplicationNotificationSubscriptionManager {

    static final String REASON_TYPE = "plugin-links-new-link-application";

    private static final String COORDINATION_KEY =
        LinkApplicationNotificationSubscriptionManager.class.getName();

    private static final GroupVersionKind APPLICATION_GVK =
        GroupVersionKind.fromExtension(LinkApplication.class);

    private final ReactiveExtensionClient client;

    private final NotificationCenter notificationCenter;

    private final LinkApplicationSettingsFetcher settingsFetcher;

    private final LinkApplicationCreationCoordinator coordinator;

    public Mono<Boolean> reconcile() {
        return Mono.defer(() -> settingsFetcher.fetch()
            .flatMap(settings -> {
                boolean enabled = settings.notificationEnabled();
                var recipients = enabled ? settings.notificationRecipients() : List.<String>of();
                return coordinator.coordinate(COORDINATION_KEY,
                    () -> reconcileRecipients(recipients).thenReturn(enabled));
            }));
    }

    public Mono<Void> cleanup() {
        return coordinator.coordinate(COORDINATION_KEY,
            () -> reconcileRecipients(List.of()));
    }

    private Mono<Void> reconcileRecipients(List<String> recipients) {
        var current = listManagedSubscriptions();
        var validRecipients = resolveExistingUsers(recipients);
        return Mono.zip(current, validRecipients)
            .flatMap(existingAndDesired -> applyDiff(
                existingAndDesired.getT1(), existingAndDesired.getT2()));
    }

    private Mono<List<Subscription>> listManagedSubscriptions() {
        var options = ListOptions.builder()
            .andQuery(equal("spec.reason.reasonType", REASON_TYPE))
            .build();
        return client.listAll(Subscription.class, options, Sort.unsorted())
            .filter(Predicate.not(ExtensionUtil::isDeleted))
            .filter(LinkApplicationNotificationSubscriptionManager::isManaged)
            .collectList();
    }

    private Mono<LinkedHashSet<String>> resolveExistingUsers(List<String> recipients) {
        return Flux.fromIterable(recipients)
            .concatMap(username -> client.fetch(User.class, username)
                .filter(Predicate.not(ExtensionUtil::isDeleted))
                .map(ignored -> username)
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("[plugin-links] Notification recipient user [{}] no longer exists",
                        username);
                    return Mono.empty();
                })))
            .collect(LinkedHashSet::new, LinkedHashSet::add);
    }

    private Mono<Void> applyDiff(List<Subscription> existing,
        LinkedHashSet<String> desiredRecipients) {
        var subscriptionsByUser = new LinkedHashMap<String, List<Subscription>>();
        for (var subscription : existing) {
            String username = subscription.getSpec().getSubscriber().getName();
            subscriptionsByUser.computeIfAbsent(username, ignored -> new ArrayList<>())
                .add(subscription);
        }

        var removals = subscriptionsByUser.keySet().stream()
            .filter(username -> !desiredRecipients.contains(username))
            .toList();
        var additions = desiredRecipients.stream()
            .filter(username -> !hasOneEnabledSubscription(subscriptionsByUser.get(username)))
            .toList();

        return Flux.concat(
                Flux.fromIterable(removals).concatMap(this::unsubscribe),
                Flux.fromIterable(additions).concatMap(this::subscribe)
            )
            .then();
    }

    private Mono<Void> unsubscribe(String username) {
        return notificationCenter.unsubscribe(subscriber(username), interestReason());
    }

    private Mono<Subscription> subscribe(String username) {
        return notificationCenter.subscribe(subscriber(username), interestReason());
    }

    private static boolean hasOneEnabledSubscription(List<Subscription> subscriptions) {
        return subscriptions != null
            && subscriptions.size() == 1
            && !subscriptions.getFirst().getSpec().isDisabled();
    }

    private static boolean isManaged(Subscription subscription) {
        if (subscription == null || subscription.getSpec() == null
            || subscription.getSpec().getSubscriber() == null
            || StringUtils.isBlank(subscription.getSpec().getSubscriber().getName())) {
            return false;
        }
        var reason = subscription.getSpec().getReason();
        if (reason == null || !Objects.equals(REASON_TYPE, reason.getReasonType())
            || StringUtils.isNotBlank(reason.getExpression())) {
            return false;
        }
        var subject = reason.getSubject();
        return subject != null
            && Objects.equals(APPLICATION_GVK.groupVersion().toString(), subject.getApiVersion())
            && Objects.equals(APPLICATION_GVK.kind(), subject.getKind())
            && StringUtils.isBlank(subject.getName());
    }

    static Subscription.InterestReason interestReason() {
        var reason = new Subscription.InterestReason();
        reason.setReasonType(REASON_TYPE);
        reason.setSubject(Subscription.ReasonSubject.builder()
            .apiVersion(APPLICATION_GVK.groupVersion().toString())
            .kind(APPLICATION_GVK.kind())
            .build());
        return reason;
    }

    private static Subscription.Subscriber subscriber(String username) {
        var subscriber = new Subscription.Subscriber();
        subscriber.setName(username);
        return subscriber;
    }
}
