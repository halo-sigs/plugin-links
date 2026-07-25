package run.halo.links.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Result of cleaning all LinkApplications matching the supplied filters.")
public record LinkApplicationCleanupResult(
    long matched,
    long deleted,
    long failed,
    long skipped
) {
}
