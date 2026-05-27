package run.halo.links.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.links.extension.Link;

@ExtendWith(MockitoExtension.class)
class LinkVerificationSchedulerTest {

    private static final Instant NOW = Instant.parse("2026-05-27T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    ReactiveExtensionClient client;

    @Mock
    LinkVerificationService linkVerificationService;

    @Mock
    LinkVerificationSettingsFetcher settingsFetcher;

    @Test
    void shouldSkipWorkWhenDisabled() {
        when(settingsFetcher.fetch()).thenReturn(Mono.just(settings(false, 1, 10, false)));
        LinkVerificationScheduler scheduler = scheduler(NOW.minusSeconds(3600));

        StepVerifier.create(scheduler.runIfDue())
            .verifyComplete();

        verifyNoInteractions(client, linkVerificationService);
    }

    @Test
    void shouldSkipWorkWhenIntervalHasNotElapsed() {
        when(settingsFetcher.fetch()).thenReturn(Mono.just(settings(true, 24, 10, false)));
        LinkVerificationScheduler scheduler = scheduler(NOW.minusSeconds(23 * 3600));

        StepVerifier.create(scheduler.runIfDue())
            .verifyComplete();

        verifyNoInteractions(client, linkVerificationService);
    }

    @Test
    void shouldSelectStaleLinksAndPassFullModeWhenBacklinkEnabled() {
        Link neverChecked = link("never", null);
        Link old = link("old", NOW.minusSeconds(5 * 3600));
        Link fresh = link("fresh", NOW.minusSeconds(3600));
        LinkVerificationTriggerResult result = new LinkVerificationTriggerResult();
        result.setAcceptedNames(List.of("never", "old"));
        when(settingsFetcher.fetch()).thenReturn(Mono.just(settings(true, 1, 2, true)));
        when(client.listAll(eq(Link.class), any(ListOptions.class), any(Sort.class)))
            .thenReturn(Flux.just(fresh, old, neverChecked));
        when(linkVerificationService.verify(any(LinkVerificationRequest.class),
            eq(LinkVerificationMode.FULL))).thenReturn(Mono.just(result));
        LinkVerificationScheduler scheduler = scheduler(NOW.minusSeconds(2 * 3600));

        StepVerifier.create(scheduler.runIfDue())
            .expectNext(result)
            .verifyComplete();

        ArgumentCaptor<LinkVerificationRequest> requestCaptor =
            ArgumentCaptor.forClass(LinkVerificationRequest.class);
        verify(linkVerificationService).verify(requestCaptor.capture(),
            eq(LinkVerificationMode.FULL));
        assertThat(requestCaptor.getValue().getNames()).containsExactly("never", "old");
    }

    @Test
    void shouldPassAccessOnlyModeWhenBacklinkDisabled() {
        Link neverChecked = link("never", null);
        LinkVerificationTriggerResult result = new LinkVerificationTriggerResult();
        result.setAcceptedNames(List.of("never"));
        when(settingsFetcher.fetch()).thenReturn(Mono.just(settings(true, 1, 50, false)));
        when(client.listAll(eq(Link.class), any(ListOptions.class), any(Sort.class)))
            .thenReturn(Flux.just(neverChecked));
        when(linkVerificationService.verify(any(LinkVerificationRequest.class),
            eq(LinkVerificationMode.ACCESS_ONLY))).thenReturn(Mono.just(result));
        LinkVerificationScheduler scheduler = scheduler(NOW.minusSeconds(3600));

        StepVerifier.create(scheduler.runIfDue())
            .expectNext(result)
            .verifyComplete();

        verify(linkVerificationService).verify(any(LinkVerificationRequest.class),
            eq(LinkVerificationMode.ACCESS_ONLY));
        verify(linkVerificationService, never()).verify(any(LinkVerificationRequest.class),
            eq(LinkVerificationMode.FULL));
    }

    private LinkVerificationScheduler scheduler(Instant lastAutomaticRunAt) {
        return new LinkVerificationScheduler(client, linkVerificationService, settingsFetcher,
            CLOCK, lastAutomaticRunAt);
    }

    private static LinkVerificationSettings settings(boolean enabled, int intervalHours,
        int maxLinksPerRun, boolean checkBacklink) {
        LinkVerificationSettings settings = new LinkVerificationSettings();
        settings.setEnabled(enabled);
        settings.setIntervalHours(intervalHours);
        settings.setMaxLinksPerRun(maxLinksPerRun);
        settings.setCheckBacklink(checkBacklink);
        return settings.normalized();
    }

    private static Link link(String name, Instant lastCheckedAt) {
        Link link = new Link();
        Metadata metadata = new Metadata();
        metadata.setName(name);
        link.setMetadata(metadata);
        Link.LinkStatus status = new Link.LinkStatus();
        if (lastCheckedAt != null) {
            Link.VerificationStatus verification = new Link.VerificationStatus();
            verification.setLastCheckedAt(lastCheckedAt);
            status.setVerification(verification);
        }
        link.setStatus(status);
        return link;
    }
}
