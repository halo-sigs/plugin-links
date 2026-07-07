package run.halo.links.endpoint;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.app.plugin.ReactiveSettingFetcher;
import run.halo.links.dto.LinkAiSettings;

@Slf4j
@Component
@RequiredArgsConstructor
public class LinkAiSettingsFetcher {

    static final String SETTING_GROUP = "ai";

    private final ReactiveSettingFetcher settingFetcher;

    Mono<LinkAiSettings> fetch() {
        return settingFetcher.fetch(SETTING_GROUP, LinkAiSettings.class)
            .defaultIfEmpty(LinkAiSettings.defaults())
            .map(LinkAiSettings::normalized)
            .onErrorResume(error -> {
                log.warn("[plugin-links] Failed to load AI settings, disabling AI features",
                    error);
                return Mono.just(LinkAiSettings.defaults().normalized());
            });
    }
}
