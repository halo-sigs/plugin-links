package run.halo.links.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Structured AI decision for a possible friend-link application.
 */
public record LinkCommentRecognitionResult(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    boolean isLinkApplication,
    String url,
    String displayName,
    String logo,
    String description,
    String backlink,
    List<String> feedUrls
) {
}
