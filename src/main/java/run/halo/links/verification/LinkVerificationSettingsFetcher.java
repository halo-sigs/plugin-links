package run.halo.links.verification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.app.plugin.ReactiveSettingFetcher;

@Slf4j
@Component
@RequiredArgsConstructor
class LinkVerificationSettingsFetcher {

    static final String SETTING_GROUP = "verification";

    private final ReactiveSettingFetcher settingFetcher;

    Mono<LinkVerificationSettings> fetch() {
        return settingFetcher.fetch(SETTING_GROUP, LinkVerificationSettings.class)
            .defaultIfEmpty(LinkVerificationSettings.defaults())
            .map(LinkVerificationSettings::normalized)
            .onErrorResume(error -> {
                log.warn("Failed to load link verification settings, disabling automatic checks",
                    error);
                return Mono.just(LinkVerificationSettings.defaults().normalized());
            });
    }
}
