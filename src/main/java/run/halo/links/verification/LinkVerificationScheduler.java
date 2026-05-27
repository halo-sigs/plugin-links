package run.halo.links.verification;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.links.extension.Link;

@Slf4j
@Component
public class LinkVerificationScheduler {

    private static final long SCHEDULER_DELAY_MS = 5 * 60 * 1000L;

    private final ReactiveExtensionClient client;
    private final LinkVerificationService linkVerificationService;
    private final LinkVerificationSettingsFetcher settingsFetcher;
    private final Clock clock;
    private Instant lastAutomaticRunAt;

    @Autowired
    public LinkVerificationScheduler(ReactiveExtensionClient client,
        LinkVerificationService linkVerificationService,
        LinkVerificationSettingsFetcher settingsFetcher) {
        this(client, linkVerificationService, settingsFetcher, Clock.systemUTC());
    }

    LinkVerificationScheduler(ReactiveExtensionClient client,
        LinkVerificationService linkVerificationService,
        LinkVerificationSettingsFetcher settingsFetcher, Clock clock) {
        this(client, linkVerificationService, settingsFetcher, clock, Instant.now(clock));
    }

    LinkVerificationScheduler(ReactiveExtensionClient client,
        LinkVerificationService linkVerificationService,
        LinkVerificationSettingsFetcher settingsFetcher, Clock clock, Instant lastAutomaticRunAt) {
        this.client = client;
        this.linkVerificationService = linkVerificationService;
        this.settingsFetcher = settingsFetcher;
        this.clock = clock;
        this.lastAutomaticRunAt = lastAutomaticRunAt;
    }

    @Scheduled(fixedDelay = SCHEDULER_DELAY_MS, initialDelay = SCHEDULER_DELAY_MS)
    public void verifyLinksAutomatically() {
        try {
            runIfDue().block();
        } catch (Throwable e) {
            log.warn("Scheduled link verification failed before work was accepted", e);
        }
    }

    Mono<LinkVerificationTriggerResult> runIfDue() {
        return settingsFetcher.fetch()
            .flatMap(settings -> {
                if (!settings.automaticVerificationEnabled() || !isDue(settings)) {
                    return Mono.empty();
                }
                Instant runAt = Instant.now(clock);
                return selectLinkNames(settings.maxLinksPerRun())
                    .collectList()
                    .flatMap(names -> {
                        lastAutomaticRunAt = runAt;
                        if (names.isEmpty()) {
                            return Mono.empty();
                        }
                        LinkVerificationRequest request = new LinkVerificationRequest();
                        request.setNames(names);
                        return linkVerificationService.verify(request, verificationMode(settings));
                    });
            });
    }

    Flux<String> selectLinkNames(int maxLinksPerRun) {
        return client.listAll(Link.class, ListOptions.builder().build(), Sort.unsorted())
            .sort(linkComparator())
            .take(maxLinksPerRun)
            .map(link -> link.getMetadata().getName());
    }

    private boolean isDue(LinkVerificationSettings settings) {
        return !Instant.now(clock).isBefore(lastAutomaticRunAt.plus(settings.interval()));
    }

    private static LinkVerificationMode verificationMode(LinkVerificationSettings settings) {
        return settings.includeBacklink()
            ? LinkVerificationMode.FULL
            : LinkVerificationMode.ACCESS_ONLY;
    }

    private static Comparator<Link> linkComparator() {
        return Comparator.comparing(LinkVerificationScheduler::lastCheckedAt,
                Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(LinkVerificationScheduler::linkName,
                Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private static Instant lastCheckedAt(Link link) {
        Link.VerificationStatus verification = link.getStatus().getVerification();
        return verification == null ? null : verification.getLastCheckedAt();
    }

    private static String linkName(Link link) {
        return link.getMetadata() == null ? null : link.getMetadata().getName();
    }
}
