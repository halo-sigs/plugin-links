package run.halo.links.rss;

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
class LinkFeedRefreshSettingsFetcherTest {

    @Mock
    ReactiveSettingFetcher settingFetcher;

    @Test
    void shouldUseEnabledDefaultsWhenSettingsAreMissing() {
        when(settingFetcher.fetch(LinkFeedRefreshSettingsFetcher.SETTING_GROUP,
            LinkFeedRefreshSettings.class)).thenReturn(Mono.empty());
        LinkFeedRefreshSettingsFetcher fetcher = new LinkFeedRefreshSettingsFetcher(
            settingFetcher);

        StepVerifier.create(fetcher.fetch())
            .assertNext(settings -> {
                assertThat(settings.automaticRefreshEnabled()).isTrue();
                assertThat(settings.publicFeedEnabled()).isFalse();
                assertThat(settings.interval().toHours()).isEqualTo(1);
                assertThat(settings.maxLinksPerRun()).isEqualTo(50);
            })
            .verifyComplete();
    }

    @Test
    void shouldNormalizeInvalidNumbersAndPreserveExplicitDisable() {
        LinkFeedRefreshSettings raw = new LinkFeedRefreshSettings();
        raw.setEnabled(false);
        raw.setIntervalHours(0);
        raw.setMaxLinksPerRun(-1);
        when(settingFetcher.fetch(LinkFeedRefreshSettingsFetcher.SETTING_GROUP,
            LinkFeedRefreshSettings.class)).thenReturn(Mono.just(raw));
        LinkFeedRefreshSettingsFetcher fetcher = new LinkFeedRefreshSettingsFetcher(
            settingFetcher);

        StepVerifier.create(fetcher.fetch())
            .assertNext(settings -> {
                assertThat(settings.automaticRefreshEnabled()).isFalse();
                assertThat(settings.interval().toHours()).isEqualTo(1);
                assertThat(settings.maxLinksPerRun()).isEqualTo(50);
            })
            .verifyComplete();
    }

    @Test
    void shouldDefaultNullEnabledToTrue() {
        LinkFeedRefreshSettings raw = new LinkFeedRefreshSettings();
        raw.setIntervalHours(2);
        raw.setMaxLinksPerRun(10);
        when(settingFetcher.fetch(LinkFeedRefreshSettingsFetcher.SETTING_GROUP,
            LinkFeedRefreshSettings.class)).thenReturn(Mono.just(raw));
        LinkFeedRefreshSettingsFetcher fetcher = new LinkFeedRefreshSettingsFetcher(
            settingFetcher);

        StepVerifier.create(fetcher.fetch())
            .assertNext(settings -> {
                assertThat(settings.automaticRefreshEnabled()).isTrue();
                assertThat(settings.interval().toHours()).isEqualTo(2);
                assertThat(settings.maxLinksPerRun()).isEqualTo(10);
            })
            .verifyComplete();
    }

    @Test
    void shouldUseEnabledDefaultsWhenSettingsCannotBeLoaded() {
        when(settingFetcher.fetch(LinkFeedRefreshSettingsFetcher.SETTING_GROUP,
            LinkFeedRefreshSettings.class)).thenReturn(Mono.error(new IllegalStateException()));
        LinkFeedRefreshSettingsFetcher fetcher = new LinkFeedRefreshSettingsFetcher(
            settingFetcher);

        StepVerifier.create(fetcher.fetch())
            .assertNext(settings -> assertThat(settings.automaticRefreshEnabled()).isTrue())
            .verifyComplete();
    }
}
