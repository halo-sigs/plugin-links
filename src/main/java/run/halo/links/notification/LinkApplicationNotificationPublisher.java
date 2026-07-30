package run.halo.links.notification;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.notification.Reason;
import run.halo.app.infra.ExternalLinkProcessor;
import run.halo.app.notification.NotificationReasonEmitter;
import run.halo.app.notification.UserIdentity;
import run.halo.links.extension.LinkApplication;

@Component
@RequiredArgsConstructor
public class LinkApplicationNotificationPublisher {

    private static final String MANAGE_PATH = "/console/links";

    private final LinkApplicationNotificationSubscriptionManager subscriptionManager;

    private final NotificationReasonEmitter reasonEmitter;

    private final ExternalLinkProcessor externalLinkProcessor;

    public Mono<Void> publish(LinkApplication application) {
        return subscriptionManager.reconcile()
            .flatMap(enabled -> {
                if (!enabled) {
                    return Mono.empty();
                }
                String manageUrl = externalLinkProcessor.processLink(MANAGE_PATH);
                var subject = Reason.Subject.builder()
                    .apiVersion(application.getApiVersion())
                    .kind(application.getKind())
                    .name(application.getMetadata().getName())
                    .title(application.getSpec().getDisplayName())
                    .url(manageUrl)
                    .build();
                var attributes = Map.<String, Object>of(
                    "displayName", application.getSpec().getDisplayName(),
                    "websiteUrl", application.getSpec().getUrl(),
                    "originLabel", originLabel(application.getSpec().getOrigin().getType()),
                    "manageUrl", manageUrl
                );
                return reasonEmitter.emit(
                    LinkApplicationNotificationSubscriptionManager.REASON_TYPE,
                    builder -> builder
                        .author(UserIdentity.of("system"))
                        .subject(subject)
                        .attributes(attributes)
                );
            });
    }

    private static String originLabel(LinkApplication.OriginType originType) {
        return switch (originType) {
            case FORM -> "访客自助申请";
            case COMMENT -> "评论识别";
        };
    }
}
