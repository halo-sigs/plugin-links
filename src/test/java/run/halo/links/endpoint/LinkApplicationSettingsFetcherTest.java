package run.halo.links.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.app.plugin.ReactiveSettingFetcher;
import run.halo.links.dto.LinkApplicationSettings;
import tools.jackson.databind.json.JsonMapper;

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
                assertThat(settings.notificationEnabled()).isFalse();
                assertThat(settings.notificationRecipients()).isEmpty();
            })
            .verifyComplete();
        StepVerifier.create(fetcher.fetch())
            .assertNext(settings -> assertThat(settings.applicationEnabled()).isFalse())
            .verifyComplete();
    }

    @Test
    void shouldFailClosedForLegacyOrIncompleteNotificationSettings() {
        var legacy = new LinkApplicationSettings();
        legacy.setEnabled(true);
        var withoutRecipients = new LinkApplicationSettings();
        withoutRecipients.setEnabled(true);
        var notification = new LinkApplicationSettings.Notification();
        notification.setEnabled(true);
        withoutRecipients.setNotification(notification);
        when(settingFetcher.fetch(LinkApplicationSettingsFetcher.SETTING_GROUP,
            LinkApplicationSettings.class))
            .thenReturn(Mono.just(legacy), Mono.just(withoutRecipients));
        var fetcher = new LinkApplicationSettingsFetcher(settingFetcher);

        StepVerifier.create(fetcher.fetch())
            .assertNext(settings -> {
                assertThat(settings.applicationEnabled()).isTrue();
                assertThat(settings.notificationEnabled()).isFalse();
                assertThat(settings.notificationRecipients()).isEmpty();
            })
            .verifyComplete();
        StepVerifier.create(fetcher.fetch())
            .assertNext(settings -> {
                assertThat(settings.notificationEnabled()).isFalse();
                assertThat(settings.notificationRecipients()).isEmpty();
            })
            .verifyComplete();
    }

    @Test
    void shouldNormalizeNotificationRecipientsAndApplyMasterSwitch() {
        var raw = new LinkApplicationSettings();
        raw.setEnabled(true);
        var notification = new LinkApplicationSettings.Notification();
        notification.setEnabled(true);
        notification.setRecipients(List.of(" admin ", "", "admin", " reviewer "));
        raw.setNotification(notification);
        when(settingFetcher.fetch(LinkApplicationSettingsFetcher.SETTING_GROUP,
            LinkApplicationSettings.class)).thenReturn(Mono.just(raw));

        StepVerifier.create(new LinkApplicationSettingsFetcher(settingFetcher).fetch())
            .assertNext(settings -> {
                assertThat(settings.notificationEnabled()).isTrue();
                assertThat(settings.notificationRecipients())
                    .containsExactly("admin", "reviewer");
            })
            .verifyComplete();

        raw.setEnabled(false);
        StepVerifier.create(Mono.just(raw.normalized()))
            .assertNext(settings -> {
                assertThat(settings.notificationEnabled()).isFalse();
                assertThat(settings.notificationRecipients())
                    .containsExactly("admin", "reviewer");
            })
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

    @Test
    void shouldDefaultPendingCapacityToOneHundred() {
        var raw = new LinkApplicationSettings();
        raw.setEnabled(true);
        when(settingFetcher.fetch(LinkApplicationSettingsFetcher.SETTING_GROUP,
            LinkApplicationSettings.class)).thenReturn(Mono.just(raw));

        StepVerifier.create(new LinkApplicationSettingsFetcher(settingFetcher).fetch())
            .assertNext(settings -> {
                assertThat(settings.applicationEnabled()).isTrue();
                assertThat(settings.pendingCapacity()).isEqualTo(BigInteger.valueOf(100));
            })
            .verifyComplete();
    }

    @Test
    void shouldKeepPositivePendingCapacity() {
        var raw = new LinkApplicationSettings();
        raw.setEnabled(true);
        var security = new LinkApplicationSettings.Security();
        security.setPendingCapacity(BigDecimal.valueOf(250));
        raw.setSecurity(security);
        when(settingFetcher.fetch(LinkApplicationSettingsFetcher.SETTING_GROUP,
            LinkApplicationSettings.class)).thenReturn(Mono.just(raw));

        StepVerifier.create(new LinkApplicationSettingsFetcher(settingFetcher).fetch())
            .assertNext(settings -> {
                assertThat(settings.applicationEnabled()).isTrue();
                assertThat(settings.pendingCapacity()).isEqualTo(BigInteger.valueOf(250));
            })
            .verifyComplete();
    }

    @Test
    void shouldFailClosedForNonPositivePendingCapacity() {
        var zero = new LinkApplicationSettings();
        zero.setEnabled(true);
        var zeroSecurity = new LinkApplicationSettings.Security();
        zeroSecurity.setPendingCapacity(BigDecimal.ZERO);
        zero.setSecurity(zeroSecurity);
        var negative = new LinkApplicationSettings();
        negative.setEnabled(true);
        var negativeSecurity = new LinkApplicationSettings.Security();
        negativeSecurity.setPendingCapacity(BigDecimal.valueOf(-1));
        negative.setSecurity(negativeSecurity);
        when(settingFetcher.fetch(LinkApplicationSettingsFetcher.SETTING_GROUP,
            LinkApplicationSettings.class)).thenReturn(Mono.just(zero), Mono.just(negative));
        var fetcher = new LinkApplicationSettingsFetcher(settingFetcher);

        StepVerifier.create(fetcher.fetch())
            .assertNext(settings -> assertThat(settings.applicationEnabled()).isFalse())
            .verifyComplete();
        StepVerifier.create(fetcher.fetch())
            .assertNext(settings -> assertThat(settings.applicationEnabled()).isFalse())
            .verifyComplete();
    }

    @Test
    void shouldFailClosedForFractionalPendingCapacity() {
        var mapper = JsonMapper.builder().build();
        var raw = mapper.convertValue(
            mapper.readTree("""
                {
                  "enabled": true,
                  "security": {
                    "pendingCapacity": 1.5
                  }
                }
                """),
            LinkApplicationSettings.class
        );
        when(settingFetcher.fetch(LinkApplicationSettingsFetcher.SETTING_GROUP,
            LinkApplicationSettings.class)).thenReturn(Mono.just(raw));

        StepVerifier.create(new LinkApplicationSettingsFetcher(settingFetcher).fetch())
            .assertNext(settings -> assertThat(settings.applicationEnabled()).isFalse())
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
