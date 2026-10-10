package run.halo.links.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.links.extension.Link;
import run.halo.links.rss.LinkFeedDiscoveryResult;
import run.halo.links.rss.LinkFeedHiddenStateResult;
import run.halo.links.rss.LinkFeedItem;
import run.halo.links.rss.LinkFeedItemPage;
import run.halo.links.rss.LinkFeedItemQuery;
import run.halo.links.rss.LinkFeedItemStore;
import run.halo.links.rss.LinkFeedItemSummary;
import run.halo.links.rss.LinkFeedRefreshResult;
import run.halo.links.rss.LinkFeedService;
import run.halo.links.rss.LinkFeedStorageUnavailableException;
import run.halo.mcpserver.api.McpToolDefinition;
import run.halo.mcpserver.api.McpToolInvocation;
import run.halo.mcpserver.api.McpToolResult;

class LinkFeedToolProviderTest {

    private final LinkFeedService service = mock(LinkFeedService.class);
    private final LinkFeedItemStore store = mock(LinkFeedItemStore.class);
    private final ReactiveExtensionClient client = mock(ReactiveExtensionClient.class);
    private final LinkFeedToolProvider provider = new LinkFeedToolProvider(service, store, client);

    @Test
    void shouldExposeOnlySixToolsWithAccurateAnnotations() {
        var tools = provider.tools().collectList().block();
        assertThat(tools).extracting(McpToolDefinition::name).containsExactly(
            "discover_feeds", "list_feed_items", "get_feed_summary", "refresh_link_feed",
            "set_feed_item_state", "mark_feed_items_read");
        assertThat(LinkFeedToolProvider.class.isAnnotationPresent(Component.class)).isFalse();
        var discovery = tool("discover_feeds").annotations();
        assertThat(discovery.readOnlyHint()).isTrue();
        assertThat(discovery.openWorldHint()).isTrue();
        assertThat(discovery.destructiveHint()).isFalse();
        assertThat(discovery.idempotentHint()).isTrue();
        assertThat(tool("list_feed_items").annotations().readOnlyHint()).isTrue();
        assertThat(tool("get_feed_summary").annotations().readOnlyHint()).isTrue();
        assertThat(tool("refresh_link_feed").annotations().openWorldHint()).isTrue();
        assertThat(tool("set_feed_item_state").annotations().readOnlyHint()).isFalse();
        assertThat(tool("mark_feed_items_read").annotations().destructiveHint()).isTrue();
    }

    @Test
    void shouldDelegateDiscoveryAndRefresh() {
        when(service.discover("https://example.com")).thenReturn(Mono.just(
            new LinkFeedDiscoveryResult(List.of("https://example.com/feed.xml"))));
        var refreshed = new LinkFeedRefreshResult();
        refreshed.setLinkName("link-a");
        refreshed.setFetchedItemCount(2);
        refreshed.setFetchedAt(Instant.parse("2026-10-10T00:00:00Z"));
        var feed = new LinkFeedRefreshResult.FeedResult();
        feed.setUrl("https://example.com/feed.xml");
        feed.setFetchedAt(refreshed.getFetchedAt());
        feed.setFetchedItemCount(2);
        refreshed.setFeeds(List.of(feed));
        when(service.refresh("link-a")).thenReturn(Mono.just(refreshed));

        assertThat(call("discover_feeds", Map.of("url", "https://example.com"))
            .structuredContent())
            .containsEntry("feedUrls", List.of("https://example.com/feed.xml"));
        assertThat(call("refresh_link_feed", Map.of("linkName", "link-a"))
            .structuredContent()).containsEntry("linkName", "link-a")
            .containsEntry("fetchedItemCount", 2);
        verify(service).discover("https://example.com");
        verify(service).refresh("link-a");
        verifyNoInteractions(store, client);
    }

    @Test
    void shouldPreserveRefreshErrorsAsToolErrors() {
        when(service.refresh("disabled")).thenReturn(Mono.error(new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "RSS is not enabled for this link.")));

        assertError(call("refresh_link_feed", Map.of("linkName", "disabled")), "INVALID_ARGUMENT");
    }

    @Test
    void shouldPassFiltersAndCursorAndBoundItemOutputOffTheCallingThread() {
        var thread = new AtomicReference<String>();
        var item = item("item-a", "2026-01-01T00:00:00Z");
        item.setSummary("x".repeat(2000));
        item.setTitle("t".repeat(600));
        item.setAuthor("a".repeat(400));
        item.setGuid("internal-guid");
        item.setContentHash("internal-hash");
        when(service.listItems(any())).thenAnswer(invocation -> {
            thread.set(Thread.currentThread().getName());
            return new LinkFeedItemPage(List.of(item), item.getPublishedAt().toString(),
                item.getId(), true);
        });

        var response = call("list_feed_items", Map.of("linkName", "link-a", "read", false,
            "favorite", true, "readLater", true, "hidden", true, "limit", 2,
            "beforePublishedAt", "2026-01-02T00:00:00Z", "beforeId", "item-z"));

        assertThat(response.error()).isFalse();
        assertThat(response.structuredContent()).containsEntry("hasNext", true)
            .containsEntry("nextBeforePublishedAt", "2026-01-01T00:00:00Z")
            .containsEntry("nextBeforeId", "item-a");
        var items = (List<?>) response.structuredContent().get("items");
        assertThat(items).hasSize(1);
        var view = (Map<?, ?>) items.getFirst();
        assertThat(view.keySet().stream().map(Object::toString).toList())
            .doesNotContain("contentHash", "guid", "feedUrl");
        assertThat((String) view.get("summary")).hasSize(500);
        assertThat((String) view.get("title")).hasSize(300);
        assertThat((String) view.get("author")).hasSize(200);
        var captor = ArgumentCaptor.forClass(LinkFeedItemQuery.class);
        verify(service).listItems(captor.capture());
        var query = captor.getValue();
        assertThat(query.getLinkName()).isEqualTo("link-a");
        assertThat(query.getRead()).isFalse();
        assertThat(query.getFavorite()).isTrue();
        assertThat(query.getReadLater()).isTrue();
        assertThat(query.getHidden()).isTrue();
        assertThat(query.getLimit()).isEqualTo(2);
        assertThat(query.getBeforePublishedAt()).isEqualTo(Instant.parse("2026-01-02T00:00:00Z"));
        assertThat(query.getBeforeId()).isEqualTo("item-z");
        assertThat(thread.get()).startsWith("boundedElastic-");
    }

    @Test
    void shouldUseDefaultLimitAndExcludeHiddenItems() {
        when(service.listItems(any()))
            .thenReturn(new LinkFeedItemPage(List.of(), null, null, false));

        var response = call("list_feed_items", Map.of());

        assertThat(response.structuredContent()).containsEntry("hasNext", false)
            .containsEntry("nextBeforeId", null).containsEntry("nextBeforePublishedAt", null);
        var captor = ArgumentCaptor.forClass(LinkFeedItemQuery.class);
        verify(service).listItems(captor.capture());
        assertThat(captor.getValue().getLimit()).isEqualTo(30);
        assertThat(captor.getValue().getHidden()).isFalse();
        assertThat(captor.getValue().getRead()).isNull();
        assertThat(captor.getValue().getFavorite()).isNull();
        assertThat(captor.getValue().getReadLater()).isNull();
    }

    @ParameterizedTest
    @MethodSource("invalidListArguments")
    void shouldRejectInvalidListArgumentsBeforeQuerying(Map<String, Object> args) {
        assertError(call("list_feed_items", args), "INVALID_ARGUMENT");
        verifyNoInteractions(service, store, client);
    }

    static List<Map<String, Object>> invalidListArguments() {
        return List.of(Map.of("linkName", "a", "groupName", "g"),
            Map.of("beforeId", "id"), Map.of("beforePublishedAt", "2026-01-01T00:00:00Z"),
            Map.of("beforeId", "id", "beforePublishedAt", "not-a-date"),
            Map.of("beforeId", " ", "beforePublishedAt", "2026-01-01T00:00:00Z"),
            Map.of("limit", 0), Map.of("limit", 101), Map.of("limit", 1.5),
            Map.of("limit", "10"), Map.of("read", "false"), Map.of("hidden", "true"),
            Map.of("groupName", " "), Map.of("linkName", 1));
    }

    @Test
    void shouldMergeGroupPagesUsingCurrentMembershipAndStableOrdering() {
        when(client.listAll(eq(Link.class), any(ListOptions.class), eq(Sort.unsorted())))
            .thenReturn(Flux.just(link("link-a"), link("link-b")));
        var thread = new AtomicReference<String>();
        when(store.listRecent(any())).thenAnswer(invocation -> {
            thread.set(Thread.currentThread().getName());
            LinkFeedItemQuery query = invocation.getArgument(0);
            assertThat(query.getLimit()).isEqualTo(3);
            assertThat(query.getHidden()).isFalse();
            assertThat(query.getRead()).isFalse();
            assertThat(query.getBeforeId()).isEqualTo("z");
            assertThat(query.getBeforePublishedAt())
                .isEqualTo(Instant.parse("2026-01-03T00:00:00Z"));
            return "link-a".equals(query.getLinkName())
                ? List.of(item("a", "2026-01-02T00:00:00Z"),
                    item("c", "2026-01-01T00:00:00Z"))
                : List.of(item("b", "2026-01-02T00:00:00Z"));
        });

        var response = call("list_feed_items", Map.of("groupName", "group-a", "limit", 2,
            "read", false, "beforeId", "z", "beforePublishedAt", "2026-01-03T00:00:00Z"));

        assertThat(response.error()).isFalse();
        var items = (List<?>) response.structuredContent().get("items");
        assertThat(items).extracting(value -> (String) ((Map<?, ?>) value).get("id"))
            .containsExactly("b", "a");
        assertThat(response.structuredContent()).containsEntry("hasNext", true)
            .containsEntry("nextBeforeId", "a");
        assertThat(thread.get()).startsWith("boundedElastic-");
        verifyNoInteractions(service);
    }

    @Test
    void shouldReturnEmptyPageForEmptyGroupWithoutQueryingStore() {
        when(client.listAll(eq(Link.class), any(ListOptions.class), any(Sort.class)))
            .thenReturn(Flux.empty());

        assertThat(call("list_feed_items", Map.of("groupName", "empty"))
            .structuredContent()).containsEntry("items", List.of()).containsEntry("hasNext", false);
        verifyNoInteractions(store, service);
    }

    @Test
    void shouldCombineExactSummaryCountsOnBoundedElastic() {
        when(store.countSummary()).thenAnswer(invocation -> {
            assertThat(Thread.currentThread().getName()).startsWith("boundedElastic-");
            return new LinkFeedItemSummary(7, 5, 4);
        });
        when(store.countUnread()).thenReturn(3L);
        when(store.countUnreadByLinkName()).thenReturn(Map.of("link-a", 3L));

        assertThat(call("get_feed_summary", Map.of()).structuredContent())
            .containsEntry("hiddenCount", 7L).containsEntry("favoriteCount", 5L)
            .containsEntry("readLaterCount", 4L).containsEntry("unreadCount", 3L)
            .containsEntry("unreadByLinkName", Map.of("link-a", 3L));
    }

    @Test
    void shouldUpdateOnlyExplicitStateIncludingFalse() {
        when(store.updateFavorite("item-a", false)).thenAnswer(invocation -> {
            assertThat(Thread.currentThread().getName()).startsWith("boundedElastic-");
            return true;
        });

        var response = call("set_feed_item_state", Map.of("id", "item-a", "favorite", false));

        assertThat(response.error()).isFalse();
        assertThat(response.structuredContent()).containsEntry("states", Map.of("favorite", false));
        verify(store).updateFavorite("item-a", false);
        verifyNoMoreInteractions(store);
    }

    @Test
    void shouldValidateAllStatesBeforeAnyMutation() {
        assertError(call("set_feed_item_state", Map.of("id", "item-a")), "INVALID_ARGUMENT");
        assertError(call("set_feed_item_state", Map.of("id", "item-a", "read", true,
            "favorite", "false")), "INVALID_ARGUMENT");
        assertError(call("set_feed_item_state", Map.of("id", "item-a", "hidden", 1)),
            "INVALID_ARGUMENT");
        verifyNoInteractions(store);
    }

    @Test
    void shouldUpdateSeveralExplicitStatesWithoutChangingOthers() {
        when(store.updateRead("item-a", true)).thenReturn(true);
        when(store.updateReadLater("item-a", false)).thenReturn(true);
        when(store.updateHidden(List.of("item-a"), true))
            .thenReturn(new LinkFeedHiddenStateResult(1, 1));

        var response = call("set_feed_item_state", Map.of("id", "item-a", "read", true,
            "readLater", false, "hidden", true));

        assertThat(response.error()).isFalse();
        assertThat(response.structuredContent()).containsEntry("hiddenUpdatedCount", 1L);
        verify(store).updateRead("item-a", true);
        verify(store).updateReadLater("item-a", false);
        verify(store).updateHidden(List.of("item-a"), true);
        verifyNoMoreInteractions(store);
    }

    @Test
    void shouldReportMissingItemForReadStateAndPreserveHiddenNoOpSemantics() {
        when(store.updateRead("missing", true)).thenReturn(false);
        when(store.updateHidden(List.of("missing"), true))
            .thenReturn(new LinkFeedHiddenStateResult(1, 0));

        assertError(call("set_feed_item_state", Map.of("id", "missing", "read", true)),
            "NOT_FOUND");
        var response = call("set_feed_item_state", Map.of("id", "missing", "hidden", true));
        assertThat(response.error()).isFalse();
        assertThat(response.structuredContent()).containsEntry("hiddenUpdatedCount", 0L);
    }

    @ParameterizedTest
    @MethodSource("invalidMarkReadArguments")
    void shouldRequireExplicitMutuallyExclusiveMarkReadScope(Map<String, Object> args) {
        assertError(call("mark_feed_items_read", args), "INVALID_ARGUMENT");
        verifyNoInteractions(store);
    }

    static List<Map<String, Object>> invalidMarkReadArguments() {
        return List.of(Map.of(), Map.of("all", false), Map.of("all", "true"),
            Map.of("linkName", "a", "all", true), Map.of("linkName", "a", "all", false),
            Map.of("linkName", " "), Map.of("linkName", " ", "all", true));
    }

    @Test
    void shouldMarkOnlyRequestedScopeReadOnBoundedElastic() {
        when(store.markUnreadAsRead("link-a")).thenAnswer(invocation -> {
            assertThat(Thread.currentThread().getName()).startsWith("boundedElastic-");
            return 2L;
        });
        when(store.markUnreadAsRead(null)).thenReturn(5L);

        assertThat(call("mark_feed_items_read", Map.of("linkName", "link-a"))
            .structuredContent()).containsEntry("updatedCount", 2L);
        assertThat(call("mark_feed_items_read", Map.of("all", true))
            .structuredContent()).containsEntry("updatedCount", 5L);
        verify(store).markUnreadAsRead("link-a");
        verify(store).markUnreadAsRead(null);
    }

    @Test
    void shouldReturnStorageUnavailableAsToolError() {
        when(store.countSummary()).thenThrow(new LinkFeedStorageUnavailableException("offline"));

        assertError(call("get_feed_summary", Map.of()), "UNAVAILABLE");
    }

    private McpToolDefinition tool(String name) {
        return provider.tools().filter(definition -> definition.name().equals(name))
            .single().block(Duration.ofSeconds(5));
    }

    private McpToolResult call(String name, Map<String, Object> args) {
        var definition = tool(name);
        return McpSchemaAssertions.assertOutput(definition,
            definition.handler().execute(new McpToolInvocation(name, args))
                .block(Duration.ofSeconds(5)));
    }

    private static void assertError(McpToolResult response, String code) {
        assertThat(response.error()).isTrue();
        assertThat(((Map<?, ?>) response.structuredContent().get("error")).get("code"))
            .isEqualTo(code);
    }

    private static LinkFeedItem item(String id, String publishedAt) {
        var item = new LinkFeedItem();
        item.setId(id);
        item.setLinkName("link-a");
        item.setPublishedAt(Instant.parse(publishedAt));
        item.setUrl("https://example.com/" + id);
        return item;
    }

    private static Link link(String name) {
        var link = new Link();
        var metadata = new Metadata();
        metadata.setName(name);
        link.setMetadata(metadata);
        return link;
    }
}
