package run.halo.links.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import run.halo.mcpserver.api.McpToolDefinition;
import run.halo.mcpserver.api.McpToolResult;
import tools.jackson.databind.json.JsonMapper;

final class McpSchemaAssertions {

    private static final SchemaRegistry SCHEMAS = SchemaRegistry.withDefaultDialect(
        SpecificationVersion.DRAFT_2020_12);
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private McpSchemaAssertions() {
    }

    static McpToolResult assertOutput(McpToolDefinition tool, McpToolResult result) {
        assertThat(result).isNotNull();
        if (!result.error()) {
            assertValid(tool, result.structuredContent());
        }
        return result;
    }

    static void assertSchema(McpToolDefinition tool) {
        var metaSchema = SCHEMAS.getSchema(com.networknt.schema.SchemaLocation.of(
            "https://json-schema.org/draft/2020-12/schema"));
        assertThat(metaSchema.validate(MAPPER.valueToTree(tool.outputSchema())))
            .as("Valid JSON Schema for %s", tool.name()).isEmpty();
    }

    static void assertValid(McpToolDefinition tool, Object value) {
        var errors = SCHEMAS.getSchema(MAPPER.valueToTree(tool.outputSchema()))
            .validate(MAPPER.valueToTree(value));
        assertThat(errors).as("Output of %s conforms to its schema", tool.name()).isEmpty();
    }
}
