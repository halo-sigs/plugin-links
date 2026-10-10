package run.halo.links.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.plugin.extensionpoint.ExtensionGetter;
import run.halo.links.McpConfiguration;
import run.halo.links.rss.LinkFeedItemStore;
import run.halo.links.rss.LinkFeedService;
import run.halo.links.service.LinkApplicationApprovalService;
import run.halo.links.service.LinkGroupService;
import run.halo.links.verification.LinkVerificationService;
import run.halo.mcpserver.api.McpToolProvider;

class McpConfigurationTest {

    @Test
    void startsWithoutMcpApiAndDoesNotRegisterProviders() {
        new ApplicationContextRunner()
            .withClassLoader(new FilteredClassLoader("run.halo.mcpserver"))
            .withUserConfiguration(McpConfiguration.class)
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).doesNotHaveBean("linkToolProvider");
                assertThat(context).doesNotHaveBean("linkApplicationToolProvider");
                assertThat(context).doesNotHaveBean("linkFeedToolProvider");
            });
    }

    @Test
    void registersAllTwentySixDistinctToolsWhenApiIsPresent() {
        new ApplicationContextRunner()
            .withUserConfiguration(McpConfiguration.class)
            .withBean(ReactiveExtensionClient.class, () -> mock(ReactiveExtensionClient.class))
            .withBean(LinkGroupService.class, () -> mock(LinkGroupService.class))
            .withBean(LinkVerificationService.class, () -> mock(LinkVerificationService.class))
            .withBean(LinkApplicationApprovalService.class,
                () -> mock(LinkApplicationApprovalService.class))
            .withBean(ExtensionGetter.class, () -> mock(ExtensionGetter.class))
            .withBean(LinkFeedService.class, () -> mock(LinkFeedService.class))
            .withBean(LinkFeedItemStore.class, () -> mock(LinkFeedItemStore.class))
            .run(context -> {
                assertThat(context).hasNotFailed();
                var tools = context.getBeansOfType(McpToolProvider.class).values().stream()
                    .flatMap(provider -> provider.tools().collectList().block().stream()).toList();
                assertThat(tools).extracting(tool -> tool.name()).containsExactlyInAnyOrder(
                    "list_links", "get_link", "create_link", "update_link", "delete_link",
                    "move_links", "sort_links", "list_link_groups", "create_link_group",
                    "update_link_group", "delete_link_group", "sort_link_groups",
                    "fetch_site_metadata", "discover_feeds", "check_links",
                    "list_link_applications", "get_link_application", "verify_link_application",
                    "approve_link_application", "reject_link_application",
                    "delete_link_application",
                    "list_feed_items", "get_feed_summary", "refresh_link_feed",
                    "set_feed_item_state", "mark_feed_items_read");
                tools.forEach(tool -> {
                    assertThat(tool.inputSchema()).containsEntry("type", "object")
                        .containsEntry("additionalProperties", false);
                    assertThat(tool.displayTitle()).containsPattern("[\\p{IsHan}]");
                    assertThat(tool.displayDescription()).containsPattern("[\\p{IsHan}]")
                        .isNotEqualTo(tool.description());
                    assertThat(tool.outputSchema()).containsEntry("type", "object")
                        .containsKey("properties").containsKey("required")
                        .containsEntry("additionalProperties", false);
                    assertThat((java.util.Map<?, ?>) tool.outputSchema().get("properties"))
                        .isNotEmpty();
                    McpSchemaAssertions.assertSchema(tool);
                    assertThat(tool.permission()).isNotNull();
                    assertThat(tool.handler()).isNotNull();
                });
                assertThat(tools.stream().collect(java.util.stream.Collectors.groupingBy(
                    tool -> tool.category(), java.util.stream.Collectors.counting())))
                    .containsExactlyInAnyOrderEntriesOf(java.util.Map.of(
                        "友链申请", 6L, "友链订阅", 6L, "友链管理", 14L));
                var byName = tools.stream().collect(java.util.stream.Collectors.toMap(
                    tool -> tool.name(), tool -> tool));
                assertThat(byName.get("fetch_site_metadata").annotations().openWorldHint())
                    .isTrue();
                assertThat(byName.get("fetch_site_metadata").annotations().readOnlyHint()).isTrue();
                assertThat(byName.get("check_links").annotations().readOnlyHint()).isFalse();
                assertThat(byName.get("approve_link_application").annotations().openWorldHint())
                    .isTrue();
                assertThat(byName.get("delete_link").annotations().destructiveHint()).isTrue();
            });
    }

    @Test
    void providersCannotBeRegisteredByUnconditionalComponentScanning() {
        for (var type : List.of(LinkToolProvider.class, LinkApplicationToolProvider.class,
            LinkFeedToolProvider.class)) {
            assertThat(AnnotatedElementUtils.hasAnnotation(type, Component.class)).isFalse();
        }
    }
}
