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
class LinkFeedPublicSettingsFetcherTest {

    @Mock
    ReactiveSettingFetcher settingFetcher;

    @Test
    void shouldDisablePublicFeedsWhenSettingsAreMissing() {
        when(settingFetcher.fetch(LinkFeedPublicSettingsFetcher.SETTING_GROUP,
            LinkFeedRefreshSettings.class)).thenReturn(Mono.empty());
        LinkFeedPublicSettingsFetcher fetcher = new LinkFeedPublicSettingsFetcher(
            settingFetcher);

        StepVerifier.create(fetcher.isPublicEnabled())
            .assertNext(enabled -> assertThat(enabled).isFalse())
            .verifyComplete();
    }

    @Test
    void shouldEnablePublicFeedsOnlyWhenExplicitlyEnabled() {
        LinkFeedRefreshSettings raw = new LinkFeedRefreshSettings();
        raw.setPublicEnabled(true);
        when(settingFetcher.fetch(LinkFeedPublicSettingsFetcher.SETTING_GROUP,
            LinkFeedRefreshSettings.class)).thenReturn(Mono.just(raw));
        LinkFeedPublicSettingsFetcher fetcher = new LinkFeedPublicSettingsFetcher(
            settingFetcher);

        StepVerifier.create(fetcher.isPublicEnabled())
            .assertNext(enabled -> assertThat(enabled).isTrue())
            .verifyComplete();
    }

    @Test
    void shouldDisablePublicFeedsWhenSettingsCannotBeLoaded() {
        when(settingFetcher.fetch(LinkFeedPublicSettingsFetcher.SETTING_GROUP,
            LinkFeedRefreshSettings.class)).thenReturn(Mono.error(new IllegalStateException()));
        LinkFeedPublicSettingsFetcher fetcher = new LinkFeedPublicSettingsFetcher(
            settingFetcher);

        StepVerifier.create(fetcher.isPublicEnabled())
            .assertNext(enabled -> assertThat(enabled).isFalse())
            .verifyComplete();
    }
}
