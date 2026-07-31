package run.halo.links.dto;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import java.net.URI;
import java.time.Instant;
import java.util.List;

@Schema(description = "Halo Problem Details response for public link application operations.")
public record LinkApplicationProblemResponse(
    @Schema(description = "Stable machine-readable problem type.", requiredMode = REQUIRED)
    URI type,

    @Schema(description = "HTTP status title.", requiredMode = REQUIRED)
    String title,

    @Schema(description = "HTTP status code.", requiredMode = REQUIRED)
    int status,

    @Schema(description = "Safe display detail.", requiredMode = REQUIRED)
    String detail,

    @Schema(description = "Request URI.", requiredMode = REQUIRED)
    URI instance,

    @Schema(description = "Halo request identifier.", requiredMode = REQUIRED)
    String requestId,

    @Schema(description = "Problem creation time.", requiredMode = REQUIRED)
    Instant timestamp,

    @Schema(description = "Display validation messages for invalid application fields.")
    List<String> errors,

    @Schema(description = "Positive retry delay for rate-limited requests.")
    Long retryAfterSeconds
) {
}
