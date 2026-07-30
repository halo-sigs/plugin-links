package run.halo.links.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;
import run.halo.app.core.extension.notification.Reason;
import run.halo.app.extension.Metadata;
import run.halo.app.infra.ExternalLinkProcessor;
import run.halo.app.notification.NotificationReasonEmitter;
import run.halo.app.notification.ReasonPayload;
import run.halo.links.extension.LinkApplication;

@ExtendWith(MockitoExtension.class)
class LinkApplicationNotificationPublisherTest {

    @Mock
    LinkApplicationNotificationSubscriptionManager subscriptionManager;

    @Mock
    NotificationReasonEmitter reasonEmitter;

    @Mock
    ExternalLinkProcessor externalLinkProcessor;

    LinkApplicationNotificationPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new LinkApplicationNotificationPublisher(subscriptionManager, reasonEmitter,
            externalLinkProcessor);
    }

    @Test
    void shouldNotEmitWhenNotificationsAreIneffective() {
        when(subscriptionManager.reconcile()).thenReturn(Mono.just(false));

        StepVerifier.create(publisher.publish(application(LinkApplication.OriginType.FORM)))
            .verifyComplete();

        verify(reasonEmitter, never()).emit(any(), any());
    }

    @Test
    void shouldReconcileBeforeEmittingFormApplicationReason() {
        var reconciliation = Sinks.<Boolean>one();
        when(subscriptionManager.reconcile()).thenReturn(reconciliation.asMono());
        when(externalLinkProcessor.processLink("/console/links"))
            .thenReturn("https://example.test/console/links");
        when(reasonEmitter.emit(any(), any())).thenReturn(Mono.empty());
        var application = application(LinkApplication.OriginType.FORM);

        StepVerifier.create(publisher.publish(application))
            .then(() -> verify(reasonEmitter, never()).emit(any(), any()))
            .then(() -> reconciliation.tryEmitValue(true))
            .verifyComplete();

        var payload = capturePayload();
        assertThat(payload.getAuthor().name()).isEqualTo("system");
        assertThat(payload.getSubject())
            .extracting(Reason.Subject::getApiVersion, Reason.Subject::getKind,
                Reason.Subject::getName, Reason.Subject::getTitle, Reason.Subject::getUrl)
            .containsExactly("core.halo.run/v1alpha1", "LinkApplication", "link-app-1",
                "Example Site", "https://example.test/console/links");
        assertThat(payload.getAttributes())
            .containsExactlyInAnyOrderEntriesOf(java.util.Map.of(
                "displayName", "Example Site",
                "websiteUrl", "https://friend.example",
                "originLabel", "访客自助申请",
                "manageUrl", "https://example.test/console/links"
            ));
    }

    @Test
    void shouldUseCommentOriginLabelAndEmitOnce() {
        when(subscriptionManager.reconcile()).thenReturn(Mono.just(true));
        when(externalLinkProcessor.processLink("/console/links"))
            .thenReturn("https://example.test/console/links");
        when(reasonEmitter.emit(any(), any())).thenReturn(Mono.empty());

        StepVerifier.create(publisher.publish(application(LinkApplication.OriginType.COMMENT)))
            .verifyComplete();

        var payload = capturePayload();
        assertThat(payload.getAttributes()).containsEntry("originLabel", "评论识别");
        verify(reasonEmitter).emit(eq("plugin-links-new-link-application"), any());
    }

    @Test
    void shouldPropagateReconciliationFailureToCaller() {
        when(subscriptionManager.reconcile())
            .thenReturn(Mono.error(new IllegalStateException("failed")));

        StepVerifier.create(publisher.publish(application(LinkApplication.OriginType.FORM)))
            .expectErrorMessage("failed")
            .verify();
    }

    private ReasonPayload capturePayload() {
        @SuppressWarnings("unchecked")
        var consumer = ArgumentCaptor.forClass(Consumer.class);
        verify(reasonEmitter).emit(eq("plugin-links-new-link-application"), consumer.capture());
        var builder = ReasonPayload.builder();
        consumer.getValue().accept(builder);
        return builder.build();
    }

    private static LinkApplication application(LinkApplication.OriginType originType) {
        var application = new LinkApplication();
        var metadata = new Metadata();
        metadata.setName("link-app-1");
        application.setMetadata(metadata);
        var spec = new LinkApplication.LinkApplicationSpec();
        spec.setDisplayName("Example Site");
        spec.setUrl("https://friend.example");
        spec.setStatus(LinkApplication.Status.PENDING);
        var origin = new LinkApplication.Origin();
        origin.setType(originType);
        spec.setOrigin(origin);
        application.setSpec(spec);
        return application;
    }
}
