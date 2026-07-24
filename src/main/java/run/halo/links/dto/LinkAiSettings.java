package run.halo.links.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import lombok.Data;

/**
 * Plugin AI settings stored in the "ai" setting group.
 */
@Data
@Schema(description = "Plugin AI settings.")
public class LinkAiSettings {

    private Boolean enabled;

    private CommentExtraction commentExtraction;

    private CommentApplicationRecognition commentApplicationRecognition;

    public static LinkAiSettings defaults() {
        var settings = new LinkAiSettings();
        settings.setEnabled(false);
        settings.setCommentExtraction(CommentExtraction.defaults());
        settings.setCommentApplicationRecognition(CommentApplicationRecognition.defaults());
        return settings;
    }

    public LinkAiSettings normalized() {
        var settings = new LinkAiSettings();
        settings.setEnabled(Boolean.TRUE.equals(enabled));
        settings.setCommentExtraction(commentExtraction == null
            ? CommentExtraction.defaults()
            : commentExtraction.normalized());
        settings.setCommentApplicationRecognition(commentApplicationRecognition == null
            ? CommentApplicationRecognition.defaults()
            : commentApplicationRecognition.normalized());
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

    public boolean commentApplicationRecognitionEnabled() {
        return aiEnabled()
            && commentApplicationRecognition != null
            && commentApplicationRecognition.enabled()
            && commentApplicationRecognitionModelName() != null
            && !commentApplicationRecognitionSources().isEmpty();
    }

    public String commentApplicationRecognitionModelName() {
        if (commentApplicationRecognition == null) {
            return null;
        }
        return normalizeModelName(commentApplicationRecognition.getModelName());
    }

    public List<RecognitionSource> commentApplicationRecognitionSources() {
        if (commentApplicationRecognition == null
            || commentApplicationRecognition.getSources() == null) {
            return List.of();
        }
        return commentApplicationRecognition.getSources();
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

    @Data
    @Schema(description = "AI settings for recognizing link applications in new comments.")
    public static class CommentApplicationRecognition {

        private Boolean enabled;

        private String modelName;

        private List<RecognitionSource> sources;

        static CommentApplicationRecognition defaults() {
            var settings = new CommentApplicationRecognition();
            settings.setEnabled(false);
            settings.setSources(List.of());
            return settings;
        }

        CommentApplicationRecognition normalized() {
            var settings = new CommentApplicationRecognition();
            settings.setEnabled(Boolean.TRUE.equals(enabled));
            settings.setModelName(normalizeModelName(modelName));
            settings.setSources(normalizeSources(sources));
            return settings;
        }

        boolean enabled() {
            return Boolean.TRUE.equals(enabled);
        }

        private static List<RecognitionSource> normalizeSources(List<RecognitionSource> sources) {
            if (sources == null || sources.isEmpty()) {
                return List.of();
            }
            var normalized = new LinkedHashMap<String, RecognitionSource>();
            for (var source : sources) {
                if (source == null || source.getType() == null) {
                    continue;
                }
                var name = normalizeModelName(source.getName());
                if (source.getType() != SourceType.LINKS && name == null) {
                    continue;
                }
                var normalizedName = source.getType() == SourceType.LINKS ? null : name;
                var normalizedSource = new RecognitionSource();
                normalizedSource.setType(source.getType());
                normalizedSource.setName(normalizedName);
                normalized.put(source.getType() + ":" + Objects.toString(normalizedName, ""),
                    normalizedSource);
            }
            return List.copyOf(normalized.values());
        }
    }

    @Data
    @Schema(description = "A comment subject eligible for application recognition.")
    public static class RecognitionSource {

        private SourceType type;

        private String name;
    }

    public enum SourceType {
        LINKS,
        POST,
        SINGLE_PAGE
    }
}
