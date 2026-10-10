package run.halo.links.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.links.extension.Link;
import run.halo.links.extension.LinkGroup;
import run.halo.links.service.LinkGroupService;
import run.halo.links.verification.LinkVerificationRequest;
import run.halo.links.verification.LinkVerificationService;
import run.halo.links.verification.LinkVerificationTriggerResult;
import run.halo.mcpserver.api.McpToolInvocation;
import run.halo.mcpserver.api.McpToolResult;

class LinkToolProviderTest {

    private final ReactiveExtensionClient client = mock(ReactiveExtensionClient.class);
    private final LinkGroupService groups = mock(LinkGroupService.class);
    private final LinkVerificationService verification = mock(LinkVerificationService.class);
    private final LinkToolProvider provider = new LinkToolProvider(client, groups, verification);

    @Test
    void rejectsImplicitGlobalChecksAndAmbiguousScopes() {
        for (var args : List.of(Map.<String, Object>of(),
            Map.<String, Object>of("names", List.of()),
            Map.<String, Object>of("all", true, "groupName", "group-a"),
            Map.<String, Object>of("names", List.of(), "all", true))) {
            assertThat(call("check_links", args).error()).isTrue();
        }
        verify(verification, never()).verify(any(LinkVerificationRequest.class));
    }

    @Test
    void acceptsExplicitGlobalCheckWithoutClaimingCompletion() {
        when(verification.verify(any(LinkVerificationRequest.class)))
            .thenReturn(Mono.just(new LinkVerificationTriggerResult()));
        var result = call("check_links", Map.of("all", true));
        assertThat(result.error()).isFalse();
        assertThat(result.structuredContent()).containsEntry("state", "accepted");
    }

    @Test
    void updatePreservesUnspecifiedFieldsAndCanUngroup() {
        var link = link("one", "group-a");
        link.getSpec().setLogo("https://example.com/logo.png");
        when(client.fetch(Link.class, "one")).thenReturn(Mono.just(link));
        when(client.update(any(Link.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        var result = call("update_link", Map.of("name", "one", "description", "", "groupName", ""));
        assertThat(result.error()).isFalse();
        var captor = ArgumentCaptor.forClass(Link.class);
        verify(client).update(captor.capture());
        assertThat(captor.getValue().getSpec().getGroupName()).isNull();
        assertThat(captor.getValue().getSpec().getDescription()).isEmpty();
        assertThat(captor.getValue().getSpec().getLogo()).isEqualTo("https://example.com/logo.png");
        assertThat(captor.getValue().getSpec().getUrl()).isEqualTo("https://example.com/one");
        verify(verification, never()).verify(any(LinkVerificationRequest.class));
    }

    @Test
    void updatingDescriptionDoesNotValidateAnUnchangedMissingGroup() {
        when(client.fetch(Link.class, "one")).thenReturn(Mono.just(link("one", "missing")));
        when(client.update(any(Link.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        assertThat(call("update_link", Map.of("name", "one", "description", "updated"))
            .error()).isFalse();
        verify(client, never()).fetch(eq(LinkGroup.class), any());
    }

    @Test
    void rejectsInvalidUpdateBeforeWriting() {
        when(client.fetch(Link.class, "one")).thenReturn(Mono.just(link("one", null)));
        assertThat(call("update_link", Map.of("name", "one", "rssEnabled", true)).error()).isTrue();
        assertThat(call("update_link", Map.of("name", "one", "url", "file:///tmp/data"))
            .error()).isTrue();
        assertThat(call("update_link", Map.of("name", "one", "priority", 1.5)).error()).isTrue();
        verify(client, never()).update(any(Link.class));
    }

    @Test
    void missingGroupPreventsCreate() {
        when(client.fetch(LinkGroup.class, "missing")).thenReturn(Mono.empty());
        var result = call("create_link", Map.of("url", "https://example.com", "displayName", "Site",
            "groupName", "missing", "priority", 1));
        assertThat(result.error()).isTrue();
        verify(client, never()).create(any(Link.class));
    }

    @Test
    void ungroupedAndVerificationFiltersApplyBeforePagination() {
        var healthy = link("healthy", null);
        healthy.getStatus().setVerification(new Link.VerificationStatus());
        healthy.getStatus().getVerification().setAccess(new Link.AccessStatus());
        healthy.getStatus().getVerification().getAccess().setState(Link.AccessState.ACCESSIBLE);
        var orphan = link("orphan", "deleted-group");
        var ungrouped = link("ungrouped", null);
        var grouped = link("grouped", "existing");
        var group = new LinkGroup();
        group.setMetadata(new Metadata());
        group.getMetadata().setName("existing");
        when(client.listAll(eq(LinkGroup.class), any(), any())).thenReturn(Flux.just(group));
        when(client.listAll(eq(Link.class), any(), any()))
            .thenReturn(Flux.just(healthy, orphan, ungrouped, grouped));
        var result = call("list_links", Map.of("ungrouped", true, "accessState", "UNKNOWN",
            "page", 2, "size", 1));
        assertThat(result.error()).isFalse();
        assertThat(((Number) result.structuredContent().get("total")).intValue()).isEqualTo(2);
        assertThat(result.structuredContent()).containsEntry("hasNext", false);
        var items = (List<?>) result.structuredContent().get("items");
        assertThat(((Map<?, ?>) items.getFirst()).get("name")).isEqualTo("ungrouped");
    }

    @Test
    void moveReportsPartialFailures() {
        when(client.fetch(Link.class, "one")).thenReturn(Mono.just(link("one", "group-a")));
        when(client.fetch(Link.class, "missing")).thenReturn(Mono.empty());
        when(client.update(any(Link.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        var result = call("move_links",
            Map.of("names", List.of("one", "missing"), "groupName", ""));
        assertThat(result.error()).isFalse();
        assertThat(((Number) result.structuredContent().get("succeeded")).intValue()).isEqualTo(1);
        assertThat(((Number) result.structuredContent().get("failed")).intValue()).isEqualTo(1);
    }

    @Test
    void deletionDefaultsToKeepingMemberLinks() {
        var group = new LinkGroup();
        group.setMetadata(new Metadata());
        group.getMetadata().setName("group-a");
        when(groups.deleteLinkGroup("group-a", false)).thenReturn(Mono.just(group));
        assertThat(call("delete_link_group", Map.of("name", "group-a")).error()).isFalse();
        verify(groups).deleteLinkGroup("group-a", false);
    }

    @Test
    void sortingRejectsDuplicateNames() {
        assertThat(call("sort_links", Map.of("names", List.of("one", "one"))).error()).isTrue();
        verify(client, never()).update(any(Link.class));
    }

    @Test
    void resultContainsJsonValuesAndVerificationTimestamp() {
        var link = link("one", null);
        link.getStatus().setVerification(new Link.VerificationStatus());
        link.getStatus().getVerification().setLastCheckedAt(Instant.parse("2026-10-10T00:00:00Z"));
        when(client.fetch(Link.class, "one")).thenReturn(Mono.just(link));
        var result = call("get_link", Map.of("name", "one"));
        assertThat(result.error()).isFalse();
        assertThat(result.structuredContent().get("verification")).isInstanceOf(Map.class);
    }

    @Test
    void validatesSuccessfulCrudAndSortOutputs() {
        var link = link("one", null);
        var group = new LinkGroup();
        group.setMetadata(new Metadata());
        group.getMetadata().setName("group-a");
        group.setSpec(new LinkGroup.LinkGroupSpec());
        group.getSpec().setDisplayName("Group A");
        when(client.fetch(Link.class, "one")).thenReturn(Mono.just(link));
        when(client.fetch(LinkGroup.class, "group-a")).thenReturn(Mono.just(group));
        when(client.create(any(Link.class))).thenReturn(Mono.just(link));
        when(client.create(any(LinkGroup.class))).thenReturn(Mono.just(group));
        when(client.update(any(Link.class))).thenReturn(Mono.just(link));
        when(client.update(any(LinkGroup.class))).thenReturn(Mono.just(group));
        when(client.delete(any(Link.class))).thenReturn(Mono.just(link));
        when(client.listBy(eq(LinkGroup.class), any(), any()))
            .thenReturn(Mono.just(new run.halo.app.extension.ListResult<>(1, 20, 1,
                List.of(group))));
        when(client.listBy(eq(Link.class), any(), any()))
            .thenReturn(Mono.just(new run.halo.app.extension.ListResult<>(1, 20, 1,
                List.of(link))));

        assertThat(call("create_link", Map.of("url", "https://example.com/one",
            "displayName", "One", "priority", 1)).error()).isFalse();
        assertThat(call("create_link_group", Map.of("displayName", "Group A", "priority", 1))
            .error()).isFalse();
        assertThat(call("update_link_group", Map.of("name", "group-a", "displayName", "Group B"))
            .error()).isFalse();
        assertThat(call("sort_links", Map.of("names", List.of("one"))).error()).isFalse();
        assertThat(call("sort_link_groups", Map.of("names", List.of("group-a"))).error()).isFalse();
        assertThat(call("list_links", Map.of()).error()).isFalse();
        assertThat(call("list_link_groups", Map.of()).error()).isFalse();
        assertThat(call("delete_link", Map.of("name", "one")).error()).isFalse();
    }

    @Test
    void validatesSiteMetadataWithMissingImages() {
        var definition = provider.tools()
            .filter(value -> value.name().equals("fetch_site_metadata")).blockFirst();
        var metadata = new run.halo.links.dto.LinkDetailDTO();
        metadata.setTitle("Example");
        metadata.setDescription("");
        McpSchemaAssertions.assertValid(definition, McpSupport.result(metadata));
    }

    private McpToolResult call(String name, Map<String, Object> args) {
        var tool = provider.tools().filter(value -> value.name().equals(name)).blockFirst();
        return McpSchemaAssertions.assertOutput(tool,
            tool.handler().execute(new McpToolInvocation(name, args)).block());
    }

    private static Link link(String name, String group) {
        var link = new Link();
        link.setMetadata(new Metadata());
        link.getMetadata().setName(name);
        link.setSpec(new Link.LinkSpec());
        link.getSpec().setDisplayName(name);
        link.getSpec().setUrl("https://example.com/" + name);
        link.getSpec().setGroupName(group);
        return link;
    }
}
