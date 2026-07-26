package run.halo.links.endpoint;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.app.plugin.ReactiveSettingFetcher;
import run.halo.links.dto.LinkApplicationSettings;

@Slf4j
@Component
@RequiredArgsConstructor
public class LinkApplicationSettingsFetcher {

    public static final String SETTING_GROUP = "application";

    private final ReactiveSettingFetcher settingFetcher;

    public Mono<LinkApplicationSettings> fetch() {
        return settingFetcher.fetch(SETTING_GROUP, LinkApplicationSettings.class)
            .defaultIfEmpty(LinkApplicationSettings.defaults())
            .map(LinkApplicationSettings::normalized)
            .onErrorResume(error -> {
                log.warn("[plugin-links] Failed to load application settings, disabling "
                    + "new applications", error);
                return Mono.just(LinkApplicationSettings.defaults().normalized());
            });
    }
}
