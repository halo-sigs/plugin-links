package run.halo.links.dto;

/**
 * Privacy-minimized context for automatic comment recognition.
 */
public record LinkCommentRecognitionRequest(
    String rawComment,
    String sourceType,
    String subjectTitle,
    String ownerDisplayName,
    String ownerWebsite
) {
}
