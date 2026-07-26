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
import run.halo.links.dto.LinkApplicationSettings;

@ExtendWith(MockitoExtension.class)
class LinkApplicationSettingsFetcherTest {

    @Mock
    ReactiveSettingFetcher settingFetcher;

    @Test
    void shouldFailClosedWhenMissingOrFailed() {
        when(settingFetcher.fetch(LinkApplicationSettingsFetcher.SETTING_GROUP,
            LinkApplicationSettings.class))
            .thenReturn(Mono.empty(), Mono.error(new IllegalStateException()));
        var fetcher = new LinkApplicationSettingsFetcher(settingFetcher);

        StepVerifier.create(fetcher.fetch())
            .assertNext(settings -> {
                assertThat(settings.applicationEnabled()).isFalse();
                assertThat(settings.selfSubmissionEnabled()).isFalse();
                assertThat(settings.commentRecognitionEnabled()).isFalse();
            })
            .verifyComplete();
        StepVerifier.create(fetcher.fetch())
            .assertNext(settings -> assertThat(settings.applicationEnabled()).isFalse())
            .verifyComplete();
    }

    @Test
    void shouldApplyMasterAndChannelSwitchesAndNormalizeRecognition() {
        var raw = new LinkApplicationSettings();
        raw.setEnabled(true);
        var selfSubmission = new LinkApplicationSettings.SelfSubmission();
        selfSubmission.setEnabled(false);
        raw.setSelfSubmission(selfSubmission);
        var recognition = new LinkApplicationSettings.CommentRecognition();
        recognition.setEnabled(true);
        recognition.setModelName(" model-a ");
        recognition.setSources(List.of(
            source(LinkApplicationSettings.SourceType.LINKS, "ignored"),
            source(LinkApplicationSettings.SourceType.POST, " post-a "),
            source(LinkApplicationSettings.SourceType.POST, " ")
        ));
        raw.setCommentRecognition(recognition);
        when(settingFetcher.fetch(LinkApplicationSettingsFetcher.SETTING_GROUP,
            LinkApplicationSettings.class)).thenReturn(Mono.just(raw));

        StepVerifier.create(new LinkApplicationSettingsFetcher(settingFetcher).fetch())
            .assertNext(settings -> {
                assertThat(settings.applicationEnabled()).isTrue();
                assertThat(settings.selfSubmissionEnabled()).isFalse();
                assertThat(settings.commentRecognitionEnabled()).isTrue();
                assertThat(settings.commentRecognitionModelName()).isEqualTo("model-a");
                assertThat(settings.commentRecognitionSources()).containsExactly(
                    source(LinkApplicationSettings.SourceType.LINKS, null),
                    source(LinkApplicationSettings.SourceType.POST, "post-a")
                );
            })
            .verifyComplete();
    }

    private static LinkApplicationSettings.RecognitionSource source(
        LinkApplicationSettings.SourceType type, String name) {
        var source = new LinkApplicationSettings.RecognitionSource();
        source.setType(type);
        source.setName(name);
        return source;
    }
}
