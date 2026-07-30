package run.halo.links.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.plugin.ReactiveSettingFetcher;
import run.halo.links.dto.LinkApplicationSettings;
import run.halo.links.endpoint.LinkApplicationSettingsFetcher;
import run.halo.links.extension.Link;
import run.halo.links.extension.LinkApplication;
import run.halo.links.notification.LinkApplicationNotificationPublisher;

@ExtendWith(MockitoExtension.class)
class LinkApplicationServiceTest {

    @Mock
    ReactiveExtensionClient client;

    @Mock
    LinkApplicationNotificationPublisher notificationPublisher;

    @Mock
    ReactiveSettingFetcher settingFetcher;

    LinkApplicationService service;

    @BeforeEach
    void setUp() {
        lenient().when(notificationPublisher.publish(any())).thenReturn(Mono.empty());
        lenient().when(settingFetcher.fetch(LinkApplicationSettingsFetcher.SETTING_GROUP,
            LinkApplicationSettings.class)).thenReturn(Mono.just(enabledSettings(100)));
        service = new LinkApplicationService(client,
            new LinkApplicationCreationCoordinator(),
            new LinkApplicationCapacityService(client,
                new LinkApplicationSettingsFetcher(settingFetcher)),
            notificationPublisher);
    }

    @Test
    void shouldCreatePendingFormApplicationWithOriginalUrl() {
        givenExisting(List.of(), List.of());
        when(client.create(any(LinkApplication.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(service.create(submission(" HTTPS://Example.COM:443#fragment ",
                LinkApplication.OriginType.FORM, null)))
            .assertNext(result -> {
                assertThat(result.status())
                    .isEqualTo(LinkApplicationService.CreateStatus.CREATED);
                assertThat(result.application().getSpec().getUrl())
                    .isEqualTo("HTTPS://Example.COM:443#fragment");
                assertThat(result.application().getSpec().getStatus())
                    .isEqualTo(LinkApplication.Status.PENDING);
                assertThat(result.application().getSpec().getOrigin().getType())
                    .isEqualTo(LinkApplication.OriginType.FORM);
            })
            .verifyComplete();
        verify(notificationPublisher).publish(any(LinkApplication.class));
    }

    @Test
    void shouldRejectInvalidPrimaryUrl() {
        StepVerifier.create(service.create(submission("ftp://example.com",
                LinkApplication.OriginType.FORM, null)))
            .assertNext(result -> {
                assertThat(result.status())
                    .isEqualTo(LinkApplicationService.CreateStatus.INVALID);
                assertThat(result.field()).isEqualTo("url");
            })
            .verifyComplete();
        verify(notificationPublisher, never()).publish(any());
    }

    @Test
    void shouldRejectInvalidLogoUrl() {
        var invalidLogo = submissionWithOptionalUrls("javascript:alert(1)", null, List.of());
        StepVerifier.create(service.create(invalidLogo))
            .assertNext(result -> {
                assertThat(result.status()).isEqualTo(LinkApplicationService.CreateStatus.INVALID);
                assertThat(result.field()).isEqualTo("logo");
            })
            .verifyComplete();
    }

    @Test
    void shouldRejectInvalidBacklinkUrl() {
        var invalidBacklink = submissionWithOptionalUrls(null, "javascript:alert(1)", List.of());
        StepVerifier.create(service.create(invalidBacklink))
            .assertNext(result -> {
                assertThat(result.status()).isEqualTo(LinkApplicationService.CreateStatus.INVALID);
                assertThat(result.field()).isEqualTo("backlink");
            })
            .verifyComplete();
    }

    @Test
    void shouldRejectInvalidFeedUrl() {
        var invalidFeed = submissionWithOptionalUrls(null, null,
            List.of("https://feed.example.com/rss.xml", "file:///etc/passwd"));
        StepVerifier.create(service.create(invalidFeed))
            .assertNext(result -> {
                assertThat(result.status()).isEqualTo(LinkApplicationService.CreateStatus.INVALID);
                assertThat(result.field()).isEqualTo("feedUrls");
            })
            .verifyComplete();
    }

    @Test
    void shouldBlockCanonicalMatchWithFormalLink() {
        givenExisting(List.of(link("https://example.com/")), List.of());

        verifyDuplicate(submission("https://EXAMPLE.com:443#about",
            LinkApplication.OriginType.COMMENT, "comment-a"));
    }

    @Test
    void shouldBlockPendingOrApprovedApplicationFromAnySource() {
        for (var status : List.of(LinkApplication.Status.PENDING,
            LinkApplication.Status.APPROVING,
            LinkApplication.Status.APPROVED)) {
            givenExisting(List.of(), List.of(application("https://example.com", status,
                LinkApplication.OriginType.COMMENT, "old-comment")));

            verifyDuplicate(submission("https://example.com/",
                LinkApplication.OriginType.FORM, null));
        }
    }

    @Test
    void shouldBlockUrlFrozenByApprovingApplication() {
        var approving = application("https://original.example.com",
            LinkApplication.Status.APPROVING, LinkApplication.OriginType.FORM, null);
        var approvalRequest = new LinkApplication.ApprovalRequest();
        approvalRequest.setUrl("https://reserved.example.com");
        approvalRequest.setDisplayName("Reserved");
        var approval = new LinkApplication.Approval();
        approval.setLinkName("reserved-link");
        approval.setRequest(approvalRequest);
        approving.getSpec().setApproval(approval);
        givenExisting(List.of(), List.of(approving));

        verifyDuplicate(submission("https://reserved.example.com/",
            LinkApplication.OriginType.FORM, null));
    }

    @Test
    void shouldBlockRejectedFormApplicationForEverySource() {
        givenExisting(List.of(), List.of(application("https://example.com",
            LinkApplication.Status.REJECTED, LinkApplication.OriginType.FORM, null)));

        verifyDuplicate(submission("https://example.com",
            LinkApplication.OriginType.FORM, null));
        verifyDuplicate(submission("https://example.com",
            LinkApplication.OriginType.COMMENT, "comment-a"));
    }

    @Test
    void shouldAllowFormAfterRejectedCommentApplication() {
        givenExisting(List.of(), List.of(application("https://example.com",
            LinkApplication.Status.REJECTED, LinkApplication.OriginType.COMMENT,
            "old-comment")));
        when(client.create(any(LinkApplication.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(service.create(submission("https://example.com",
                LinkApplication.OriginType.FORM, null)))
            .assertNext(result -> assertThat(result.status())
                .isEqualTo(LinkApplicationService.CreateStatus.CREATED))
            .verifyComplete();
    }

    @Test
    void shouldBlockCommentAfterRejectedCommentApplication() {
        givenExisting(List.of(), List.of(application("https://example.com",
            LinkApplication.Status.REJECTED, LinkApplication.OriginType.COMMENT,
            "old-comment")));

        verifyDuplicate(submission("https://example.com",
            LinkApplication.OriginType.COMMENT, "new-comment"));
    }

    @Test
    void shouldUseCommentNameAsStableIdempotencyKey() {
        givenExisting(List.of(), List.of(application("https://old.example.com",
            LinkApplication.Status.REJECTED, LinkApplication.OriginType.COMMENT,
            "comment-a")));

        verifyDuplicate(submission("https://new.example.com",
            LinkApplication.OriginType.COMMENT, "comment-a"));
    }

    @Test
    void shouldNormalizeStoredCommentName() {
        givenExisting(List.of(), List.of());
        when(client.create(any(LinkApplication.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        var submission = submission("https://example.com",
            LinkApplication.OriginType.COMMENT, " comment-a ");

        StepVerifier.create(service.create(submission))
            .assertNext(result -> assertThat(result.application().getSpec().getOrigin()
                .getComment().getName()).isEqualTo("comment-a"))
            .verifyComplete();
        verify(notificationPublisher).publish(any(LinkApplication.class));
    }

    @Test
    void shouldKeepCreatedResultWhenNotificationFails() {
        givenExisting(List.of(), List.of());
        when(client.create(any(LinkApplication.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(notificationPublisher.publish(any()))
            .thenReturn(Mono.error(new IllegalStateException("notification failed")));

        StepVerifier.create(service.create(submission("https://example.com",
                LinkApplication.OriginType.FORM, null)))
            .assertNext(result -> assertThat(result.status())
                .isEqualTo(LinkApplicationService.CreateStatus.CREATED))
            .verifyComplete();

        verify(notificationPublisher).publish(any(LinkApplication.class));
    }

    @Test
    void shouldRejectWhenPendingCapacityIsFull() {
        givenCapacity(1);
        givenExisting(List.of(), List.of(application("https://existing.example",
            LinkApplication.Status.PENDING, LinkApplication.OriginType.COMMENT, "comment-a")));

        StepVerifier.create(service.create(submission("https://new.example",
                LinkApplication.OriginType.FORM, null)))
            .assertNext(result -> assertThat(result.status())
                .isEqualTo(LinkApplicationService.CreateStatus.CAPACITY_REACHED))
            .verifyComplete();

        verify(client, never()).create(any(LinkApplication.class));
        verify(notificationPublisher, never()).publish(any());
    }

    @Test
    void shouldReturnDuplicateBeforeCapacityReached() {
        givenExisting(List.of(), List.of(application("https://example.com",
            LinkApplication.Status.PENDING, LinkApplication.OriginType.FORM, null)));

        verifyDuplicate(submission("https://example.com/",
            LinkApplication.OriginType.COMMENT, "comment-a"));
        verify(client, never()).create(any(LinkApplication.class));
    }

    @Test
    void shouldCreateWhenExistingApplicationsDoNotConsumeCapacity() {
        givenCapacity(1);
        when(client.create(any(LinkApplication.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        for (var status : List.of(
            LinkApplication.Status.APPROVING,
            LinkApplication.Status.APPROVED,
            LinkApplication.Status.REJECTED
        )) {
            givenExisting(List.of(), List.of(application("https://existing.example",
                status, LinkApplication.OriginType.COMMENT, "comment-" + status)));

            StepVerifier.create(service.create(submission("https://" + status + ".example",
                    LinkApplication.OriginType.FORM, null)))
                .assertNext(result -> assertThat(result.status())
                    .isEqualTo(LinkApplicationService.CreateStatus.CREATED))
                .verifyComplete();
        }
    }

    @Test
    void shouldPropagateCapacityEvaluationFailure() {
        when(client.listAll(
            org.mockito.ArgumentMatchers.eq(Link.class),
            any(ListOptions.class),
            any(Sort.class)
        )).thenReturn(Flux.empty());
        when(client.listAll(
            org.mockito.ArgumentMatchers.eq(LinkApplication.class),
            any(ListOptions.class),
            any(Sort.class)
        )).thenReturn(Flux.error(new IllegalStateException("applications unavailable")));

        StepVerifier.create(service.create(submission("https://example.com",
                LinkApplication.OriginType.FORM, null)))
            .expectErrorMessage("applications unavailable")
            .verify();

        verify(client, never()).create(any(LinkApplication.class));
        verify(notificationPublisher, never()).publish(any());
    }

    @Test
    void shouldReleaseCreationGateAfterFailure() {
        when(client.listAll(
            org.mockito.ArgumentMatchers.eq(Link.class),
            any(ListOptions.class),
            any(Sort.class)
        )).thenReturn(Flux.empty());
        when(client.listAll(
            org.mockito.ArgumentMatchers.eq(LinkApplication.class),
            any(ListOptions.class),
            any(Sort.class)
        )).thenReturn(
            Flux.error(new IllegalStateException("applications unavailable")),
            Flux.empty()
        );
        when(client.create(any(LinkApplication.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(service.create(submission("https://first.example",
                LinkApplication.OriginType.FORM, null)))
            .expectErrorMessage("applications unavailable")
            .verify();
        StepVerifier.create(service.create(submission("https://second.example",
                LinkApplication.OriginType.FORM, null)))
            .assertNext(result -> assertThat(result.status())
                .isEqualTo(LinkApplicationService.CreateStatus.CREATED))
            .verifyComplete();
    }

    @Test
    void shouldCoordinateConcurrentFormAndCommentCreation() {
        var stored = new CopyOnWriteArrayList<LinkApplication>();
        when(client.listAll(
            org.mockito.ArgumentMatchers.eq(Link.class),
            any(ListOptions.class),
            any(Sort.class)
        )).thenReturn(Flux.empty());
        when(client.listAll(
            org.mockito.ArgumentMatchers.eq(LinkApplication.class),
            any(ListOptions.class),
            any(Sort.class)
        )).thenAnswer(ignored -> Flux.defer(() -> Flux.fromIterable(stored)));
        when(client.create(any(LinkApplication.class))).thenAnswer(invocation -> {
            LinkApplication application = invocation.getArgument(0);
            stored.add(application);
            return Mono.just(application);
        });

        var form = service.create(submission("https://example.com",
            LinkApplication.OriginType.FORM, null));
        var comment = service.create(submission("https://example.com/",
            LinkApplication.OriginType.COMMENT, "comment-a"));

        StepVerifier.create(Flux.merge(form, comment).map(LinkApplicationService.CreateResult::status)
                .collectList())
            .assertNext(statuses -> assertThat(statuses)
                .containsExactlyInAnyOrder(
                    LinkApplicationService.CreateStatus.CREATED,
                    LinkApplicationService.CreateStatus.DUPLICATE))
            .verifyComplete();
    }

    @Test
    void shouldKeepDifferentUrlConcurrencyWithinPendingCapacity() {
        givenCapacity(2);
        var stored = new CopyOnWriteArrayList<LinkApplication>();
        stored.add(application("https://existing.example", LinkApplication.Status.PENDING,
            LinkApplication.OriginType.FORM, null));
        when(client.listAll(
            org.mockito.ArgumentMatchers.eq(Link.class),
            any(ListOptions.class),
            any(Sort.class)
        )).thenReturn(Flux.empty());
        when(client.listAll(
            org.mockito.ArgumentMatchers.eq(LinkApplication.class),
            any(ListOptions.class),
            any(Sort.class)
        )).thenAnswer(ignored -> Flux.defer(() -> {
            var snapshot = new ArrayList<>(stored);
            return Mono.delay(Duration.ofMillis(50))
                .thenMany(Flux.fromIterable(snapshot));
        }));
        when(client.create(any(LinkApplication.class))).thenAnswer(invocation -> {
            LinkApplication application = invocation.getArgument(0);
            stored.add(application);
            return Mono.just(application);
        });

        var first = service.create(submission("https://first.example",
            LinkApplication.OriginType.FORM, null));
        var second = service.create(submission("https://second.example",
            LinkApplication.OriginType.COMMENT, "comment-b"));

        StepVerifier.create(Flux.merge(first, second)
                .map(LinkApplicationService.CreateResult::status)
                .collectList())
            .assertNext(statuses -> assertThat(statuses)
                .containsExactlyInAnyOrder(
                    LinkApplicationService.CreateStatus.CREATED,
                    LinkApplicationService.CreateStatus.CAPACITY_REACHED))
            .verifyComplete();
        assertThat(stored).hasSize(2);
    }

    private void verifyDuplicate(LinkApplicationService.Submission submission) {
        StepVerifier.create(service.create(submission))
            .assertNext(result -> assertThat(result.status())
                .isEqualTo(LinkApplicationService.CreateStatus.DUPLICATE))
            .verifyComplete();
    }

    private void givenExisting(List<Link> links, List<LinkApplication> applications) {
        when(client.listAll(
            org.mockito.ArgumentMatchers.eq(Link.class),
            any(ListOptions.class),
            any(Sort.class)
        )).thenReturn(Flux.fromIterable(links));
        when(client.listAll(
            org.mockito.ArgumentMatchers.eq(LinkApplication.class),
            any(ListOptions.class),
            any(Sort.class)
        )).thenReturn(Flux.fromIterable(applications));
    }

    private void givenCapacity(int capacity) {
        when(settingFetcher.fetch(LinkApplicationSettingsFetcher.SETTING_GROUP,
            LinkApplicationSettings.class)).thenReturn(Mono.just(enabledSettings(capacity)));
    }

    private static LinkApplicationSettings enabledSettings(int capacity) {
        var settings = new LinkApplicationSettings();
        settings.setEnabled(true);
        var security = new LinkApplicationSettings.Security();
        security.setPendingCapacity(BigDecimal.valueOf(capacity));
        settings.setSecurity(security);
        return settings;
    }

    private static LinkApplicationService.Submission submission(String url,
        LinkApplication.OriginType originType, String commentName) {
        var origin = new LinkApplication.Origin();
        origin.setType(originType);
        if (commentName != null) {
            var comment = new LinkApplication.CommentOrigin();
            comment.setName(commentName);
            origin.setComment(comment);
        }
        return new LinkApplicationService.Submission(
            url,
            "Example",
            null,
            null,
            null,
            null,
            List.of(),
            origin
        );
    }

    private static LinkApplicationService.Submission submissionWithOptionalUrls(String logo,
        String backlink, List<String> feedUrls) {
        var origin = new LinkApplication.Origin();
        origin.setType(LinkApplication.OriginType.FORM);
        return new LinkApplicationService.Submission(
            "https://example.com",
            "Example",
            logo,
            null,
            null,
            backlink,
            feedUrls,
            origin
        );
    }

    private static Link link(String url) {
        var link = new Link();
        var spec = new Link.LinkSpec();
        spec.setUrl(url);
        link.setSpec(spec);
        return link;
    }

    private static LinkApplication application(String url, LinkApplication.Status status,
        LinkApplication.OriginType originType, String commentName) {
        var application = new LinkApplication();
        application.setMetadata(new Metadata());
        var spec = new LinkApplication.LinkApplicationSpec();
        spec.setUrl(url);
        spec.setDisplayName("Existing");
        spec.setStatus(status);
        var origin = new LinkApplication.Origin();
        origin.setType(originType);
        if (commentName != null) {
            var comment = new LinkApplication.CommentOrigin();
            comment.setName(commentName);
            origin.setComment(comment);
        }
        spec.setOrigin(origin);
        application.setSpec(spec);
        return application;
    }
}
