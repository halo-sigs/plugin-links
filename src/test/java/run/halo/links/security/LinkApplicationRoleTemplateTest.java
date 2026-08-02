package run.halo.links.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class LinkApplicationRoleTemplateTest {

    @Test
    void shouldMatchFeedEndpointPathsUsingHaloRequestAttributes() throws IOException {
        Map<String, List<Map<String, Object>>> roleRules = loadRoleRules();
        List<Map<String, Object>> viewRules = roleRules.get("role-template-link-view");
        List<Map<String, Object>> manageRules = roleRules.get("role-template-link-manage");

        assertThat(viewRules).isNotNull();
        assertThat(manageRules).isNotNull();

        assertThat(allows(viewRules, "GET", "rss/discovery")).isTrue();
        assertThat(allows(viewRules, "GET", "rss/items")).isTrue();
        assertThat(allows(viewRules, "GET", "rss/items/-/hidden-count")).isTrue();
        assertThat(allows(viewRules, "GET", "rss/items/-/unread-summary")).isTrue();
        assertThat(allows(viewRules, "POST", "rss/items/-/read")).isTrue();
        assertThat(allows(viewRules, "POST", "rss/items/item-a/read")).isTrue();
        assertThat(allows(viewRules, "POST", "rss/items/item-a/favorite")).isTrue();
        assertThat(allows(viewRules, "POST", "rss/items/item-a/read-later")).isTrue();
        assertThat(allows(viewRules, "POST", "rss/items/-/hidden")).isFalse();

        assertThat(allows(manageRules, "POST", "links/link-a/rss/refresh")).isTrue();
        assertThat(allows(manageRules, "POST", "rss/-/cleanup")).isTrue();
        assertThat(allows(manageRules, "POST", "rss/items/-/hidden")).isTrue();
    }

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

    @SuppressWarnings("unchecked")
    private Map<String, List<Map<String, Object>>> loadRoleRules() throws IOException {
        try (var input = getClass().getResourceAsStream("/extensions/roleTemplate.yaml")) {
            assertThat(input).isNotNull();
            var rulesByRole = new LinkedHashMap<String, List<Map<String, Object>>>();
            for (Object loaded : new Yaml().loadAll(input)) {
                Map<String, Object> role = (Map<String, Object>) loaded;
                Map<String, Object> metadata = (Map<String, Object>) role.get("metadata");
                rulesByRole.put((String) metadata.get("name"),
                    (List<Map<String, Object>>) role.get("rules"));
            }
            return rulesByRole;
        }
    }

    private static boolean allows(List<Map<String, Object>> rules, String method, String path) {
        RequestAttributes request = RequestAttributes.from(method, path);
        return rules.stream().anyMatch(rule -> values(rule, "apiGroups")
                .contains("console.api.link.halo.run")
            && values(rule, "verbs").contains(request.verb())
            && values(rule, "resources").stream()
                .anyMatch(resource -> resource.equals("*") || resource.equals(request.resource()))
            && resourceNameMatches(values(rule, "resourceNames"), request.resourceName()));
    }

    private static boolean resourceNameMatches(List<String> patterns, String resourceName) {
        if (patterns.isEmpty()) {
            return true;
        }
        return patterns.stream().anyMatch(pattern -> {
            String[] patternParts = pattern.split("/");
            String[] nameParts = resourceName.split("/");
            if (patternParts.length != nameParts.length) {
                return false;
            }
            for (int i = 0; i < patternParts.length; i++) {
                if (!Objects.equals(patternParts[i], "*")
                    && !Objects.equals(patternParts[i], nameParts[i])) {
                    return false;
                }
            }
            return true;
        });
    }

    private static List<String> values(Map<String, Object> rule, String key) {
        Object value = rule.get(key);
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(String::valueOf).toList();
    }

    private record RequestAttributes(String verb, String resource, String resourceName) {

        private static RequestAttributes from(String method, String path) {
            String[] parts = Arrays.stream(path.split("/"))
                .filter(part -> !part.isBlank())
                .toArray(String[]::new);
            String resource = parts[0];
            String name = parts.length >= 2 ? parts[1] : "";
            if (parts.length >= 3) {
                resource += "/" + parts[2];
            }
            if (parts.length >= 4) {
                name += "/" + parts[3];
            }
            String verb = Objects.equals(method, "POST") ? "create" : "get";
            return new RequestAttributes(verb, resource, name);
        }
    }
}
