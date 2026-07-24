package run.halo.links.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.app.plugin.ReactiveSettingFetcher;
import run.halo.links.dto.LinkAiSettings;

@ExtendWith(MockitoExtension.class)
class LinkAiSettingsFetcherTest {

    @Mock
    ReactiveSettingFetcher settingFetcher;

    @Test
    void shouldUseDisabledDefaultsWhenSettingsAreMissing() {
        when(settingFetcher.fetch(LinkAiSettingsFetcher.SETTING_GROUP, LinkAiSettings.class))
            .thenReturn(Mono.empty());
        var fetcher = new LinkAiSettingsFetcher(settingFetcher);

        StepVerifier.create(fetcher.fetch())
            .assertNext(settings -> {
                assertThat(settings.aiEnabled()).isFalse();
                assertThat(settings.commentExtractionEnabled()).isFalse();
                assertThat(settings.commentExtractionModelName()).isNull();
                assertThat(settings.commentApplicationRecognitionEnabled()).isFalse();
                assertThat(settings.commentApplicationRecognitionModelName()).isNull();
                assertThat(settings.commentApplicationRecognitionSources()).isEmpty();
            })
            .verifyComplete();
    }

    @Test
    void shouldNormalizeCommentExtractionSettings() {
        var raw = new LinkAiSettings();
        raw.setEnabled(true);
        var commentExtraction = new LinkAiSettings.CommentExtraction();
        commentExtraction.setModelName("  model-a  ");
        raw.setCommentExtraction(commentExtraction);
        when(settingFetcher.fetch(LinkAiSettingsFetcher.SETTING_GROUP, LinkAiSettings.class))
            .thenReturn(Mono.just(raw));
        var fetcher = new LinkAiSettingsFetcher(settingFetcher);

        StepVerifier.create(fetcher.fetch())
            .assertNext(settings -> {
                assertThat(settings.aiEnabled()).isTrue();
                assertThat(settings.commentExtractionEnabled()).isTrue();
                assertThat(settings.commentExtractionModelName()).isEqualTo("model-a");
            })
            .verifyComplete();
    }

    @Test
    void shouldNormalizeCommentApplicationRecognitionSettings() {
        var raw = new LinkAiSettings();
        raw.setEnabled(true);
        var recognition = new LinkAiSettings.CommentApplicationRecognition();
        recognition.setEnabled(true);
        recognition.setModelName("  model-a  ");
        recognition.setSources(List.of(
            source(LinkAiSettings.SourceType.LINKS, null),
            source(LinkAiSettings.SourceType.LINKS, "stale-post-name"),
            source(LinkAiSettings.SourceType.POST, " post-a "),
            source(LinkAiSettings.SourceType.POST, " "),
            source(LinkAiSettings.SourceType.SINGLE_PAGE, "page-a")
        ));
        raw.setCommentApplicationRecognition(recognition);
        when(settingFetcher.fetch(LinkAiSettingsFetcher.SETTING_GROUP, LinkAiSettings.class))
            .thenReturn(Mono.just(raw));
        var fetcher = new LinkAiSettingsFetcher(settingFetcher);

        StepVerifier.create(fetcher.fetch())
            .assertNext(settings -> {
                assertThat(settings.commentApplicationRecognitionEnabled()).isTrue();
                assertThat(settings.commentApplicationRecognitionModelName())
                    .isEqualTo("model-a");
                assertThat(settings.commentApplicationRecognitionSources())
                    .containsExactly(
                        source(LinkAiSettings.SourceType.LINKS, null),
                        source(LinkAiSettings.SourceType.POST, "post-a"),
                        source(LinkAiSettings.SourceType.SINGLE_PAGE, "page-a")
                    );
            })
            .verifyComplete();
    }

    @Test
    void shouldDisableRecognitionWhenModelOrSourcesAreMissing() {
        var raw = new LinkAiSettings();
        raw.setEnabled(true);
        var recognition = new LinkAiSettings.CommentApplicationRecognition();
        recognition.setEnabled(true);
        raw.setCommentApplicationRecognition(recognition);
        when(settingFetcher.fetch(LinkAiSettingsFetcher.SETTING_GROUP, LinkAiSettings.class))
            .thenReturn(Mono.just(raw));
        var fetcher = new LinkAiSettingsFetcher(settingFetcher);

        StepVerifier.create(fetcher.fetch())
            .assertNext(settings ->
                assertThat(settings.commentApplicationRecognitionEnabled()).isFalse())
            .verifyComplete();
    }

    @Test
    void shouldDisableAiWhenSettingsCannotBeLoaded() {
        when(settingFetcher.fetch(LinkAiSettingsFetcher.SETTING_GROUP, LinkAiSettings.class))
            .thenReturn(Mono.error(new IllegalStateException()));
        var fetcher = new LinkAiSettingsFetcher(settingFetcher);

        StepVerifier.create(fetcher.fetch())
            .assertNext(settings -> assertThat(settings.aiEnabled()).isFalse())
            .verifyComplete();
    }

    private static LinkAiSettings.RecognitionSource source(LinkAiSettings.SourceType type,
        String name) {
        var source = new LinkAiSettings.RecognitionSource();
        source.setType(type);
        source.setName(name);
        return source;
    }
}
