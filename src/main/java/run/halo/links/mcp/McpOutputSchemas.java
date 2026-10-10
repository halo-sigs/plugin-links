package run.halo.links.mcp;

import static run.halo.links.mcp.McpSupport.object;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class McpOutputSchemas {

    private McpOutputSchemas() {
    }

    static Map<String, Object> forTool(String name) {
        return switch (name) {
            case "list_links" -> page(link(false), true);
            case "get_link", "create_link", "update_link" -> link(true);
            case "list_link_groups" -> page(group(), true);
            case "create_link_group", "update_link_group" -> group();
            case "delete_link", "delete_link_group", "delete_link_application" ->
                fields("name", text(), "deleted", constant(true));
            case "move_links" -> batch("groupName", text());
            case "sort_links", "sort_link_groups" -> batch("priority", integer());
            case "fetch_site_metadata" -> fields("title", nullable(text()),
                "description", nullable(text()), "icon", nullable(text()),
                "image", nullable(text()));
            case "check_links" -> check();
            case "list_link_applications" -> page(application(), false);
            case "get_link_application" -> applicationDetail();
            case "reject_link_application" -> application();
            case "verify_link_application" -> verifyApplication();
            case "approve_link_application" -> fields(
                "name", text(), "status", constant("APPROVED"),
                "link", fields("name", text(), "url", text(), "displayName", text(),
                    "logo", nullable(text()), "description", nullable(text()),
                    "groupName", nullable(text()), "backlink", nullable(text()),
                    "feedUrls", nullable(array(text()))));
            case "discover_feeds" -> discovery();
            case "refresh_link_feed" -> refresh();
            case "list_feed_items" -> fields("items", array(feedItem()), "hasNext", bool(),
                "nextBeforePublishedAt", nullable(text()), "nextBeforeId", nullable(text()));
            case "get_feed_summary" -> fields("hiddenCount", integer(), "favoriteCount", integer(),
                "readLaterCount", integer(), "unreadCount", integer(),
                "unreadByLinkName", object("type", "object", "additionalProperties", integer()));
            case "set_feed_item_state" -> feedState();
            case "mark_feed_items_read" -> fields("updatedCount", integer());
            default -> throw new IllegalArgumentException(
                "Missing output schema for tool: " + name);
        };
    }

    private static Map<String, Object> link(boolean detail) {
        var schema = fields("name", text(), "displayName", text(), "url", text(),
            "description", nullable(text()), "logo", nullable(text()),
            "groupName", nullable(text()), "priority", nullable(integer()),
            "verification", nullable(fields("lastCheckedAt", nullable(text()),
                "access", nullable(access()), "backlink", nullable(backlink()))));
        if (detail) {
            add(schema, "rss", nullable(fields("enabled", nullable(bool()),
                "feedUrls", nullable(array(text())))), true);
            add(schema, "backlinkScanUrl", nullable(text()), true);
            add(schema, "rssStatus", nullable(fields("lastFetchedAt", nullable(text()),
                "lastSuccessAt", nullable(text()), "lastError", nullable(text()),
                "failureCount", nullable(integer()), "latestPublishedAt", nullable(text()),
                "itemCount", nullable(integer()))), true);
        }
        return schema;
    }

    private static Map<String, Object> access() {
        return fields("state", nullable(enumeration("CHECKING", "ACCESSIBLE", "INACCESSIBLE")),
            "checkedAt", nullable(text()), "statusCode", nullable(integer()),
            "finalUrl", nullable(text()), "error", nullable(text()));
    }

    private static Map<String, Object> backlink() {
        return fields("state", nullable(backlinkState()), "checkedAt", nullable(text()),
            "scanUrl", nullable(text()), "targetUrl", nullable(text()),
            "matchedUrl", nullable(text()), "error", nullable(text()));
    }

    private static Map<String, Object> backlinkState() {
        return enumeration("CHECKING", "FOUND", "MISSING", "NOT_CONFIGURED", "FAILED");
    }

    private static Map<String, Object> group() {
        return fields("name", text(), "displayName", text(), "priority", nullable(integer()));
    }

    private static Map<String, Object> page(Map<String, Object> item, boolean hasNext) {
        var schema = fields("items", array(item), "page", integer(), "size", integer(),
            "total", integer());
        if (hasNext) {
            add(schema, "hasNext", bool(), true);
        }
        return schema;
    }

    private static Map<String, Object> batch(String field, Map<String, Object> value) {
        var success = fields("name", text(), "success", constant(true), field, value);
        var failure = fields("name", text(), "success", constant(false), "code", text(),
            "message", text());
        return fields("items", array(object("oneOf", List.of(success, failure))),
            "succeeded", integer(), "failed", integer());
    }

    private static Map<String, Object> application() {
        return fields("name", text(), "creationTimestamp", nullable(text()), "url", text(),
            "displayName", text(), "logo", nullable(text()), "description", nullable(text()),
            "backlink", nullable(text()), "feedUrls", nullable(array(text())),
            "status", nullable(enumeration("PENDING", "APPROVING", "APPROVED", "REJECTED")),
            "originType", nullable(enumeration("FORM", "COMMENT")),
            "commentName", nullable(text()), "linkName", nullable(text()));
    }

    private static Map<String, Object> applicationDetail() {
        var schema = application();
        var comment = fields("name", text(), "raw", nullable(text()),
            "creationTime", nullable(text()), "approved", bool(), "hidden", bool(),
            "subjectRef", nullable(fields("group", nullable(text()), "version", nullable(text()),
                "kind", nullable(text()), "name", nullable(text()))));
        add(comment, "subject", fields("title", nullable(text()), "url", nullable(text()),
            "kindName", nullable(text())), false);
        add(schema, "originComment", comment, false);
        return schema;
    }

    private static Map<String, Object> verifyApplication() {
        var schema = fields("name", text(), "state", nullable(backlinkState()), "found", bool());
        for (var key : List.of("checkedAt", "scanUrl", "targetUrl", "matchedUrl", "error")) {
            add(schema, key, nullable(text()), false);
        }
        return schema;
    }

    private static Map<String, Object> feedItem() {
        return fields("id", text(), "linkName", text(), "url", text(),
            "title", nullable(text()), "summary", nullable(text()), "author", nullable(text()),
            "publishedAt", nullable(text()), "read", bool(), "favorite", bool(),
            "readLater", bool(), "hidden", bool());
    }

    private static Map<String, Object> feedState() {
        var states = fields();
        for (var key : List.of("read", "favorite", "readLater", "hidden")) {
            add(states, key, bool(), false);
        }
        states.put("minProperties", 1);
        var schema = fields("id", text(), "states", states);
        add(schema, "hiddenUpdatedCount", integer(), false);
        return schema;
    }

    private static Map<String, Object> check() {
        return fields("state", constant("accepted"), "result",
            fields("acceptedNames", array(text()), "skippedNames", array(text()),
                "alreadyRunningNames", array(text()), "acceptedCount", integer(),
                "skippedCount", integer(), "alreadyRunningCount", integer()));
    }

    private static Map<String, Object> discovery() {
        return fields("feedUrls", array(text()));
    }

    private static Map<String, Object> refresh() {
        var feed = fields("url", text(), "fetchedAt", text(),
            "latestPublishedAt", nullable(text()), "itemCount", integer(),
            "fetchedItemCount", integer(), "notModified", bool(), "etag", nullable(text()),
            "lastModified", nullable(text()), "error", nullable(text()));
        return fields("linkName", text(), "fetchedAt", text(),
            "latestPublishedAt", nullable(text()), "itemCount", integer(),
            "fetchedItemCount", integer(), "partialFailure", bool(), "feeds", array(feed));
    }

    private static Map<String, Object> fields(Object... entries) {
        var properties = object(entries);
        return object("type", "object", "properties", properties,
            "required", new ArrayList<>(properties.keySet()), "additionalProperties", false);
    }

    @SuppressWarnings("unchecked")
    private static void add(Map<String, Object> schema, String key, Map<String, Object> value,
        boolean required) {
        ((Map<String, Object>) schema.get("properties")).put(key, value);
        if (required) {
            ((List<String>) schema.get("required")).add(key);
        }
    }

    private static Map<String, Object> text() {
        return object("type", "string");
    }

    private static Map<String, Object> integer() {
        return object("type", "integer");
    }

    private static Map<String, Object> bool() {
        return object("type", "boolean");
    }

    private static Map<String, Object> constant(Object value) {
        return object("type", value instanceof Boolean ? "boolean" : "string", "const", value);
    }

    private static Map<String, Object> enumeration(String... values) {
        return object("type", "string", "enum", List.of(values));
    }

    private static Map<String, Object> array(Map<String, Object> items) {
        return object("type", "array", "items", items);
    }

    private static Map<String, Object> nullable(Map<String, Object> schema) {
        return object("anyOf", List.of(schema, object("type", "null")));
    }
}
