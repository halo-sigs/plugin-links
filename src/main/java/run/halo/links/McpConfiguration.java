package run.halo.links;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.plugin.extensionpoint.ExtensionGetter;
import run.halo.links.mcp.LinkApplicationToolProvider;
import run.halo.links.mcp.LinkFeedToolProvider;
import run.halo.links.mcp.LinkToolProvider;
import run.halo.links.rss.LinkFeedItemStore;
import run.halo.links.rss.LinkFeedService;
import run.halo.links.service.LinkApplicationApprovalService;
import run.halo.links.service.LinkGroupService;
import run.halo.links.verification.LinkVerificationService;

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = "run.halo.mcpserver.api.McpToolProvider")
public class McpConfiguration {

    @Bean
    LinkToolProvider linkToolProvider(ReactiveExtensionClient client,
        LinkGroupService groups, LinkVerificationService verification) {
        return new LinkToolProvider(client, groups, verification);
    }

    @Bean
    LinkApplicationToolProvider linkApplicationToolProvider(ReactiveExtensionClient client,
        LinkApplicationApprovalService approvals, LinkVerificationService verification,
        ExtensionGetter extensions) {
        return new LinkApplicationToolProvider(client, approvals, verification, extensions);
    }

    @Bean
    LinkFeedToolProvider linkFeedToolProvider(LinkFeedService feeds, LinkFeedItemStore items,
        ReactiveExtensionClient client) {
        return new LinkFeedToolProvider(feeds, items, client);
    }
}
