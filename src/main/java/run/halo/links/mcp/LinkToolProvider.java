package run.halo.links.mcp;

import static run.halo.app.extension.index.query.Queries.contains;
import static run.halo.app.extension.index.query.Queries.equal;
import static run.halo.app.extension.index.query.Queries.isNull;
import static run.halo.app.extension.index.query.Queries.or;
import static run.halo.links.mcp.McpSupport.bool;
import static run.halo.links.mcp.McpSupport.error;
import static run.halo.links.mcp.McpSupport.integer;
import static run.halo.links.mcp.McpSupport.object;
import static run.halo.links.mcp.McpSupport.readOnly;
import static run.halo.links.mcp.McpSupport.requiredString;
import static run.halo.links.mcp.McpSupport.result;
import static run.halo.links.mcp.McpSupport.schema;
import static run.halo.links.mcp.McpSupport.string;
import static run.halo.links.mcp.McpSupport.strings;
import static run.halo.links.mcp.McpSupport.tool;
import static run.halo.links.mcp.McpSupport.write;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Sort;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.PageRequestImpl;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.links.dto.LinkRequest;
import run.halo.links.extension.Link;
import run.halo.links.extension.LinkGroup;
import run.halo.links.service.LinkGroupService;
import run.halo.links.service.LinkUrlCanonicalizer;
import run.halo.links.verification.LinkVerificationRequest;
import run.halo.links.verification.LinkVerificationService;
import run.halo.mcpserver.api.McpToolAnnotations;
import run.halo.mcpserver.api.McpToolDefinition;
import run.halo.mcpserver.api.McpToolException;
import run.halo.mcpserver.api.McpToolProvider;

@RequiredArgsConstructor
public final class LinkToolProvider implements McpToolProvider {

    private final ReactiveExtensionClient client;
    private final LinkGroupService groupService;
    private final LinkVerificationService verificationService;

    @Override
    public Flux<McpToolDefinition> tools() {
        return Flux.just(
            tool("list_links", "查询友链",
                "Search links with pagination. Use groupName or ungrouped, never both. "
                    + "Verification filters describe stored results, not a new check.",
                "分页查询友链。groupName 与 ungrouped 不能同时使用。"
                    + "验证条件筛选的是已保存的结果，不会发起新的检查。",
                listProperties(), List.of(), readOnly("List links"), this::listLinks),
            tool("get_link", "查看友链详情", "Read a link by its metadata name, including "
                    + "RSS configuration and timestamped verification results.",
                "根据资源名称查看友链详情，包括 RSS 配置和带检查时间的验证结果。",
                nameProperties(), List.of("name"), readOnly("Get link"),
                args -> getLink(requiredString(args, "name")).map(LinkToolProvider::linkView)),
            tool("create_link", "添加友链", "Create a link. URL duplicates are allowed. "
                    + "Does not automatically run verification or refresh feeds; use check_links "
                    + "and refresh_link_feed when needed.",
                "创建友链，允许网址重复。不会自动执行验证或刷新订阅；"
                    + "需要时请调用 check_links 和 refresh_link_feed。",
                editProperties(false), List.of("url", "displayName"),
                write("Create link", false, false), this::createLink),
            tool("update_link", "修改友链", "Update only supplied fields. Empty groupName "
                    + "ungroups the link; empty optional strings clear them. Disabling RSS "
                    + "cleans cached articles asynchronously. Does not trigger a check or refresh.",
                "仅更新提供的字段。groupName 为空字符串时移出分组，其他可选字符串为空时清空对应字段。"
                    + "关闭 RSS 后会异步清理缓存文章。不会触发检查或刷新订阅。",
                editProperties(true), List.of("name"), write("Update link", true, false),
                this::updateLink),
            tool("delete_link", "删除友链", "Delete a link by metadata name. Its cached RSS "
                    + "articles are also cleaned asynchronously.",
                "根据资源名称删除友链，并异步清理其 RSS 缓存文章。",
                nameProperties(), List.of("name"), write("Delete link", true, false),
                args -> getLink(requiredString(args, "name"))
                    .flatMap(client::delete).map(link -> object("name",
                        link.getMetadata().getName(), "deleted", true))),
            tool("move_links", "批量移动友链", "Move explicit links to a group. Empty groupName "
                    + "means ungrouped. Returns per-link results; the batch is not atomic.",
                "将指定友链批量移动到目标分组，groupName 为空字符串时移出分组。"
                    + "逐条返回处理结果，批量操作不保证全部成功或全部回滚。",
                object("names", namesSchema(), "groupName", schema("string",
                    "Target group metadata name, or empty string to ungroup.")),
                List.of("names", "groupName"), write("Move links", true, false),
                this::moveLinks),
            tool("sort_links", "调整友链顺序", "Set priorities to 0, 1, ... for the supplied "
                    + "ordered names only. Other links and group membership are unchanged. "
                    + "Returns per-link results; the batch is not atomic.",
                "按提供的友链名称顺序，将其优先级依次设为 0、1 等。其他友链及分组归属保持不变。"
                    + "逐条返回处理结果，批量操作不保证全部成功或全部回滚。",
                object("names", namesSchema()), List.of("names"),
                write("Sort links", true, false), args -> sortResources(args, false)),
            tool("list_link_groups", "查询友链分组", "List link groups ordered by priority "
                    + "with pagination. Use returned names for link membership changes.",
                "按优先级分页查询友链分组，可使用返回的分组名称调整友链的分组归属。",
                pageProperties(), List.of(), readOnly("List link groups"), this::listGroups),
            tool("create_link_group", "创建友链分组", "Create a link group.",
                "创建友链分组。",
                groupProperties(false), List.of("displayName"),
                write("Create link group", false, false), this::createGroup),
            tool("update_link_group", "修改友链分组", "Update only supplied group fields.",
                "仅更新提供的分组字段。",
                groupProperties(true), List.of("name"),
                write("Update link group", true, false), this::updateGroup),
            tool("delete_link_group", "删除友链分组", "Delete a group. By default its links "
                    + "become ungrouped. deleteLinks=true also deletes its links and their feed "
                    + "caches. This operation is not atomic.",
                "删除友链分组，默认将组内友链移至未分组。"
                    + "deleteLinks=true 时同时删除组内友链及其订阅缓存。此操作不保证全部成功或全部回滚。",
                object("name", schema("string", "Group metadata name."),
                    "deleteLinks", schema("boolean", "Also delete member links; default false.")),
                List.of("name"), write("Delete link group", true, false),
                args -> groupService.deleteLinkGroup(requiredString(args, "name"),
                        bool(args, "deleteLinks", false))
                    .switchIfEmpty(Mono.error(error("NOT_FOUND", "Group not found.")))
                    .map(group -> object("name", group.getMetadata().getName(), "deleted", true))),
            tool("sort_link_groups", "调整分组顺序", "Set priorities to 0, 1, ... for the "
                    + "supplied ordered group names only. Other groups are unchanged. "
                    + "Returns per-group results; the batch is not atomic.",
                "按提供的分组名称顺序，将其优先级依次设为 0、1 等，其他分组保持不变。"
                    + "逐组返回处理结果，批量操作不保证全部成功或全部回滚。",
                object("names", namesSchema()), List.of("names"),
                write("Sort link groups", true, false), args -> sortResources(args, true)),
            tool("fetch_site_metadata", "获取网站资料", "Fetch title, description, icon and "
                    + "preview image from a public HTTP(S) website. Does not save a link.",
                "从公开的 HTTP(S) 网站获取标题、描述、图标和预览图片，不会保存为友链。",
                object("url", schema("string", "Absolute HTTP(S) website URL.")),
                List.of("url"), new McpToolAnnotations(true, false, true, true,
                    "Fetch site metadata"),
                args -> Mono.fromCallable(() -> result(LinkRequest.getLinkDetail(
                        url(requiredString(args, "url")))))
                    .subscribeOn(Schedulers.boundedElastic())),
            tool("check_links", "检查友链", "Start asynchronous reachability/backlink checks. "
                    + "Specify exactly one of names, groupName or all=true. Returns accepted, "
                    + "skipped and already-running names, not completed checks. Read get_link "
                    + "or list_links later for timestamped results. Writes verification status.",
                "异步检查友链的可访问性和反向链接。names、groupName 与 all=true 必须且只能指定一项。"
                    + "返回已受理、已跳过和正在检查的友链名称，不代表检查已完成。"
                    + "此操作会写入验证状态，稍后可通过 get_link 或 list_links 查看带检查时间的结果。",
                object("names", namesSchema(), "groupName", schema("string", "Group name."),
                    "all", schema("boolean", "Explicitly check every link.")),
                List.of(), write("Check links", false, true), this::checkLinks)
        );
    }

    private Mono<Map<String, Object>> listLinks(Map<String, Object> args) {
        int page = integer(args, "page", 1, 1, Integer.MAX_VALUE);
        int size = integer(args, "size", 20, 1, 100);
        var groupName = string(args, "groupName");
        boolean ungrouped = bool(args, "ungrouped", false);
        if (groupName != null && (groupName.isBlank() || ungrouped)) {
            throw error("INVALID_ARGUMENT", "Use a nonblank groupName or ungrouped=true.");
        }
        var access = enumFilter(args, "accessState", Link.AccessState.class);
        var backlink = enumFilter(args, "backlinkState", Link.BacklinkState.class);
        var builder = ListOptions.builder().andQuery(isNull("metadata.deletionTimestamp"));
        var keyword = string(args, "keyword");
        if (keyword != null && !keyword.isBlank()) {
            builder.andQuery(or(contains("spec.displayName", keyword),
                contains("spec.description", keyword), contains("spec.url", keyword)));
        }
        if (groupName != null) {
            builder.andQuery(equal("spec.groupName", groupName));
        }
        var options = builder.build();
        var sort = sort(args);
        if (!ungrouped && access == null && backlink == null) {
            return client.listBy(Link.class, options, PageRequestImpl.of(page, size, sort))
                .map(value -> page(value.getItems().stream()
                    .map(LinkToolProvider::linkSummary).toList(), page, size, value.getTotal()));
        }
        // Verification is not indexed; filter before pagination to keep totals accurate.
        Mono<Set<String>> groups = ungrouped
            ? client.listAll(LinkGroup.class, activeOptions(), Sort.unsorted())
                .map(group -> group.getMetadata().getName()).collectList().map(HashSet::new)
            : Mono.just(Set.of());
        return groups.flatMap(existing -> client.listAll(Link.class, options, sort)
            .filter(link -> !ungrouped || !existing.contains(link.getSpec().getGroupName()))
            .filter(link -> matchesVerification(link, access, backlink))
            .collectList().map(links -> {
                long offset = (long) (page - 1) * size;
                var items = links.stream().skip(offset).limit(size)
                    .map(LinkToolProvider::linkSummary).toList();
                return page(items, page, size, links.size());
            }));
    }

    private Mono<Map<String, Object>> listGroups(Map<String, Object> args) {
        int page = integer(args, "page", 1, 1, Integer.MAX_VALUE);
        int size = integer(args, "size", 20, 1, 100);
        return client.listBy(LinkGroup.class, activeOptions(), PageRequestImpl.of(page, size,
                Sort.by("spec.priority", "metadata.name")))
            .map(value -> page(value.getItems().stream().map(LinkToolProvider::groupView).toList(),
                page, size, value.getTotal()));
    }

    private Mono<Map<String, Object>> createLink(Map<String, Object> args) {
        var link = new Link();
        var metadata = new Metadata();
        metadata.setGenerateName("link-");
        link.setMetadata(metadata);
        link.setSpec(new Link.LinkSpec());
        applyLinkFields(link, args);
        return validateGroup(link.getSpec().getGroupName())
            .then(Mono.defer(() -> nextLinkPriority(args))).flatMap(priority -> {
                link.getSpec().setPriority(priority);
                return client.create(link);
            }).map(LinkToolProvider::linkView);
    }

    private Mono<Map<String, Object>> updateLink(Map<String, Object> args) {
        if (args.size() == 1) {
            throw error("INVALID_ARGUMENT", "Supply at least one field to update.");
        }
        return getLink(requiredString(args, "name")).flatMap(link -> {
            applyLinkFields(link, args);
            var validation = args.containsKey("groupName")
                ? validateGroup(link.getSpec().getGroupName()) : Mono.<Void>empty();
            return validation.then(Mono.defer(() -> client.update(link)));
        }).map(LinkToolProvider::linkView);
    }

    private void applyLinkFields(Link link, Map<String, Object> args) {
        var spec = link.getSpec();
        if (args.containsKey("url")) {
            spec.setUrl(url(requiredString(args, "url")));
        }
        if (args.containsKey("displayName")) {
            spec.setDisplayName(requiredString(args, "displayName"));
        }
        if (args.containsKey("description")) {
            spec.setDescription(string(args, "description"));
        }
        if (args.containsKey("logo")) {
            spec.setLogo(optionalUrl(string(args, "logo")));
        }
        if (args.containsKey("groupName")) {
            var group = string(args, "groupName").trim();
            spec.setGroupName(group.isEmpty() ? null : group);
        }
        if (args.containsKey("priority")) {
            spec.setPriority(integer(args, "priority", 0, Integer.MIN_VALUE, Integer.MAX_VALUE));
        }
        if (args.containsKey("backlinkScanUrl")) {
            var backlink = string(args, "backlinkScanUrl");
            if (backlink.isBlank()) {
                spec.setVerification(null);
            } else {
                var verification = new Link.VerificationSpec();
                verification.setBacklinkScanUrl(url(backlink));
                spec.setVerification(verification);
            }
        }
        if (args.containsKey("rssEnabled") || args.containsKey("feedUrls")) {
            var rss = spec.getRss() == null ? new Link.RssSpec() : spec.getRss();
            if (args.containsKey("rssEnabled")) {
                rss.setEnabled(bool(args, "rssEnabled", false));
            } else if (rss.getEnabled() == null) {
                rss.setEnabled(false);
            }
            if (args.containsKey("feedUrls")) {
                rss.setFeedUrls(strings(args, "feedUrls").stream().map(LinkToolProvider::url)
                    .distinct().toList());
            }
            boolean noFeeds = rss.getFeedUrls() == null || rss.getFeedUrls().isEmpty();
            if (Boolean.TRUE.equals(rss.getEnabled()) && noFeeds) {
                throw error("INVALID_ARGUMENT", "Enabled RSS requires at least one feed URL.");
            }
            spec.setRss(noFeeds ? null : rss);
        }
    }

    private Mono<Integer> nextLinkPriority(Map<String, Object> args) {
        if (args.containsKey("priority")) {
            return Mono.just(integer(args, "priority", 0, Integer.MIN_VALUE, Integer.MAX_VALUE));
        }
        return client.listAll(Link.class, activeOptions(), Sort.unsorted())
            .map(link -> link.getSpec().getPriority() == null ? 0 : link.getSpec().getPriority())
            .reduce(0, Math::max).map(value -> value == Integer.MAX_VALUE ? value : value + 1);
    }

    private Mono<Map<String, Object>> createGroup(Map<String, Object> args) {
        var group = new LinkGroup();
        var metadata = new Metadata();
        metadata.setGenerateName("link-group-");
        group.setMetadata(metadata);
        group.setSpec(new LinkGroup.LinkGroupSpec());
        group.getSpec().setDisplayName(requiredString(args, "displayName"));
        Mono<Integer> priority = args.containsKey("priority")
            ? Mono.just(integer(args, "priority", 0, Integer.MIN_VALUE, Integer.MAX_VALUE))
            : client.listAll(LinkGroup.class, activeOptions(), Sort.unsorted())
                .map(item -> item.getSpec().getPriority() == null
                    ? 0 : item.getSpec().getPriority())
                .reduce(0, Math::max).map(value -> value == Integer.MAX_VALUE ? value : value + 1);
        return priority.flatMap(value -> {
            group.getSpec().setPriority(value);
            return client.create(group);
        }).map(LinkToolProvider::groupView);
    }

    private Mono<Map<String, Object>> updateGroup(Map<String, Object> args) {
        if (args.size() == 1) {
            throw error("INVALID_ARGUMENT", "Supply at least one field to update.");
        }
        return getGroup(requiredString(args, "name")).flatMap(group -> {
            if (args.containsKey("displayName")) {
                group.getSpec().setDisplayName(requiredString(args, "displayName"));
            }
            if (args.containsKey("priority")) {
                group.getSpec().setPriority(integer(args, "priority", 0,
                    Integer.MIN_VALUE, Integer.MAX_VALUE));
            }
            return client.update(group);
        }).map(LinkToolProvider::groupView);
    }

    private Mono<Map<String, Object>> moveLinks(Map<String, Object> args) {
        var names = orderedNames(args);
        var target = string(args, "groupName").trim();
        return validateGroup(target).then(batch(names, name -> getLink(name).flatMap(link -> {
            link.getSpec().setGroupName(target.isEmpty() ? null : target);
            return client.update(link);
        }).thenReturn(object("name", name, "success", true, "groupName", target))));
    }

    private Mono<Map<String, Object>> sortResources(Map<String, Object> args, boolean groups) {
        var names = orderedNames(args);
        return batch(names, name -> {
            int priority = names.indexOf(name);
            Mono<?> update = groups
                ? getGroup(name).flatMap(group -> {
                    group.getSpec().setPriority(priority);
                    return client.update(group);
                })
                : getLink(name).flatMap(link -> {
                    link.getSpec().setPriority(priority);
                    return client.update(link);
                });
            return update.thenReturn(object("name", name, "success", true, "priority", priority));
        });
    }

    private Mono<Map<String, Object>> batch(List<String> names,
        Function<String, Mono<Map<String, Object>>> operation) {
        return Flux.fromIterable(names).concatMap(name -> Mono.defer(() -> operation.apply(name))
                .onErrorResume(OptimisticLockingFailureException.class,
                    e -> Mono.just(object("name", name, "success", false, "code", "CONFLICT",
                        "message", "The resource changed. Read it again before retrying.")))
                .onErrorResume(e -> Mono.just(object("name", name, "success", false,
                    "code", e instanceof McpToolException toolError ? toolError.code()
                        : "UPDATE_FAILED", "message", "Could not update this resource."))))
            .collectList().map(items -> object("items", items,
                "succeeded", items.stream().filter(item -> Boolean.TRUE.equals(item.get("success")))
                    .count(), "failed", items.stream()
                    .filter(item -> Boolean.FALSE.equals(item.get("success"))).count()));
    }

    private Mono<Map<String, Object>> checkLinks(Map<String, Object> args) {
        var names = strings(args, "names");
        var group = string(args, "groupName");
        boolean all = bool(args, "all", false);
        if ((args.containsKey("names") && names.isEmpty())
            || (group != null && group.isBlank())
            || (args.containsKey("names") ? 1 : 0) + (group != null ? 1 : 0) + (all ? 1 : 0) != 1) {
            throw error("INVALID_ARGUMENT", "Specify exactly one of nonempty names, "
                + "groupName or all=true.");
        }
        var request = new LinkVerificationRequest();
        request.setNames(names);
        request.setGroupName(group);
        return validateGroup(group).then(Mono.defer(() -> verificationService.verify(request)))
            .map(value -> object("state", "accepted", "result", result(value)));
    }

    private Mono<Link> getLink(String name) {
        return client.fetch(Link.class, name)
            .filter(link -> link.getMetadata().getDeletionTimestamp() == null)
            .switchIfEmpty(Mono.error(error("NOT_FOUND", "Link not found: " + name)));
    }

    private Mono<LinkGroup> getGroup(String name) {
        return client.fetch(LinkGroup.class, name)
            .filter(group -> group.getMetadata().getDeletionTimestamp() == null)
            .switchIfEmpty(Mono.error(error("NOT_FOUND", "Group not found: " + name)));
    }

    private Mono<Void> validateGroup(String name) {
        return name == null || name.isBlank() ? Mono.empty() : getGroup(name).then();
    }

    private static ListOptions activeOptions() {
        return ListOptions.builder().andQuery(isNull("metadata.deletionTimestamp")).build();
    }

    private static Map<String, Object> linkSummary(Link link) {
        var spec = link.getSpec();
        return object("name", link.getMetadata().getName(), "displayName", spec.getDisplayName(),
            "url", spec.getUrl(), "description", spec.getDescription(), "logo", spec.getLogo(),
            "groupName", spec.getGroupName(), "priority", spec.getPriority(),
            "verification", link.getStatus().getVerification());
    }

    private static Map<String, Object> linkView(Link link) {
        var view = linkSummary(link);
        view.put("rss", link.getSpec().getRss());
        view.put("backlinkScanUrl", link.getSpec().getVerification() == null ? null
            : link.getSpec().getVerification().getBacklinkScanUrl());
        var rss = link.getStatus().getRss();
        view.put("rssStatus", rss == null ? null : object("lastFetchedAt", rss.getLastFetchedAt(),
            "lastSuccessAt", rss.getLastSuccessAt(), "lastError", rss.getLastError(),
            "failureCount", rss.getFailureCount(), "latestPublishedAt", rss.getLatestPublishedAt(),
            "itemCount", rss.getItemCount()));
        return view;
    }

    private static Map<String, Object> groupView(LinkGroup group) {
        return object("name", group.getMetadata().getName(),
            "displayName", group.getSpec().getDisplayName(),
            "priority", group.getSpec().getPriority());
    }

    private static Map<String, Object> page(List<?> items, int page, int size, long total) {
        return object("items", items, "page", page, "size", size, "total", total,
            "hasNext", (long) page * size < total);
    }

    private static Sort sort(Map<String, Object> args) {
        var key = string(args, "sortBy");
        key = key == null ? "priority" : key;
        var field = switch (key) {
            case "priority" -> "spec.priority";
            case "displayName" -> "spec.displayName";
            case "creationTimestamp" -> "metadata.creationTimestamp";
            default -> throw error("INVALID_ARGUMENT", "Unsupported sortBy.");
        };
        var direction = string(args, "sortDirection");
        if (direction != null && !Set.of("asc", "desc").contains(direction)) {
            throw error("INVALID_ARGUMENT", "sortDirection must be asc or desc.");
        }
        return Sort.by("desc".equals(direction) ? Sort.Direction.DESC : Sort.Direction.ASC, field)
            .and(Sort.by("metadata.name"));
    }

    private static <E extends Enum<E>> String enumFilter(Map<String, Object> args, String key,
        Class<E> type) {
        var value = string(args, key);
        if (value != null && !"UNKNOWN".equals(value)) {
            try {
                Enum.valueOf(type, value);
            } catch (IllegalArgumentException e) {
                throw error("INVALID_ARGUMENT", "Invalid " + key + ".");
            }
        }
        return value;
    }

    private static boolean matchesVerification(Link link, String access, String backlink) {
        var verification = link.getStatus().getVerification();
        var accessState = verification == null || verification.getAccess() == null
            ? null : verification.getAccess().getState();
        var backlinkState = verification == null || verification.getBacklink() == null
            ? null : verification.getBacklink().getState();
        return (access == null
            || access.equals(accessState == null ? "UNKNOWN" : accessState.name()))
            && (backlink == null || backlink.equals(
                backlinkState == null ? "UNKNOWN" : backlinkState.name()));
    }

    private static String url(String value) {
        if (LinkUrlCanonicalizer.canonicalKey(value).isEmpty()) {
            throw error("INVALID_ARGUMENT", "Expected an absolute HTTP(S) URL.");
        }
        return value.trim();
    }

    private static String optionalUrl(String value) {
        return value.isBlank() ? "" : url(value);
    }

    private static List<String> orderedNames(Map<String, Object> args) {
        var names = strings(args, "names");
        if (names.isEmpty() || new HashSet<>(names).size() != names.size()) {
            throw error("INVALID_ARGUMENT", "names must contain 1 to 100 unique metadata names.");
        }
        return names;
    }

    private static Map<String, Object> nameProperties() {
        return object("name", schema("string", "Resource metadata name returned by a query."));
    }

    private static Map<String, Object> pageProperties() {
        return object("page", object("type", "integer", "minimum", 1, "default", 1),
            "size", object("type", "integer", "minimum", 1, "maximum", 100, "default", 20));
    }

    private static Map<String, Object> namesSchema() {
        return object("type", "array", "items", schema("string", "Resource metadata name."),
            "minItems", 1, "maxItems", 100, "uniqueItems", true);
    }

    private static Map<String, Object> listProperties() {
        var properties = pageProperties();
        properties.putAll(object("keyword", schema("string", "Search name, description and URL."),
            "groupName", schema("string", "Filter by group metadata name."),
            "ungrouped", schema("boolean", "Include links with no group or a missing group."),
            "accessState", object("type", "string", "enum",
                List.of("UNKNOWN", "CHECKING", "ACCESSIBLE", "INACCESSIBLE")),
            "backlinkState", object("type", "string", "enum",
                List.of("UNKNOWN", "CHECKING", "FOUND", "MISSING", "NOT_CONFIGURED", "FAILED")),
            "sortBy", object("type", "string", "enum",
                List.of("priority", "displayName", "creationTimestamp")),
            "sortDirection", object("type", "string", "enum", List.of("asc", "desc"))));
        return properties;
    }

    private static Map<String, Object> editProperties(boolean update) {
        var properties = update ? nameProperties() : object();
        properties.putAll(object("url", schema("string", "Absolute HTTP(S) website URL."),
            "displayName", schema("string", "Nonblank display name."),
            "description", schema("string", "Description; empty string clears it."),
            "logo", schema("string", "HTTP(S) logo URL; empty string clears it."),
            "groupName", schema("string", "Existing group metadata name; empty means ungrouped."),
            "priority", schema("integer", "Lower priorities appear first."),
            "rssEnabled", schema("boolean", "Enable RSS tracking; requires feedUrls."),
            "feedUrls", object("type", "array", "maxItems", 100,
                "items", schema("string", "Absolute HTTP(S) feed URL.")),
            "backlinkScanUrl", schema("string", "HTTP(S) backlink page; empty clears it.")));
        return properties;
    }

    private static Map<String, Object> groupProperties(boolean update) {
        var properties = update ? nameProperties() : object();
        properties.putAll(object("displayName", schema("string", "Nonblank display name."),
            "priority", schema("integer", "Lower priorities appear first.")));
        return properties;
    }
}
