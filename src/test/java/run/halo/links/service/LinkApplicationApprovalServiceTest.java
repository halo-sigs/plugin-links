package run.halo.links.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.links.extension.Link;
import run.halo.links.extension.LinkApplication;
import run.halo.links.rss.LinkFeedService;
import run.halo.links.verification.LinkVerificationRequest;
import run.halo.links.verification.LinkVerificationService;
import run.halo.links.verification.LinkVerificationTriggerResult;

@ExtendWith(MockitoExtension.class)
class LinkApplicationApprovalServiceTest {

    @Mock
    ReactiveExtensionClient client;

    @Mock
    LinkVerificationService verificationService;

    @Mock
    LinkFeedService feedService;

    LinkApplicationApprovalService service;

    @BeforeEach
    void setUp() {
        service = new LinkApplicationApprovalService(client, verificationService, feedService);
    }

    @Test
    void shouldFreezeCreateCompleteAndTriggerAutomation() {
        var application = pending("application-a");
        when(client.fetch(LinkApplication.class, "application-a"))
            .thenReturn(Mono.just(application));
        givenNoDuplicates();
        when(client.update(any(LinkApplication.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(client.fetch(Link.class, "application-a")).thenReturn(Mono.empty());
        when(client.create(any(Link.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(verificationService.verify(any(LinkVerificationRequest.class)))
            .thenReturn(Mono.just(new LinkVerificationTriggerResult()));

        StepVerifier.create(service.approve("application-a",
                new LinkApplicationApprovalService.ApprovalCommand(
                    " https://approved.example ", " Approved ", " ", " Desc ", null)))
            .assertNext(link -> {
                assertThat(link.getMetadata().getName()).isEqualTo("application-a");
                assertThat(link.getMetadata().getAnnotations())
                    .containsEntry(LinkApplicationApprovalService.APPLICATION_NAME_ANNO,
                        "application-a");
                assertThat(link.getSpec().getUrl()).isEqualTo("https://approved.example");
                assertThat(link.getSpec().getDisplayName()).isEqualTo("Approved");
                assertThat(link.getSpec().getLogo()).isNull();
                assertThat(link.getSpec().getDescription()).isEqualTo("Desc");
                assertThat(application.getSpec().getStatus())
                    .isEqualTo(LinkApplication.Status.APPROVED);
                assertThat(application.getSpec().getApproval().getRequest().getUrl())
                    .isEqualTo("https://approved.example");
            })
            .verifyComplete();

        verify(verificationService).verify(any(LinkVerificationRequest.class));
    }

    @Test
    void shouldResumeFrozenApprovalAndIgnoreReplacementFields() {
        var application = approving("application-a", "https://frozen.example");
        var owned = ownedLink("application-a", "https://frozen.example");
        when(client.fetch(LinkApplication.class, "application-a"))
            .thenReturn(Mono.just(application));
        when(client.fetch(Link.class, "application-a")).thenReturn(Mono.just(owned));
        when(client.update(application)).thenReturn(Mono.just(application));
        when(verificationService.verify(any(LinkVerificationRequest.class)))
            .thenReturn(Mono.just(new LinkVerificationTriggerResult()));

        StepVerifier.create(service.approve("application-a",
                new LinkApplicationApprovalService.ApprovalCommand(
                    "https://replacement.example", "Replacement", null, null, null)))
            .assertNext(link -> assertThat(link.getSpec().getUrl())
                .isEqualTo("https://frozen.example"))
            .verifyComplete();

        verify(client, never()).create(any(Link.class));
    }

    @Test
    void shouldReturnOwnedLinkForApprovedRetryWithoutAutomation() {
        var application = approving("application-a", "https://example.com");
        application.getSpec().setStatus(LinkApplication.Status.APPROVED);
        var owned = ownedLink("application-a", "https://example.com");
        when(client.fetch(LinkApplication.class, "application-a"))
            .thenReturn(Mono.just(application));
        when(client.fetch(Link.class, "application-a")).thenReturn(Mono.just(owned));

        StepVerifier.create(service.approve("application-a", null))
            .expectNext(owned)
            .verifyComplete();

        verify(verificationService, never()).verify(any());
    }

    @Test
    void shouldRejectInvalidOverrideBeforeReservation() {
        var application = pending("application-a");
        when(client.fetch(LinkApplication.class, "application-a"))
            .thenReturn(Mono.just(application));

        StepVerifier.create(service.approve("application-a",
                new LinkApplicationApprovalService.ApprovalCommand(
                    "ftp://example.com", null, null, null, null)))
            .expectError(ResponseStatusException.class)
            .verify();

        assertThat(application.getSpec().getStatus()).isEqualTo(LinkApplication.Status.PENDING);
        verify(client, never()).update(any(LinkApplication.class));
        verify(client, never()).create(any(Link.class));
    }

    @Test
    void shouldRejectReservedNameOwnedByAnotherApplication() {
        var application = approving("application-a", "https://example.com");
        var foreign = ownedLink("other-application", "https://example.com");
        foreign.getMetadata().setName("application-a");
        when(client.fetch(LinkApplication.class, "application-a"))
            .thenReturn(Mono.just(application));
        when(client.fetch(Link.class, "application-a")).thenReturn(Mono.just(foreign));

        StepVerifier.create(service.approve("application-a", null))
            .expectErrorSatisfies(error -> {
                assertThat(error).isInstanceOf(ResponseStatusException.class);
                assertThat(((ResponseStatusException) error).getStatusCode().value())
                    .isEqualTo(409);
            })
            .verify();

        verify(client, never()).create(any(Link.class));
    }

    @Test
    void shouldKeepApprovalDurableWhenAutomationFails() {
        var application = approving("application-a", "https://example.com");
        var owned = ownedLink("application-a", "https://example.com");
        when(client.fetch(LinkApplication.class, "application-a"))
            .thenReturn(Mono.just(application));
        when(client.fetch(Link.class, "application-a")).thenReturn(Mono.just(owned));
        when(client.update(application)).thenReturn(Mono.just(application));
        when(verificationService.verify(any(LinkVerificationRequest.class)))
            .thenReturn(Mono.error(new IllegalStateException("offline")));

        StepVerifier.create(service.approve("application-a", null))
            .expectNext(owned)
            .verifyComplete();

        assertThat(application.getSpec().getStatus()).isEqualTo(LinkApplication.Status.APPROVED);
    }

    @Test
    void shouldConvergeOnWinnerAfterConcurrentReservationConflict() {
        var loserSnapshot = pending("application-a");
        var winner = approving("application-a", "https://winner.example");
        var owned = ownedLink("application-a", "https://winner.example");
        when(client.fetch(LinkApplication.class, "application-a"))
            .thenReturn(Mono.just(loserSnapshot), Mono.just(winner));
        givenNoDuplicates();
        when(client.update(loserSnapshot))
            .thenReturn(Mono.error(new OptimisticLockingFailureException("lost race")));
        when(client.fetch(Link.class, "application-a")).thenReturn(Mono.just(owned));
        when(client.update(winner)).thenReturn(Mono.just(winner));
        when(verificationService.verify(any(LinkVerificationRequest.class)))
            .thenReturn(Mono.just(new LinkVerificationTriggerResult()));

        StepVerifier.create(service.approve("application-a",
                new LinkApplicationApprovalService.ApprovalCommand(
                    "https://loser.example", "Loser", null, null, null)))
            .assertNext(link -> assertThat(link.getSpec().getUrl())
                .isEqualTo("https://winner.example"))
            .verifyComplete();

        verify(client, never()).create(any(Link.class));
    }

    @Test
    void shouldNotCreateLinkWhenRejectWinsReservationRace() {
        var loserSnapshot = pending("application-a");
        var rejected = pending("application-a");
        rejected.getSpec().setStatus(LinkApplication.Status.REJECTED);
        when(client.fetch(LinkApplication.class, "application-a"))
            .thenReturn(Mono.just(loserSnapshot), Mono.just(rejected));
        givenNoDuplicates();
        when(client.update(loserSnapshot))
            .thenReturn(Mono.error(new OptimisticLockingFailureException("lost race")));

        StepVerifier.create(service.approve("application-a", null))
            .expectErrorSatisfies(error -> {
                assertThat(error).isInstanceOf(ResponseStatusException.class);
                assertThat(((ResponseStatusException) error).getStatusCode().value())
                    .isEqualTo(409);
            })
            .verify();

        verify(client, never()).create(any(Link.class));
    }

    @Test
    void shouldLeaveApprovalResumableWhenInfrastructureFailsAfterReservation() {
        var application = pending("application-a");
        when(client.fetch(LinkApplication.class, "application-a"))
            .thenReturn(Mono.just(application));
        givenNoDuplicates();
        when(client.update(application)).thenReturn(Mono.just(application));
        when(client.fetch(Link.class, "application-a"))
            .thenReturn(Mono.error(new IllegalStateException("store unavailable")));

        StepVerifier.create(service.approve("application-a", null))
            .expectErrorMessage("store unavailable")
            .verify();

        assertThat(application.getSpec().getStatus())
            .isEqualTo(LinkApplication.Status.APPROVING);
        assertThat(application.getSpec().getApproval().getLinkName())
            .isEqualTo("application-a");
        verify(client, never()).create(any(Link.class));
    }

    private void givenNoDuplicates() {
        when(client.listAll(eq(Link.class), any(ListOptions.class), any(Sort.class)))
            .thenReturn(Flux.empty());
        when(client.listAll(eq(LinkApplication.class), any(ListOptions.class), any(Sort.class)))
            .thenReturn(Flux.empty());
    }

    private static LinkApplication pending(String name) {
        var application = new LinkApplication();
        var metadata = new Metadata();
        metadata.setName(name);
        metadata.setVersion(1L);
        application.setMetadata(metadata);
        var spec = new LinkApplication.LinkApplicationSpec();
        spec.setUrl("https://example.com");
        spec.setDisplayName("Example");
        spec.setStatus(LinkApplication.Status.PENDING);
        spec.setFeedUrls(List.of());
        application.setSpec(spec);
        return application;
    }

    private static LinkApplication approving(String name, String url) {
        var application = pending(name);
        var request = new LinkApplication.ApprovalRequest();
        request.setUrl(url);
        request.setDisplayName("Frozen");
        var approval = new LinkApplication.Approval();
        approval.setLinkName(name);
        approval.setRequest(request);
        application.getSpec().setApproval(approval);
        application.getSpec().setStatus(LinkApplication.Status.APPROVING);
        return application;
    }

    private static Link ownedLink(String applicationName, String url) {
        var link = new Link();
        var metadata = new Metadata();
        metadata.setName(applicationName);
        metadata.setAnnotations(Map.of(
            LinkApplicationApprovalService.APPLICATION_NAME_ANNO, applicationName));
        link.setMetadata(metadata);
        var spec = new Link.LinkSpec();
        spec.setUrl(url);
        spec.setDisplayName("Frozen");
        link.setSpec(spec);
        return link;
    }
}
