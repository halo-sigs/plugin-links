package run.halo.links.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Simplified comment data for friend-link extraction selection.
 *
 * @param name        metadata name of the comment
 * @param raw         raw comment content
 * @param content     rendered HTML content
 * @param ownerName   comment owner display name
 * @param ownerEmail  comment owner email
 * @param creationTime comment creation time
 */
public record LinkCommentSummaryDTO(
    @Schema(description = "Metadata name of the comment")
    String name,

    @Schema(description = "Raw comment content submitted by the owner")
    String raw,

    @Schema(description = "Rendered HTML content")
    String content,

    @Schema(description = "Comment owner display name")
    String ownerName,

    @Schema(description = "Comment owner email")
    String ownerEmail,

    @Schema(description = "Comment creation time")
    Instant creationTime
) {
}
