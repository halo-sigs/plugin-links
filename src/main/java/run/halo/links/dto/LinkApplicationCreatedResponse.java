package run.halo.links.dto;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Minimal reference to a newly created link application.")
public record LinkApplicationCreatedResponse(
    @Schema(description = "Created LinkApplication metadata name.", requiredMode = REQUIRED)
    String id,

    @Schema(description = "Initial application status.", requiredMode = REQUIRED,
        allowableValues = "PENDING")
    String status
) {
}
