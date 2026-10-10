package run.halo.links.mcp;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import run.halo.links.rss.LinkFeedStorageUnavailableException;
import run.halo.mcpserver.api.McpToolAnnotations;
import run.halo.mcpserver.api.McpToolDefinition;
import run.halo.mcpserver.api.McpToolException;
import run.halo.mcpserver.api.McpToolResult;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

final class McpSupport {

    private McpSupport() {
    }

    static McpToolDefinition tool(String name, String title, String description,
        String displayDescription, Map<String, Object> properties, List<String> required,
        McpToolAnnotations annotations,
        Function<Map<String, Object>, Mono<Map<String, Object>>> handler) {
        return McpToolDefinition.builder()
            .name(name).title(name.replace('_', ' ')).description(description)
            .displayTitle(title).displayDescription(displayDescription)
            .category(category(name))
            .inputSchema(object("type", "object", "properties", properties,
                "required", required, "additionalProperties", false))
            .outputSchema(McpOutputSchemas.forTool(name))
            .annotations(annotations)
            // The MCP server checks the access key's per-tool allowlist before this callback.
            .permission(invocation -> Mono.just(true))
            .handler(invocation -> Mono.defer(() -> {
                var args = invocation.arguments();
                if (!properties.keySet().containsAll(args.keySet())) {
                    throw error("INVALID_ARGUMENT", "Unknown argument.");
                }
                for (var key : required) {
                    if (!args.containsKey(key) || args.get(key) == null) {
                        throw error("INVALID_ARGUMENT", "Missing argument: " + key);
                    }
                }
                return handler.apply(args).map(McpSupport::result).map(McpToolResult::success);
            }).onErrorResume(McpToolException.class,
                e -> Mono.just(McpToolResult.error(e.code(), e.getMessage())))
                .onErrorResume(LinkFeedStorageUnavailableException.class,
                    e -> Mono.just(McpToolResult.error("UNAVAILABLE",
                        "Feed storage is unavailable.")))
                .onErrorResume(IllegalArgumentException.class,
                    e -> Mono.just(McpToolResult.error("INVALID_ARGUMENT", e.getMessage())))
                .onErrorResume(OptimisticLockingFailureException.class,
                    e -> Mono.just(McpToolResult.error("CONFLICT",
                        "The resource changed. Read it again before retrying.")))
                .onErrorResume(ResponseStatusException.class, e -> {
                    var code = switch (e.getStatusCode().value()) {
                        case 400 -> "INVALID_ARGUMENT";
                        case 403 -> "FORBIDDEN";
                        case 404 -> "NOT_FOUND";
                        case 409 -> "CONFLICT";
                        case 429 -> "RATE_LIMITED";
                        case 503 -> "UNAVAILABLE";
                        default -> "REQUEST_FAILED";
                    };
                    return Mono.just(McpToolResult.error(code,
                        e.getReason() == null ? code : e.getReason()));
                }))
            .build();
    }

    private static String category(String name) {
        return switch (name) {
            case "list_links", "get_link", "create_link", "update_link", "delete_link",
                "move_links", "sort_links", "list_link_groups", "create_link_group",
                "update_link_group", "delete_link_group", "sort_link_groups",
                "fetch_site_metadata", "check_links" -> "友链管理";
            case "list_link_applications", "get_link_application", "verify_link_application",
                "approve_link_application", "reject_link_application",
                "delete_link_application" -> "友链申请";
            case "discover_feeds", "list_feed_items", "get_feed_summary", "refresh_link_feed",
                "set_feed_item_state", "mark_feed_items_read" -> "友链订阅";
            default -> throw new IllegalArgumentException("Missing category for tool: " + name);
        };
    }

    static String string(Map<String, Object> args, String key) {
        var value = args.get(key);
        if (value == null && !args.containsKey(key)) {
            return null;
        }
        if (!(value instanceof String text)) {
            throw error("INVALID_ARGUMENT", key + " must be a string.");
        }
        return text;
    }

    static String requiredString(Map<String, Object> args, String key) {
        var value = string(args, key);
        if (value == null || value.isBlank()) {
            throw error("INVALID_ARGUMENT", key + " must not be blank.");
        }
        return value.trim();
    }

    static boolean bool(Map<String, Object> args, String key, boolean fallback) {
        if (!args.containsKey(key)) {
            return fallback;
        }
        if (!(args.get(key) instanceof Boolean value)) {
            throw error("INVALID_ARGUMENT", key + " must be a boolean.");
        }
        return value;
    }

    static int integer(Map<String, Object> args, String key, int fallback, int min, int max) {
        if (!args.containsKey(key)) {
            return fallback;
        }
        if (!(args.get(key) instanceof Number value)
            || !Double.isFinite(value.doubleValue())
            || value.doubleValue() != Math.rint(value.doubleValue())
            || value.doubleValue() < min || value.doubleValue() > max) {
            throw error("INVALID_ARGUMENT", key + " must be an integer between "
                + min + " and " + max + ".");
        }
        return value.intValue();
    }

    static List<String> strings(Map<String, Object> args, String key) {
        if (!args.containsKey(key)) {
            return List.of();
        }
        if (!(args.get(key) instanceof List<?> values) || values.size() > 100) {
            throw error("INVALID_ARGUMENT", key + " must be an array with at most 100 items.");
        }
        return values.stream().map(value -> {
            if (!(value instanceof String text) || text.isBlank()) {
                throw error("INVALID_ARGUMENT", key + " must contain nonblank strings.");
            }
            return text.trim();
        }).toList();
    }

    static Map<String, Object> schema(String type, String description) {
        return object("type", type, "description", description);
    }

    static Map<String, Object> object(Object... entries) {
        var result = new LinkedHashMap<String, Object>();
        for (int i = 0; i < entries.length; i += 2) {
            result.put((String) entries[i], entries[i + 1]);
        }
        return result;
    }

    static Map<String, Object> result(Object value) {
        return JsonMapper.shared().convertValue(value, new TypeReference<>() { });
    }

    static McpToolException error(String code, String message) {
        return new McpToolException(code, message);
    }

    static McpToolAnnotations readOnly(String title) {
        return McpToolAnnotations.readOnly(title);
    }

    static McpToolAnnotations write(String title, boolean destructive, boolean openWorld) {
        return new McpToolAnnotations(false, destructive, false, openWorld, title);
    }
}
