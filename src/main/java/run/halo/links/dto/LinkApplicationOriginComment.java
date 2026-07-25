package run.halo.links.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import run.halo.app.extension.Ref;

@Schema(description = "Minimal source Comment context for one LinkApplication.")
public record LinkApplicationOriginComment(
    String name,
    String raw,
    Ref subjectRef,
    Instant creationTime
) {
}
