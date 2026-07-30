package run.halo.links.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Resolved display information for a source Comment subject.")
public record LinkApplicationOriginSubject(
    String title,
    String url,
    String kindName
) {
}
