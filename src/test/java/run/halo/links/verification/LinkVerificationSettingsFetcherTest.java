package run.halo.links.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.app.plugin.ReactiveSettingFetcher;

@ExtendWith(MockitoExtension.class)
class LinkVerificationSettingsFetcherTest {

    @Mock
    ReactiveSettingFetcher settingFetcher;

    @Test
    void shouldUseDisabledDefaultsWhenSettingsAreMissing() {
        when(settingFetcher.fetch(LinkVerificationSettingsFetcher.SETTING_GROUP,
            LinkVerificationSettings.class)).thenReturn(Mono.empty());
        LinkVerificationSettingsFetcher fetcher = new LinkVerificationSettingsFetcher(
            settingFetcher);

        StepVerifier.create(fetcher.fetch())
            .assertNext(settings -> {
                assertThat(settings.automaticVerificationEnabled()).isFalse();
                assertThat(settings.interval().toHours()).isEqualTo(24);
                assertThat(settings.maxLinksPerRun()).isEqualTo(50);
                assertThat(settings.includeBacklink()).isFalse();
            })
            .verifyComplete();
    }

    @Test
    void shouldNormalizeInvalidNumbersAndPreserveEnabledFlags() {
        LinkVerificationSettings raw = new LinkVerificationSettings();
        raw.setEnabled(true);
        raw.setIntervalHours(0);
        raw.setMaxLinksPerRun(-1);
        raw.setCheckBacklink(true);
        when(settingFetcher.fetch(LinkVerificationSettingsFetcher.SETTING_GROUP,
            LinkVerificationSettings.class)).thenReturn(Mono.just(raw));
        LinkVerificationSettingsFetcher fetcher = new LinkVerificationSettingsFetcher(
            settingFetcher);

        StepVerifier.create(fetcher.fetch())
            .assertNext(settings -> {
                assertThat(settings.automaticVerificationEnabled()).isTrue();
                assertThat(settings.interval().toHours()).isEqualTo(24);
                assertThat(settings.maxLinksPerRun()).isEqualTo(50);
                assertThat(settings.includeBacklink()).isTrue();
            })
            .verifyComplete();
    }

    @Test
    void shouldDisableAutomaticVerificationWhenSettingsCannotBeLoaded() {
        when(settingFetcher.fetch(LinkVerificationSettingsFetcher.SETTING_GROUP,
            LinkVerificationSettings.class)).thenReturn(Mono.error(new IllegalStateException()));
        LinkVerificationSettingsFetcher fetcher = new LinkVerificationSettingsFetcher(
            settingFetcher);

        StepVerifier.create(fetcher.fetch())
            .assertNext(settings -> assertThat(settings.automaticVerificationEnabled()).isFalse())
            .verifyComplete();
    }
}
