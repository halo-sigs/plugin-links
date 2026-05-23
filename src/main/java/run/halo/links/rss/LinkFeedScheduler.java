package run.halo.links.rss;

import static run.halo.app.extension.index.query.Queries.equal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.links.extension.Link;
import run.halo.links.nitrite.LinksNitriteDatabase;

@Slf4j
@Component
@RequiredArgsConstructor
public class LinkFeedScheduler {

    private static final int REFRESH_CONCURRENCY = 2;

    private final ReactiveExtensionClient client;
    private final LinkFeedService linkFeedService;
    private final LinkFeedRetentionService retentionService;
    private final LinksNitriteDatabase database;

    @Scheduled(fixedDelay = 15 * 60 * 1000L, initialDelay = 2 * 60 * 1000L)
    public void refreshEnabledFeeds() {
        var options = ListOptions.builder()
            .andQuery(equal("spec.rss.enabled", Boolean.TRUE))
            .build();
        try {
            client.listAll(Link.class, options, Sort.unsorted())
                .filter(LinkFeedScheduler::hasFeedUrls)
                .map(link -> link.getMetadata().getName())
                .flatMap(name -> linkFeedService.refresh(name)
                        .doOnError(error -> log.warn("Failed to refresh RSS feed for link {}",
                            name, error))
                        .onErrorResume(error -> Mono.empty()),
                    REFRESH_CONCURRENCY)
                .blockLast();
        } catch (Throwable e) {
            log.warn("Scheduled RSS refresh failed before all enabled links were processed", e);
        }
    }

    @Scheduled(cron = "0 30 3 * * *")
    public void cleanupAndCompact() {
        try {
            retentionService.enforce(LinkFeedRetentionPolicy.defaults());
            database.compact();
        } catch (Throwable e) {
            log.warn("Scheduled RSS retention cleanup failed", e);
        }
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
