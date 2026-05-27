package run.halo.links.rss;

import static run.halo.app.extension.index.query.Queries.equal;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.links.extension.Link;
import run.halo.links.nitrite.LinksNitriteDatabase;

@Slf4j
@Component
public class LinkFeedScheduler {

    private static final long SCHEDULER_DELAY_MS = 5 * 60 * 1000L;
    private static final int REFRESH_CONCURRENCY = 2;

    private final ReactiveExtensionClient client;
    private final LinkFeedService linkFeedService;
    private final LinkFeedRetentionService retentionService;
    private final LinksNitriteDatabase database;
    private final LinkFeedRefreshSettingsFetcher settingsFetcher;
    private final Clock clock;
    private Instant lastAutomaticRefreshAt;

    @Autowired
    public LinkFeedScheduler(ReactiveExtensionClient client,
        LinkFeedService linkFeedService,
        LinkFeedRetentionService retentionService,
        LinksNitriteDatabase database,
        LinkFeedRefreshSettingsFetcher settingsFetcher) {
        this(client, linkFeedService, retentionService, database, settingsFetcher,
            Clock.systemUTC());
    }

    LinkFeedScheduler(ReactiveExtensionClient client,
        LinkFeedService linkFeedService,
        LinkFeedRetentionService retentionService,
        LinksNitriteDatabase database,
        LinkFeedRefreshSettingsFetcher settingsFetcher,
        Clock clock) {
        this(client, linkFeedService, retentionService, database, settingsFetcher, clock,
            Instant.now(clock));
    }

    LinkFeedScheduler(ReactiveExtensionClient client,
        LinkFeedService linkFeedService,
        LinkFeedRetentionService retentionService,
        LinksNitriteDatabase database,
        LinkFeedRefreshSettingsFetcher settingsFetcher,
        Clock clock,
        Instant lastAutomaticRefreshAt) {
        this.client = client;
        this.linkFeedService = linkFeedService;
        this.retentionService = retentionService;
        this.database = database;
        this.settingsFetcher = settingsFetcher;
        this.clock = clock;
        this.lastAutomaticRefreshAt = lastAutomaticRefreshAt;
    }

    @Scheduled(fixedDelay = SCHEDULER_DELAY_MS, initialDelay = SCHEDULER_DELAY_MS)
    public void refreshEnabledFeeds() {
        try {
            refreshIfDue().block();
        } catch (Throwable e) {
            log.warn("[plugin-links] Scheduled RSS refresh failed before work was accepted", e);
        }
    }

    Mono<Void> refreshIfDue() {
        return settingsFetcher.fetch()
            .flatMap(settings -> {
                if (!settings.automaticRefreshEnabled()) {
                    log.debug("[plugin-links] Scheduled RSS refresh is disabled");
                    return Mono.empty();
                }
                if (!isDue(settings)) {
                    log.debug("[plugin-links] Scheduled RSS refresh is not due yet");
                    return Mono.empty();
                }
                Instant runAt = Instant.now(clock);
                return selectLinkNames(settings.maxLinksPerRun())
                    .collectList()
                    .flatMap(names -> {
                        lastAutomaticRefreshAt = runAt;
                        log.info("[plugin-links] Scheduled RSS refresh selected {} link(s), "
                                + "intervalHours={}, maxLinksPerRun={}", names.size(),
                            settings.interval().toHours(), settings.maxLinksPerRun());
                        return Flux.fromIterable(names)
                            .flatMap(name -> linkFeedService.refresh(name)
                                    .doOnError(error -> log.warn(
                                        "[plugin-links] Failed to refresh RSS feed for link {}",
                                        name, error))
                                    .onErrorResume(error -> Mono.empty()),
                                REFRESH_CONCURRENCY)
                            .then(Mono.fromRunnable(() -> log.info(
                                "[plugin-links] Scheduled RSS refresh finished for {} selected "
                                    + "link(s)",
                                names.size())));
                    });
            });
    }

    Flux<String> selectLinkNames(int maxLinksPerRun) {
        var options = ListOptions.builder()
            .andQuery(equal("spec.rss.enabled", Boolean.TRUE))
            .build();
        return client.listAll(Link.class, options, Sort.unsorted())
            .filter(LinkFeedScheduler::isRssEnabled)
            .filter(LinkFeedScheduler::hasFeedUrls)
            .sort(linkComparator())
            .take(maxLinksPerRun)
            .map(LinkFeedScheduler::linkName);
    }

    @Scheduled(cron = "0 30 3 * * *")
    public void cleanupAndCompact() {
        try {
            retentionService.enforce(LinkFeedRetentionPolicy.defaults());
            database.compact();
        } catch (Throwable e) {
            log.warn("[plugin-links] Scheduled RSS retention cleanup failed", e);
        }
    }

    private boolean isDue(LinkFeedRefreshSettings settings) {
        return !Instant.now(clock).isBefore(lastAutomaticRefreshAt.plus(settings.interval()));
    }

    private static Comparator<Link> linkComparator() {
        return Comparator.comparing(LinkFeedScheduler::lastFetchedAt,
                Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(LinkFeedScheduler::linkName,
                Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private static Instant lastFetchedAt(Link link) {
        Link.RssStatus rss = link.getStatus().getRss();
        return rss == null ? null : rss.getLastFetchedAt();
    }

    private static String linkName(Link link) {
        return link.getMetadata() == null ? null : link.getMetadata().getName();
    }

    private static boolean isRssEnabled(Link link) {
        return link.getSpec() != null
            && link.getSpec().getRss() != null
            && Boolean.TRUE.equals(link.getSpec().getRss().getEnabled());
    }

    private static boolean hasFeedUrls(Link link) {
        return link.getSpec() != null
            && link.getSpec().getRss() != null
            && link.getSpec().getRss().getFeedUrls() != null
            && link.getSpec().getRss().getFeedUrls()
            .stream()
            .anyMatch(StringUtils::hasText);
    }
}
