package run.halo.links.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.webflux.core.fn.SpringdocRouteBuilder.route;
import static run.halo.app.extension.index.query.Queries.equal;

import io.swagger.v3.oas.annotations.enums.ParameterIn;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerErrorException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.links.extension.Link;
import run.halo.links.nitrite.LinksNitriteDatabase;
import run.halo.links.rss.LinkFeedCleanupResult;
import run.halo.links.rss.LinkFeedDiscoveryResult;
import run.halo.links.rss.LinkFeedItem;
import run.halo.links.rss.LinkFeedItemPage;
import run.halo.links.rss.LinkFeedItemQuery;
import run.halo.links.rss.LinkFeedItemStore;
import run.halo.links.rss.LinkFeedRefreshResult;
import run.halo.links.rss.LinkFeedRetentionPolicy;
import run.halo.links.rss.LinkFeedRetentionService;
import run.halo.links.rss.LinkFeedService;

@Component
@RequiredArgsConstructor
public class LinkFeedEndpoint implements CustomEndpoint {

    private final LinkFeedService linkFeedService;
    private final LinkFeedRetentionService retentionService;
    private final LinkFeedItemStore itemStore;
    private final LinksNitriteDatabase database;
    private final ReactiveExtensionClient client;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        final var tag = "console.api.link.halo.run/v1alpha1/LinkFeed";
        return route()
            .GET("rss/discovery", this::discoverFeed, builder -> builder
                .operationId("discoverLinkFeed")
                .description("Discover RSS or Atom feed URLs from a link website URL.")
                .tag(tag)
                .parameter(parameterBuilder()
                    .name("url")
                    .description("Absolute HTTP or HTTPS website URL to inspect.")
                    .in(ParameterIn.QUERY)
                    .implementation(String.class)
                    .required(true)
                )
                .response(responseBuilder().implementation(LinkFeedDiscoveryResult.class))
            )
            .POST("links/{name}/rss/refresh", this::refreshFeed, builder -> builder
                .operationId("refreshLinkFeed")
                .description("Refresh RSS or Atom items for all configured feed URLs on an enabled link.")
                .tag(tag)
                .parameter(parameterBuilder()
                    .name("name")
                    .description("Metadata name of the link.")
                    .in(ParameterIn.PATH)
                    .implementation(String.class)
                    .required(true)
                )
                .response(responseBuilder().implementation(LinkFeedRefreshResult.class))
            )
            .GET("rss/items", this::listFeedItems, builder -> {
                builder.operationId("listLinkFeedItems")
                    .description("List cached RSS or Atom feed items with cursor pagination.")
                    .tag(tag)
                    .parameter(parameterBuilder()
                        .name("linkName")
                        .description("Filter items by link metadata name.")
                        .in(ParameterIn.QUERY)
                        .implementation(String.class)
                        .required(false)
                    )
                    .parameter(parameterBuilder()
                        .name("groupName")
                        .description("Filter items by current link group metadata name.")
                        .in(ParameterIn.QUERY)
                        .implementation(String.class)
                        .required(false)
                    )
                    .parameter(parameterBuilder()
                        .name("read")
                        .description("Filter items by read state.")
                        .in(ParameterIn.QUERY)
                        .implementation(Boolean.class)
                        .required(false)
                    )
                    .parameter(parameterBuilder()
                        .name("favorite")
                        .description("Filter items by favorite state.")
                        .in(ParameterIn.QUERY)
                        .implementation(Boolean.class)
                        .required(false)
                    )
                    .parameter(parameterBuilder()
                        .name("readLater")
                        .description("Filter items by read-later state.")
                        .in(ParameterIn.QUERY)
                        .implementation(Boolean.class)
                        .required(false)
                    )
                    .parameter(parameterBuilder()
                        .name("beforePublishedAt")
                        .description("Cursor published time boundary.")
                        .in(ParameterIn.QUERY)
                        .implementation(String.class)
                        .required(false)
                    )
                    .parameter(parameterBuilder()
                        .name("beforeId")
                        .description("Cursor stable item id boundary.")
                        .in(ParameterIn.QUERY)
                        .implementation(String.class)
                        .required(false)
                    )
                    .parameter(parameterBuilder()
                        .name("limit")
                        .description("Maximum number of items to return.")
                        .in(ParameterIn.QUERY)
                        .implementation(Integer.class)
                        .required(false)
                    )
                    .response(responseBuilder().implementation(LinkFeedItemPage.class));
            })
            .POST("rss/items/{id}/read", this::markItemRead, builder -> builder
                .operationId("markLinkFeedItemRead")
                .description("Mark a cached RSS or Atom feed item as read or unread.")
                .tag(tag)
                .parameter(parameterBuilder()
                    .name("id")
                    .description("Stable cached feed item id.")
                    .in(ParameterIn.PATH)
                    .implementation(String.class)
                    .required(true)
                )
                .parameter(parameterBuilder()
                    .name("read")
                    .description("Read state to apply.")
                    .in(ParameterIn.QUERY)
                    .implementation(Boolean.class)
                    .required(true)
                )
            )
            .POST("rss/items/{id}/favorite", this::markItemFavorite, builder -> builder
                .operationId("markLinkFeedItemFavorite")
                .description("Mark a cached RSS or Atom feed item as favorite or not favorite.")
                .tag(tag)
                .parameter(parameterBuilder()
                    .name("id")
                    .description("Stable cached feed item id.")
                    .in(ParameterIn.PATH)
                    .implementation(String.class)
                    .required(true)
                )
                .parameter(parameterBuilder()
                    .name("favorite")
                    .description("Favorite state to apply.")
                    .in(ParameterIn.QUERY)
                    .implementation(Boolean.class)
                    .required(true)
                )
            )
            .POST("rss/items/{id}/read-later", this::markItemReadLater, builder -> builder
                .operationId("markLinkFeedItemReadLater")
                .description("Mark a cached RSS or Atom feed item as read-later or not read-later.")
                .tag(tag)
                .parameter(parameterBuilder()
                    .name("id")
                    .description("Stable cached feed item id.")
                    .in(ParameterIn.PATH)
                    .implementation(String.class)
                    .required(true)
                )
                .parameter(parameterBuilder()
                    .name("readLater")
                    .description("Read-later state to apply.")
                    .in(ParameterIn.QUERY)
                    .implementation(Boolean.class)
                    .required(true)
                )
            )
            .POST("rss/-/cleanup", this::cleanupFeedItems, builder -> builder
                .operationId("cleanupLinkFeedItems")
                .description("Apply RSS item retention cleanup and compact the embedded feed cache.")
                .tag(tag)
                .response(responseBuilder().implementation(LinkFeedCleanupResult.class))
            )
            .build();
    }

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion("console.api.link.halo.run/v1alpha1");
    }

    private Mono<ServerResponse> discoverFeed(ServerRequest request) {
        return Mono.fromSupplier(() -> requiredQuery(request, "url"))
            .flatMap(linkFeedService::discover)
            .flatMap(result -> ServerResponse.ok().bodyValue(result))
            .onErrorResume(IllegalArgumentException.class, LinkFeedEndpoint::badRequest)
            .onErrorResume(ServerErrorException.class, LinkFeedEndpoint::fetchError);
    }

    private Mono<ServerResponse> refreshFeed(ServerRequest request) {
        String name = request.pathVariable("name");
        return linkFeedService.refresh(name)
            .flatMap(result -> ServerResponse.ok().bodyValue(result))
            .onErrorResume(ResponseStatusException.class, LinkFeedEndpoint::statusError)
            .onErrorResume(ServerErrorException.class, LinkFeedEndpoint::fetchError);
    }

    private Mono<ServerResponse> listFeedItems(ServerRequest request) {
        String groupName = request.queryParam("groupName")
            .filter(StringUtils::hasText)
            .orElse(null);
        try {
            LinkFeedItemQuery query = parseQuery(request);
            if (StringUtils.hasText(groupName) && StringUtils.hasText(query.getLinkName())) {
                return badRequest("linkName and groupName cannot be used together.");
            }
            Mono<LinkFeedItemPage> page = StringUtils.hasText(groupName)
                ? listByGroup(groupName, query)
                : Mono.fromCallable(() -> linkFeedService.listItems(query))
                    .subscribeOn(Schedulers.boundedElastic());
            return page.flatMap(result -> ServerResponse.ok().bodyValue(result));
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        }
    }

    private Mono<ServerResponse> markItemRead(ServerRequest request) {
        String id = request.pathVariable("id");
        try {
            boolean read = request.queryParam("read")
                .filter(StringUtils::hasText)
                .map(value -> parseBoolean(value, "read"))
                .orElseThrow(() -> new IllegalArgumentException("Missing query parameter: read"));
            return Mono.fromCallable(() -> itemStore.updateRead(id, read))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(updated -> updated
                    ? ServerResponse.noContent().build()
                    : ServerResponse.notFound().build());
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        }
    }

    private Mono<ServerResponse> markItemFavorite(ServerRequest request) {
        String id = request.pathVariable("id");
        try {
            boolean favorite = request.queryParam("favorite")
                .filter(StringUtils::hasText)
                .map(value -> parseBoolean(value, "favorite"))
                .orElseThrow(() -> new IllegalArgumentException(
                    "Missing query parameter: favorite"));
            return Mono.fromCallable(() -> itemStore.updateFavorite(id, favorite))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(updated -> updated
                    ? ServerResponse.noContent().build()
                    : ServerResponse.notFound().build());
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        }
    }

    private Mono<ServerResponse> markItemReadLater(ServerRequest request) {
        String id = request.pathVariable("id");
        try {
            boolean readLater = request.queryParam("readLater")
                .filter(StringUtils::hasText)
                .map(value -> parseBoolean(value, "readLater"))
                .orElseThrow(() -> new IllegalArgumentException(
                    "Missing query parameter: readLater"));
            return Mono.fromCallable(() -> itemStore.updateReadLater(id, readLater))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(updated -> updated
                    ? ServerResponse.noContent().build()
                    : ServerResponse.notFound().build());
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        }
    }

    private Mono<ServerResponse> cleanupFeedItems(ServerRequest request) {
        return Mono.fromCallable(() -> {
                retentionService.enforce(LinkFeedRetentionPolicy.defaults());
                database.compact();
                return new LinkFeedCleanupResult(itemStore.count());
            })
            .subscribeOn(Schedulers.boundedElastic())
            .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<LinkFeedItemPage> listByGroup(String groupName, LinkFeedItemQuery query) {
        var options = ListOptions.builder()
            .andQuery(equal("spec.groupName", groupName))
            .build();
        return client.listAll(Link.class, options, Sort.unsorted())
            .map(link -> link.getMetadata().getName())
            .collectList()
            .flatMap(linkNames -> Mono.fromCallable(() -> listByLinkNames(linkNames, query))
                .subscribeOn(Schedulers.boundedElastic()));
    }

    private LinkFeedItemPage listByLinkNames(List<String> linkNames, LinkFeedItemQuery query) {
        if (linkNames.isEmpty()) {
            return new LinkFeedItemPage(List.of(), null, null, false);
        }
        int limit = query.normalizedLimit();
        List<LinkFeedItem> items = new ArrayList<>();
        for (String linkName : linkNames) {
            LinkFeedItemQuery linkQuery = new LinkFeedItemQuery();
            linkQuery.setLinkName(linkName);
            linkQuery.setBeforePublishedAt(query.getBeforePublishedAt());
            linkQuery.setBeforeId(query.getBeforeId());
            linkQuery.setRead(query.getRead());
            linkQuery.setFavorite(query.getFavorite());
            linkQuery.setReadLater(query.getReadLater());
            linkQuery.setLimit(limit + 1);
            items.addAll(itemStore.listRecent(linkQuery));
        }
        items.sort(recentComparator());
        boolean hasNext = items.size() > limit;
        List<LinkFeedItem> pageItems = hasNext
            ? List.copyOf(items.subList(0, limit))
            : List.copyOf(items);
        LinkFeedItem last = pageItems.isEmpty() ? null : pageItems.get(pageItems.size() - 1);
        String nextBeforePublishedAt = last == null || last.getPublishedAt() == null
            ? null
            : last.getPublishedAt().toString();
        String nextBeforeId = last == null ? null : last.getId();
        return new LinkFeedItemPage(pageItems, nextBeforePublishedAt, nextBeforeId, hasNext);
    }

    private static LinkFeedItemQuery parseQuery(ServerRequest request) {
        LinkFeedItemQuery query = new LinkFeedItemQuery();
        request.queryParam("linkName")
            .filter(StringUtils::hasText)
            .ifPresent(query::setLinkName);
        request.queryParam("beforeId")
            .filter(StringUtils::hasText)
            .ifPresent(query::setBeforeId);
        request.queryParam("beforePublishedAt")
            .filter(StringUtils::hasText)
            .ifPresent(value -> {
                try {
                    query.setBeforePublishedAt(Instant.parse(value));
                } catch (DateTimeParseException e) {
                    throw new IllegalArgumentException("Invalid beforePublishedAt.", e);
                }
            });
        request.queryParam("read")
            .filter(StringUtils::hasText)
            .ifPresent(value -> query.setRead(parseBoolean(value, "read")));
        request.queryParam("favorite")
            .filter(StringUtils::hasText)
            .ifPresent(value -> query.setFavorite(parseBoolean(value, "favorite")));
        request.queryParam("readLater")
            .filter(StringUtils::hasText)
            .ifPresent(value -> query.setReadLater(parseBoolean(value, "readLater")));
        request.queryParam("limit")
            .filter(StringUtils::hasText)
            .ifPresent(value -> {
                try {
                    query.setLimit(Integer.parseInt(value));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid limit.", e);
                }
            });
        return query;
    }

    private static boolean parseBoolean(String value, String parameterName) {
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        throw new IllegalArgumentException("Invalid " + parameterName + ".");
    }

    private static Comparator<LinkFeedItem> recentComparator() {
        return Comparator.comparing(LinkFeedEndpoint::sortInstant,
                Comparator.nullsLast(Comparator.naturalOrder()))
            .reversed()
            .thenComparing(LinkFeedItem::getId, Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private static Instant sortInstant(LinkFeedItem item) {
        if (item.getPublishedAt() != null) {
            return item.getPublishedAt();
        }
        if (item.getUpdatedAt() != null) {
            return item.getUpdatedAt();
        }
        return item.getFetchedAt();
    }

    private static String requiredQuery(ServerRequest request, String name) {
        return request.queryParam(name)
            .filter(StringUtils::hasText)
            .orElseThrow(() -> new IllegalArgumentException("Missing query parameter: " + name));
    }

    private static Mono<ServerResponse> statusError(ResponseStatusException error) {
        return ServerResponse.status(error.getStatusCode())
            .bodyValue(Map.of("error", error.getReason() == null
                ? error.getMessage()
                : error.getReason()));
    }

    private static Mono<ServerResponse> fetchError(ServerErrorException error) {
        String message = error.getMessage();
        if (message != null && (message.contains("Invalid URL")
            || message.contains("blocked")
            || message.contains("redirect")
            || message.contains("maximum size"))) {
            return badRequest(message);
        }
        return ServerResponse.status(HttpStatus.BAD_GATEWAY)
            .bodyValue(Map.of("error", message == null ? "Failed to fetch feed." : message));
    }

    private static Mono<ServerResponse> badRequest(Throwable error) {
        return badRequest(error.getMessage());
    }

    private static Mono<ServerResponse> badRequest(String message) {
        return ServerResponse.badRequest()
            .bodyValue(Map.of("error", message));
    }
}
