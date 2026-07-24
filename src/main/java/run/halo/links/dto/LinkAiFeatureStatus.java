package run.halo.links.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Runtime status for AI-assisted link features.
 *
 * @param enabled                    whether the plugin AI feature is enabled
 * @param available                  whether the AI Foundation API is available at runtime
 * @param commentExtractionEnabled   whether link extraction from recent comments is enabled
 * @param commentExtractionModelName selected language model name, or null for default model
 */
public record LinkAiFeatureStatus(
    @Schema(description = "Whether the plugin AI feature is enabled")
    boolean enabled,

    @Schema(description = "Whether the AI Foundation API is available at runtime")
    boolean available,

    @Schema(description = "Whether link extraction from recent comments is enabled")
    boolean commentExtractionEnabled,

    @Schema(description = "Selected language model name, or null for default model")
    String commentExtractionModelName
) {
}
