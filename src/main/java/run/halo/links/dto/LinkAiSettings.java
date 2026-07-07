package run.halo.links.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Plugin AI settings stored in the "ai" setting group.
 */
@Data
@Schema(description = "Plugin AI settings.")
public class LinkAiSettings {

    private Boolean enabled;

    private CommentExtraction commentExtraction;

    public static LinkAiSettings defaults() {
        var settings = new LinkAiSettings();
        settings.setEnabled(false);
        settings.setCommentExtraction(CommentExtraction.defaults());
        return settings;
    }

    public LinkAiSettings normalized() {
        var settings = new LinkAiSettings();
        settings.setEnabled(Boolean.TRUE.equals(enabled));
        settings.setCommentExtraction(commentExtraction == null
            ? CommentExtraction.defaults()
            : commentExtraction.normalized());
        return settings;
    }

    public boolean aiEnabled() {
        return Boolean.TRUE.equals(enabled);
    }

    public boolean commentExtractionEnabled() {
        return aiEnabled() && commentExtraction != null && commentExtraction.enabled();
    }

    public String commentExtractionModelName() {
        if (commentExtraction == null) {
            return null;
        }
        return normalizeModelName(commentExtraction.getModelName());
    }

    private static String normalizeModelName(String modelName) {
        if (modelName == null) {
            return null;
        }
        var normalized = modelName.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    @Data
    @Schema(description = "AI settings for extracting link information from comments.")
    public static class CommentExtraction {

        private Boolean enabled;

        private String modelName;

        static CommentExtraction defaults() {
            var settings = new CommentExtraction();
            settings.setEnabled(true);
            return settings;
        }

        CommentExtraction normalized() {
            var settings = new CommentExtraction();
            settings.setEnabled(enabled == null || Boolean.TRUE.equals(enabled));
            settings.setModelName(normalizeModelName(modelName));
            return settings;
        }

        boolean enabled() {
            return enabled == null || Boolean.TRUE.equals(enabled);
        }
    }
}
