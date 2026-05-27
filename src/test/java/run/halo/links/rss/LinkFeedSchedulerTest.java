package run.halo.links.rss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import run.halo.links.nitrite.LinksNitriteDatabase;

@ExtendWith(MockitoExtension.class)
class LinkFeedSchedulerTest {

    private static final Instant NOW = Instant.parse("2026-05-27T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    ReactiveExtensionClient client;

    @Mock
    LinkFeedService linkFeedService;

    @Mock
    LinkFeedRetentionService retentionService;

    @Mock
    LinksNitriteDatabase database;

    @Mock
    LinkFeedRefreshSettingsFetcher settingsFetcher;

    @Test
    void shouldSkipAutomaticRefreshWhenGloballyDisabled() {
        when(settingsFetcher.fetch()).thenReturn(Mono.just(settings(false, 1, 10)));
        LinkFeedScheduler scheduler = scheduler(NOW.minusSeconds(3600));

        StepVerifier.create(scheduler.refreshIfDue())
            .verifyComplete();

        verifyNoInteractions(client, linkFeedService);
    }

    @Test
    void shouldSkipAutomaticRefreshWhenIntervalHasNotElapsed() {
        when(settingsFetcher.fetch()).thenReturn(Mono.just(settings(true, 24, 10)));
        LinkFeedScheduler scheduler = scheduler(NOW.minusSeconds(23 * 3600));

        StepVerifier.create(scheduler.refreshIfDue())
            .verifyComplete();

        verifyNoInteractions(client, linkFeedService);
    }

    @Test
    void shouldRefreshEligibleLinksWithDefaultEnabledSettings() {
        Link link = rssLink("link-a", null, List.of("https://example.com/feed.xml"));
        when(settingsFetcher.fetch()).thenReturn(Mono.just(
            LinkFeedRefreshSettings.defaults().normalized()));
        when(client.listAll(eq(Link.class), any(ListOptions.class), any(Sort.class)))
            .thenReturn(Flux.just(link));
        when(linkFeedService.refresh("link-a")).thenReturn(Mono.just(result("link-a")));
        LinkFeedScheduler scheduler = scheduler(NOW.minusSeconds(3600));

        StepVerifier.create(scheduler.refreshIfDue())
            .verifyComplete();

        verify(linkFeedService).refresh("link-a");
    }

    @Test
    void shouldSelectMissingOrOldestRssStatusAndRespectBatchLimit() {
        Link neverFetched = rssLink("never", null, List.of("https://example.com/never.xml"));
        Link old = rssLink("old", NOW.minusSeconds(5 * 3600),
            List.of("https://example.com/old.xml"));
        Link fresh = rssLink("fresh", NOW.minusSeconds(3600),
            List.of("https://example.com/fresh.xml"));
        Link disabled = rssLink("disabled", null, List.of("https://example.com/disabled.xml"));
        disabled.getSpec().getRss().setEnabled(false);
        Link feedless = rssLink("feedless", null, List.of(" "));
        when(settingsFetcher.fetch()).thenReturn(Mono.just(settings(true, 1, 2)));
        when(client.listAll(eq(Link.class), any(ListOptions.class), any(Sort.class)))
            .thenReturn(Flux.just(fresh, disabled, feedless, old, neverFetched));
        when(linkFeedService.refresh("never")).thenReturn(Mono.just(result("never")));
        when(linkFeedService.refresh("old")).thenReturn(Mono.just(result("old")));
        LinkFeedScheduler scheduler = scheduler(NOW.minusSeconds(2 * 3600));

        StepVerifier.create(scheduler.refreshIfDue())
            .verifyComplete();

        ArgumentCaptor<String> nameCaptor = ArgumentCaptor.forClass(String.class);
        verify(linkFeedService, times(2)).refresh(nameCaptor.capture());
        assertThat(nameCaptor.getAllValues()).containsExactly("never", "old");
        verify(linkFeedService, never()).refresh("fresh");
        verify(linkFeedService, never()).refresh("disabled");
        verify(linkFeedService, never()).refresh("feedless");
        assertThat(disabled.getSpec().getRss().getEnabled()).isFalse();
    }

    private LinkFeedScheduler scheduler(Instant lastAutomaticRefreshAt) {
        return new LinkFeedScheduler(client, linkFeedService, retentionService, database,
            settingsFetcher, CLOCK, lastAutomaticRefreshAt);
    }

    private static LinkFeedRefreshSettings settings(boolean enabled, int intervalHours,
        int maxLinksPerRun) {
        LinkFeedRefreshSettings settings = new LinkFeedRefreshSettings();
        settings.setEnabled(enabled);
        settings.setIntervalHours(intervalHours);
        settings.setMaxLinksPerRun(maxLinksPerRun);
        return settings.normalized();
    }

    private static Link rssLink(String name, Instant lastFetchedAt, List<String> feedUrls) {
        Link link = new Link();
        Metadata metadata = new Metadata();
        metadata.setName(name);
        link.setMetadata(metadata);
        Link.LinkSpec spec = new Link.LinkSpec();
        Link.RssSpec rss = new Link.RssSpec();
        rss.setEnabled(true);
        rss.setFeedUrls(feedUrls);
        spec.setRss(rss);
        link.setSpec(spec);
        Link.LinkStatus status = new Link.LinkStatus();
        if (lastFetchedAt != null) {
            Link.RssStatus rssStatus = new Link.RssStatus();
            rssStatus.setLastFetchedAt(lastFetchedAt);
            status.setRss(rssStatus);
        }
        link.setStatus(status);
        return link;
    }

    private static LinkFeedRefreshResult result(String linkName) {
        LinkFeedRefreshResult result = new LinkFeedRefreshResult();
        result.setLinkName(linkName);
        return result;
    }
}
