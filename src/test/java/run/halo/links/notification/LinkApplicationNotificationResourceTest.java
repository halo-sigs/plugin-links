package run.halo.links.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class LinkApplicationNotificationResourceTest {

    @Test
    void shouldDeclareReasonTypeAndSafeDefaultTemplate() throws IOException {
        String yaml;
        try (var input =
            getClass().getResourceAsStream("/extensions/link-application-notification.yaml")) {
            assertThat(input).isNotNull();
            yaml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(yaml)
            .contains("kind: ReasonType")
            .contains("name: plugin-links-new-link-application")
            .contains("[\"plugin:links:manage\"]")
            .contains("name: displayName")
            .contains("name: websiteUrl")
            .contains("name: originLabel")
            .contains("name: manageUrl")
            .contains("kind: NotificationTemplate")
            .contains("reasonType: plugin-links-new-link-application")
            .contains("language: default")
            .contains("收到新的友链申请：[(${displayName})]")
            .contains("访客自助申请")
            .contains("评论识别")
            .contains("前往审核")
            .contains("th:text=\"${displayName}\"")
            .contains("th:text=\"${websiteUrl}\"")
            .contains("th:text=\"${originLabel}\"")
            .contains("th:href=\"${manageUrl}\"")
            .doesNotContain("th:utext")
            .doesNotContain("${email}")
            .doesNotContain("${description}")
            .doesNotContain("${commentName}")
            .doesNotContain("${applicationName}");
    }
}
