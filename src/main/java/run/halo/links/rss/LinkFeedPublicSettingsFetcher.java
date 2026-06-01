package run.halo.links.rss;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.app.plugin.ReactiveSettingFetcher;

@Slf4j
@Component
@RequiredArgsConstructor
public class LinkFeedPublicSettingsFetcher {

    static final String SETTING_GROUP = "rss";

    private final ReactiveSettingFetcher settingFetcher;

    public Mono<Boolean> isPublicEnabled() {
        return settingFetcher.fetch(SETTING_GROUP, LinkFeedRefreshSettings.class)
            .defaultIfEmpty(LinkFeedRefreshSettings.defaults())
            .map(LinkFeedRefreshSettings::publicFeedEnabled)
            .onErrorResume(error -> {
                log.warn("[plugin-links] Failed to load RSS public settings, keeping public "
                    + "feed queries disabled", error);
                return Mono.just(false);
            });
    }
}
