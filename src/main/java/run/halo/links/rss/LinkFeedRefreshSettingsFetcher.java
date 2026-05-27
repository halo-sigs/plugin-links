package run.halo.links.rss;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.app.plugin.ReactiveSettingFetcher;

@Slf4j
@Component
@RequiredArgsConstructor
class LinkFeedRefreshSettingsFetcher {

    static final String SETTING_GROUP = "rss";

    private final ReactiveSettingFetcher settingFetcher;

    Mono<LinkFeedRefreshSettings> fetch() {
        return settingFetcher.fetch(SETTING_GROUP, LinkFeedRefreshSettings.class)
            .defaultIfEmpty(LinkFeedRefreshSettings.defaults())
            .map(LinkFeedRefreshSettings::normalized)
            .onErrorResume(error -> {
                log.warn("Failed to load RSS refresh settings, using defaults", error);
                return Mono.just(LinkFeedRefreshSettings.defaults().normalized());
            });
    }
}
