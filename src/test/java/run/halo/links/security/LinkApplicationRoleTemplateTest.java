package run.halo.links.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class LinkApplicationRoleTemplateTest {

    @Test
    void shouldGrantOnlyManageRoleApplicationSubresources() throws IOException {
        String yaml;
        try (var input = getClass().getResourceAsStream("/extensions/roleTemplate.yaml")) {
            assertThat(input).isNotNull();
            yaml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        String viewRole = yaml.substring(0, yaml.indexOf("---"));
        String manageRole = yaml.substring(yaml.indexOf("name: role-template-link-manage"),
            yaml.lastIndexOf("---"));

        assertThat(viewRole)
            .doesNotContain("linkapplications/approve")
            .doesNotContain("linkapplications/origin-comment")
            .doesNotContain("linkapplications/cleanup");
        assertThat(manageRole)
            .contains("linkapplications/approve")
            .contains("linkapplications/reject")
            .contains("linkapplications/verify")
            .contains("linkapplications/origin-comment")
            .contains("linkapplications/cleanup");
        assertThat(manageRole.substring(
            manageRole.indexOf("linkapplications/approve"),
            manageRole.indexOf("linkapplications/origin-comment")))
            .doesNotContain("resourceNames");
        assertThat(manageRole.substring(manageRole.indexOf("linkapplications/cleanup")))
            .contains("resourceNames: [ \"-\" ]");
    }

    @Test
    void shouldGrantAnonymousOnlyCreateAccessToPublicApplicationResources()
        throws IOException {
        String yaml;
        try (var input = getClass().getResourceAsStream("/extensions/roleTemplate.yaml")) {
            assertThat(input).isNotNull();
            yaml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        String anonymousRole = yaml.substring(
            yaml.indexOf("name: role-template-link-anonymous"));

        assertThat(anonymousRole)
            .contains("resources: [ \"link-applications\" ]")
            .contains("- \"/apis/api.link.halo.run/v1alpha1/link-applications/captcha\"")
            .contains("verbs: [ \"create\" ]")
            .doesNotContain("link-applications/*");
        String applicationRule = anonymousRole.substring(
            anonymousRole.indexOf("resources: [ \"link-applications\""));
        assertThat(applicationRule)
            .doesNotContain("\"get\"")
            .doesNotContain("\"list\"")
            .doesNotContain("\"update\"")
            .doesNotContain("\"patch\"")
            .doesNotContain("\"delete\"");
    }
}
