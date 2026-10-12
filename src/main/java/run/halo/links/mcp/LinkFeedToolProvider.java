package run.halo.links.mcp;

import static run.halo.app.extension.index.query.Queries.equal;
import static run.halo.links.mcp.McpSupport.bool;
import static run.halo.links.mcp.McpSupport.error;
import static run.halo.links.mcp.McpSupport.integer;
import static run.halo.links.mcp.McpSupport.object;
import static run.halo.links.mcp.McpSupport.readOnly;
import static run.halo.links.mcp.McpSupport.requiredString;
import static run.halo.links.mcp.McpSupport.schema;
import static run.halo.links.mcp.McpSupport.string;
import static run.halo.links.mcp.McpSupport.tool;
import static run.halo.links.mcp.McpSupport.write;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.links.extension.Link;
import run.halo.links.rss.LinkFeedItem;
import run.halo.links.rss.LinkFeedItemPage;
import run.halo.links.rss.LinkFeedItemQuery;
import run.halo.links.rss.LinkFeedItemStore;
import run.halo.links.rss.LinkFeedService;
import run.halo.links.service.LinkUrlCanonicalizer;
import run.halo.mcpserver.api.McpToolAnnotations;
import run.halo.mcpserver.api.McpToolDefinition;
import run.halo.mcpserver.api.McpToolProvider;

@RequiredArgsConstructor
public class LinkFeedToolProvider implements McpToolProvider {

    private final LinkFeedService feedService;
    private final LinkFeedItemStore itemStore;
    private final ReactiveExtensionClient client;

    @Override
    public Flux<McpToolDefinition> tools() {
        return Flux.just(
            tool("discover_feeds", "发现订阅源",
                "Discover RSS or Atom feed URLs from an HTTP(S) website. Fetches remote content "
                    + "without changing subscriptions.",
                "从 HTTP(S) 网站发现 RSS 或 Atom 订阅地址。会获取远程内容，不会修改订阅配置。",
                object("url", schema("string", "Absolute HTTP(S) website URL.")),
                List.of("url"), new McpToolAnnotations(true, false, true, true, "Discover feeds"),
                args -> {
                    var url = requiredString(args, "url");
                    if (LinkUrlCanonicalizer.canonicalKey(url).isEmpty()) {
                        throw error("INVALID_ARGUMENT", "Expected an absolute HTTP(S) URL.");
                    }
                    return feedService.discover(url).map(McpSupport::result);
                }),
            tool("list_feed_items", "查询订阅文章",
                "List cached feed items, newest first. Hidden items are excluded by default. "
                    + "Returns bounded summaries, not full article content. For the next page, "
                    + "pass both nextBeforePublishedAt and nextBeforeId as beforePublishedAt "
                    + "and beforeId, retaining the same filters; stop when hasNext is false.",
                "按时间从新到旧查询缓存的订阅文章，默认排除已隐藏文章，仅返回长度受限的摘要，不包含全文。"
                    + "获取下一页时，保持筛选条件不变，将 nextBeforePublishedAt 和 nextBeforeId"
                    + " 分别作为 beforePublishedAt 和 beforeId 同时传入；hasNext 为 false 时停止翻页。",
                listProperties(), List.of(), readOnly("List feed items"), this::listItems),
            tool("get_feed_summary", "查看订阅统计",
                "Return exact global hidden, visible favorite, visible read-later and visible "
                    + "unread counts, including unread counts by link.",
                "返回全局已隐藏文章，以及可见文章中已收藏、稍后阅读和未读文章的准确数量，"
                    + "同时提供按友链统计的未读数量。",
                Map.of(), List.of(), readOnly("Get feed summary"), args -> summary()),
            tool("refresh_link_feed", "刷新友链订阅",
                "Fetch configured feeds for one RSS-enabled link and update its cached items "
                    + "and RSS status. Existing refresh coordination and retention rules apply.",
                "获取指定友链配置的订阅内容，并更新缓存文章和 RSS 状态。友链须已启用 RSS，"
                    + "刷新遵循现有的并发协调和数据保留规则。",
                object("linkName", schema("string", "Link metadata name.")),
                List.of("linkName"), write("Refresh link feed", true, true),
                args -> feedService.refresh(requiredString(args, "linkName"))
                    .map(McpSupport::result)),
            tool("set_feed_item_state", "设置订阅文章状态",
                "Set explicitly supplied states of one cached feed item. Omitted states remain "
                    + "unchanged; provide at least one state. Each supplied field is updated "
                    + "independently. Hidden updates ignore missing IDs and report the number "
                    + "of changed items; other states report not_found for a missing item.",
                "更新一篇缓存订阅文章中明确提供的状态，至少提供一项，未提供的状态保持不变。"
                    + "每个字段独立更新。更新隐藏状态时忽略不存在的文章 ID，并返回实际变更数量；"
                    + "更新其他状态时，文章不存在则返回 not_found。",
                object("id", schema("string", "Cached feed item ID."),
                    "read", schema("boolean", "Read state to apply."),
                    "favorite", schema("boolean", "Favorite state to apply."),
                    "readLater", schema("boolean", "Read-later state to apply."),
                    "hidden", schema("boolean", "Hidden state to apply.")),
                List.of("id"), write("Set feed item state", true, false), this::setItemState),
            tool("mark_feed_items_read", "将订阅文章标为已读",
                "Mark visible unread cached items as read. Specify linkName for one link, "
                    + "or explicitly set all=true for all links; these scopes are mutually "
                    + "exclusive. Hidden items remain unchanged.",
                "将可见的未读缓存文章标为已读。指定 linkName 处理单个友链，"
                    + "或明确设置 all=true 处理全部友链，两者不能同时使用。已隐藏文章保持不变。",
                object("linkName", schema("string", "Limit updates to this link metadata name."),
                    "all", schema("boolean", "Explicitly authorize updating all links.")),
                List.of(), write("Mark feed items read", true, false), this::markItemsRead)
        );
    }

    private static Map<String, Object> listProperties() {
        return object(
            "linkName", schema("string", "Filter by link; cannot be combined with groupName."),
            "groupName", schema("string", "Filter by current group; excludes linkName."),
            "read", schema("boolean", "Filter by read state; omitted means either state."),
            "favorite", schema("boolean", "Filter by favorite state."),
            "readLater", schema("boolean", "Filter by read-later state."),
            "hidden", object("type", "boolean", "description", "Filter by hidden state.",
                "default", false),
            "beforePublishedAt", schema("string", "ISO-8601 instant from the previous page; "
                + "requires beforeId."),
            "beforeId", schema("string", "Stable item ID from the previous page; "
                + "requires beforePublishedAt."),
            "limit", object("type", "integer", "description", "Maximum items per page.",
                "default", LinkFeedItemQuery.DEFAULT_LIMIT, "minimum", 1,
                "maximum", LinkFeedItemQuery.MAX_LIMIT));
    }

    private Mono<Map<String, Object>> listItems(Map<String, Object> args) {
        String linkName = optionalName(args, "linkName");
        String groupName = optionalName(args, "groupName");
        if (linkName != null && groupName != null) {
            throw error("INVALID_ARGUMENT", "linkName and groupName cannot be used together.");
        }
        var query = new LinkFeedItemQuery();
        query.setLinkName(linkName);
        query.setLimit(integer(args, "limit", LinkFeedItemQuery.DEFAULT_LIMIT, 1,
            LinkFeedItemQuery.MAX_LIMIT));
        query.setRead(optionalBoolean(args, "read"));
        query.setFavorite(optionalBoolean(args, "favorite"));
        query.setReadLater(optionalBoolean(args, "readLater"));
        query.setHidden(bool(args, "hidden", false));
        String beforePublishedAt = optionalName(args, "beforePublishedAt");
        String beforeId = optionalName(args, "beforeId");
        if ((beforePublishedAt == null) != (beforeId == null)) {
            throw error("INVALID_ARGUMENT", "beforePublishedAt and beforeId must be supplied "
                + "together.");
        }
        if (beforePublishedAt != null) {
            try {
                query.setBeforePublishedAt(Instant.parse(beforePublishedAt));
            } catch (DateTimeParseException e) {
                throw error("INVALID_ARGUMENT", "beforePublishedAt must be an ISO-8601 instant.");
            }
            query.setBeforeId(beforeId);
        }
        Mono<LinkFeedItemPage> page;
        if (groupName == null) {
            page = Mono.fromCallable(() -> feedService.listItems(query))
                .subscribeOn(Schedulers.boundedElastic());
        } else {
            var options = ListOptions.builder()
                .andQuery(equal("spec.groupName", groupName))
                .build();
            page = client.listAll(Link.class, options, Sort.unsorted())
                .map(link -> link.getMetadata().getName())
                .collectList()
                .flatMap(names -> Mono.fromCallable(() -> listByLinkNames(names, query))
                    .subscribeOn(Schedulers.boundedElastic()));
        }
        return page.map(value -> object("items", value.getItems().stream()
                .map(LinkFeedToolProvider::itemView).toList(),
            "hasNext", value.isHasNext(), "nextBeforePublishedAt", value.getNextBeforePublishedAt(),
            "nextBeforeId", value.getNextBeforeId()));
    }

    private LinkFeedItemPage listByLinkNames(List<String> names, LinkFeedItemQuery query) {
        List<LinkFeedItem> items = new ArrayList<>();
        int limit = query.normalizedLimit();
        for (String name : names) {
            var linkQuery = new LinkFeedItemQuery();
            linkQuery.setLinkName(name);
            linkQuery.setBeforePublishedAt(query.getBeforePublishedAt());
            linkQuery.setBeforeId(query.getBeforeId());
            linkQuery.setRead(query.getRead());
            linkQuery.setFavorite(query.getFavorite());
            linkQuery.setReadLater(query.getReadLater());
            linkQuery.setHidden(query.getHidden());
            linkQuery.setLimit(limit + 1);
            items.addAll(itemStore.listRecent(linkQuery));
        }
        items.sort(Comparator.comparing(LinkFeedToolProvider::sortInstant,
                Comparator.nullsLast(Comparator.naturalOrder()))
            .reversed()
            .thenComparing(LinkFeedItem::getId, Comparator.nullsLast(Comparator.reverseOrder())));
        boolean hasNext = items.size() > limit;
        List<LinkFeedItem> pageItems = List.copyOf(items.subList(0, Math.min(items.size(), limit)));
        LinkFeedItem last = pageItems.isEmpty() ? null : pageItems.getLast();
        return new LinkFeedItemPage(pageItems,
            last == null ? null : instantText(last.getPublishedAt()),
            last == null ? null : last.getId(), hasNext);
    }

    private Mono<Map<String, Object>> summary() {
        return Mono.fromCallable(() -> {
            var summary = itemStore.countSummary();
            return object("hiddenCount", summary.getHiddenCount(),
                "favoriteCount", summary.getFavoriteCount(),
                "readLaterCount", summary.getReadLaterCount(),
                "unreadCount", itemStore.countUnread(),
                "unreadByLinkName", itemStore.countUnreadByLinkName());
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<Map<String, Object>> setItemState(Map<String, Object> args) {
        String id = requiredString(args, "id");
        Map<String, Boolean> states = new LinkedHashMap<>();
        for (String field : List.of("read", "favorite", "readLater", "hidden")) {
            if (args.containsKey(field)) {
                if (!(args.get(field) instanceof Boolean)) {
                    throw error("INVALID_ARGUMENT", field + " must be a boolean.");
                }
                states.put(field, bool(args, field, false));
            }
        }
        if (states.isEmpty()) {
            throw error("INVALID_ARGUMENT", "At least one state must be supplied.");
        }
        return Mono.fromCallable(() -> {
            Map<String, Object> response = object("id", id, "states", states);
            for (var state : states.entrySet()) {
                if ("hidden".equals(state.getKey())) {
                    var updated = itemStore.updateHidden(List.of(id), state.getValue());
                    response.put("hiddenUpdatedCount", updated.getUpdatedCount());
                    continue;
                }
                boolean updated = switch (state.getKey()) {
                    case "read" -> itemStore.updateRead(id, state.getValue());
                    case "favorite" -> itemStore.updateFavorite(id, state.getValue());
                    case "readLater" -> itemStore.updateReadLater(id, state.getValue());
                    default -> throw new IllegalStateException("Unexpected feed item state.");
                };
                if (!updated) {
                    throw error("NOT_FOUND", "Feed item not found: " + id);
                }
            }
            return response;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<Map<String, Object>> markItemsRead(Map<String, Object> args) {
        String linkName = optionalName(args, "linkName");
        boolean all = bool(args, "all", false);
        if (linkName != null && args.containsKey("all")) {
            throw error("INVALID_ARGUMENT", "linkName and all cannot be used together.");
        }
        if (linkName == null && !all) {
            throw error("INVALID_ARGUMENT", "Supply linkName or explicitly set all=true.");
        }
        return Mono.fromCallable(() -> object("updatedCount", itemStore.markUnreadAsRead(linkName)))
            .subscribeOn(Schedulers.boundedElastic());
    }

    private static String optionalName(Map<String, Object> args, String key) {
        return args.containsKey(key) ? requiredString(args, key) : string(args, key);
    }

    private static Boolean optionalBoolean(Map<String, Object> args, String key) {
        if (!args.containsKey(key)) {
            return null;
        }
        if (!(args.get(key) instanceof Boolean)) {
            throw error("INVALID_ARGUMENT", key + " must be a boolean.");
        }
        return bool(args, key, false);
    }

    private static Map<String, Object> itemView(LinkFeedItem item) {
        return object("id", item.getId(), "linkName", item.getLinkName(), "url", item.getUrl(),
            "title", truncate(item.getTitle(), 300), "summary", truncate(item.getSummary(), 500),
            "author", truncate(item.getAuthor(), 200),
            "publishedAt", instantText(item.getPublishedAt()),
            "read", Boolean.TRUE.equals(item.getRead()),
            "favorite", Boolean.TRUE.equals(item.getFavorite()),
            "readLater", Boolean.TRUE.equals(item.getReadLater()),
            "hidden", Boolean.TRUE.equals(item.getHidden()));
    }

    private static String truncate(String value, int limit) {
        return value == null || value.length() <= limit ? value : value.substring(0, limit);
    }

    private static String instantText(Instant value) {
        return value == null ? null : value.toString();
    }

    private static Instant sortInstant(LinkFeedItem item) {
        if (item.getPublishedAt() != null) {
            return item.getPublishedAt();
        }
        return item.getUpdatedAt() != null ? item.getUpdatedAt() : item.getFetchedAt();
    }
}
