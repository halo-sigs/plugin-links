package run.halo.links.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Runtime status for AI-assisted link features.
 *
 * @param enabled                    whether the plugin AI feature is enabled
 * @param available                  whether the AI Foundation API is available at runtime
 * @param operational                whether an enabled AI model service is available
 * @param commentExtractionEnabled   whether link extraction from recent comments is enabled
 * @param commentExtractionOperational whether the extraction model can currently be resolved
 * @param commentExtractionModelName selected language model name, or null for default model
 * @param commentApplicationRecognitionEnabled whether automatic recognition is effective in
 *                                             Link Application settings
 * @param commentApplicationRecognitionOperational whether its selected model is available
 * @param commentApplicationRecognitionModelName selected automatic recognition model
 */
public record LinkAiFeatureStatus(
    @Schema(description = "Whether the plugin AI feature is enabled")
    boolean enabled,

    @Schema(description = "Whether the AI Foundation API is available at runtime")
    boolean available,

    @Schema(description = "Whether AI Foundation exposes an enabled model service")
    boolean operational,

    @Schema(description = "Whether link extraction from recent comments is enabled")
    boolean commentExtractionEnabled,

    @Schema(description = "Whether the comment extraction model can currently be resolved")
    boolean commentExtractionOperational,

    @Schema(description = "Selected language model name, or null for default model")
    String commentExtractionModelName,

    @Schema(description = "Whether automatic comment application recognition is configured")
    boolean commentApplicationRecognitionEnabled,

    @Schema(description = "Whether the automatic recognition model can currently be resolved")
    boolean commentApplicationRecognitionOperational,

    @Schema(description = "Selected automatic recognition model name")
    String commentApplicationRecognitionModelName
) {
}
