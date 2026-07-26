package run.halo.links.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import lombok.Data;

@Data
@Schema(description = "Link application settings.")
public class LinkApplicationSettings {

    private Boolean enabled;

    private SelfSubmission selfSubmission;

    private CommentRecognition commentRecognition;

    public static LinkApplicationSettings defaults() {
        var settings = new LinkApplicationSettings();
        settings.setEnabled(false);
        settings.setSelfSubmission(SelfSubmission.defaults());
        settings.setCommentRecognition(CommentRecognition.defaults());
        return settings;
    }

    public LinkApplicationSettings normalized() {
        var settings = new LinkApplicationSettings();
        settings.setEnabled(Boolean.TRUE.equals(enabled));
        settings.setSelfSubmission(selfSubmission == null
            ? SelfSubmission.defaults()
            : selfSubmission.normalized());
        settings.setCommentRecognition(commentRecognition == null
            ? CommentRecognition.defaults()
            : commentRecognition.normalized());
        return settings;
    }

    public boolean applicationEnabled() {
        return Boolean.TRUE.equals(enabled);
    }

    public boolean selfSubmissionEnabled() {
        return applicationEnabled()
            && selfSubmission != null
            && selfSubmission.enabled();
    }

    public boolean commentRecognitionEnabled() {
        return applicationEnabled()
            && commentRecognition != null
            && commentRecognition.enabled()
            && commentRecognitionModelName() != null
            && !commentRecognitionSources().isEmpty();
    }

    public String commentRecognitionModelName() {
        return commentRecognition == null
            ? null
            : normalizeText(commentRecognition.getModelName());
    }

    public List<RecognitionSource> commentRecognitionSources() {
        return commentRecognition == null || commentRecognition.getSources() == null
            ? List.of()
            : commentRecognition.getSources();
    }

    @Data
    public static class SelfSubmission {

        private Boolean enabled;

        static SelfSubmission defaults() {
            var settings = new SelfSubmission();
            settings.setEnabled(true);
            return settings;
        }

        SelfSubmission normalized() {
            var settings = new SelfSubmission();
            settings.setEnabled(enabled == null || Boolean.TRUE.equals(enabled));
            return settings;
        }

        boolean enabled() {
            return enabled == null || Boolean.TRUE.equals(enabled);
        }
    }

    @Data
    public static class CommentRecognition {

        private Boolean enabled;

        private String modelName;

        private List<RecognitionSource> sources;

        static CommentRecognition defaults() {
            var settings = new CommentRecognition();
            settings.setEnabled(false);
            settings.setSources(List.of());
            return settings;
        }

        CommentRecognition normalized() {
            var settings = new CommentRecognition();
            settings.setEnabled(Boolean.TRUE.equals(enabled));
            settings.setModelName(normalizeText(modelName));
            settings.setSources(normalizeSources(sources));
            return settings;
        }

        boolean enabled() {
            return Boolean.TRUE.equals(enabled);
        }
    }

    @Data
    @Schema(description = "A Comment subject eligible for application recognition.")
    public static class RecognitionSource {

        private SourceType type;

        private String name;
    }

    public enum SourceType {
        LINKS,
        POST,
        SINGLE_PAGE
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
            var name = normalizeText(source.getName());
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

    private static String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        var normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
