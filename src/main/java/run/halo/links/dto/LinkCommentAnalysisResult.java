package run.halo.links.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Structured result of extracting friend-link information from a comment using AI.
 *
 * @param url         the website URL extracted from the comment
 * @param displayName the website name extracted from the comment
 * @param logo        the logo URL extracted from the comment, if any
 * @param description the website description extracted from the comment, if any
 */
public record LinkCommentAnalysisResult(
    @Schema(description = "Website URL extracted from the comment")
    String url,

    @Schema(description = "Website name extracted from the comment")
    String displayName,

    @Schema(description = "Logo URL extracted from the comment, if any")
    String logo,

    @Schema(description = "Website description extracted from the comment, if any")
    String description
) {
}
