package run.halo.links.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Request body for extracting friend-link information from a comment using AI.
 */
@Data
@Schema(description = "Request body for extracting friend-link information from a comment using AI.")
public class LinkCommentExtractionRequest {

    @Schema(description = "Comment content to analyze", requiredMode = Schema.RequiredMode.REQUIRED)
    private String content;
}
