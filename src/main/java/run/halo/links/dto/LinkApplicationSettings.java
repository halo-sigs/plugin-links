package run.halo.links.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import lombok.Data;

@Data
@Schema(description = "Link application settings.")
public class LinkApplicationSettings {

    public static final int DEFAULT_PENDING_CAPACITY = 100;

    private Boolean enabled;

    private SelfSubmission selfSubmission;

    private Security security;

    private CommentRecognition commentRecognition;

    private Notification notification;

    public static LinkApplicationSettings defaults() {
        var settings = new LinkApplicationSettings();
        settings.setEnabled(false);
        settings.setSelfSubmission(SelfSubmission.defaults());
        settings.setSecurity(Security.defaults());
        settings.setCommentRecognition(CommentRecognition.defaults());
        settings.setNotification(Notification.defaults());
        return settings;
    }

    public LinkApplicationSettings normalized() {
        var settings = new LinkApplicationSettings();
        settings.setEnabled(Boolean.TRUE.equals(enabled));
        settings.setSelfSubmission(selfSubmission == null
            ? SelfSubmission.defaults()
            : selfSubmission.normalized());
        settings.setSecurity(security == null
            ? Security.defaults()
            : security.normalized());
        settings.setCommentRecognition(commentRecognition == null
            ? CommentRecognition.defaults()
            : commentRecognition.normalized());
        settings.setNotification(notification == null
            ? Notification.defaults()
            : notification.normalized());
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

    public BigInteger pendingCapacity() {
        return security == null
            ? BigInteger.valueOf(DEFAULT_PENDING_CAPACITY)
            : security.pendingCapacity();
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

    public boolean notificationEnabled() {
        return applicationEnabled()
            && notification != null
            && notification.enabled()
            && !notificationRecipients().isEmpty();
    }

    public List<String> notificationRecipients() {
        return notification == null || notification.getRecipients() == null
            ? List.of()
            : notification.getRecipients();
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
    public static class Security {

        private BigDecimal pendingCapacity;

        static Security defaults() {
            var settings = new Security();
            settings.setPendingCapacity(BigDecimal.valueOf(DEFAULT_PENDING_CAPACITY));
            return settings;
        }

        Security normalized() {
            var settings = new Security();
            settings.setPendingCapacity(new BigDecimal(validatedPendingCapacity()));
            return settings;
        }

        BigInteger pendingCapacity() {
            return validatedPendingCapacity();
        }

        private BigInteger validatedPendingCapacity() {
            var value = pendingCapacity == null
                ? BigDecimal.valueOf(DEFAULT_PENDING_CAPACITY)
                : pendingCapacity;
            try {
                var integer = value.toBigIntegerExact();
                if (integer.signum() < 1) {
                    throw new IllegalArgumentException(
                        "Pending application capacity must be a positive integer");
                }
                return integer;
            } catch (ArithmeticException error) {
                throw new IllegalArgumentException(
                    "Pending application capacity must be a positive integer", error);
            }
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
    public static class Notification {

        private Boolean enabled;

        private List<String> recipients;

        static Notification defaults() {
            var settings = new Notification();
            settings.setEnabled(false);
            settings.setRecipients(List.of());
            return settings;
        }

        Notification normalized() {
            var settings = new Notification();
            settings.setEnabled(Boolean.TRUE.equals(enabled));
            settings.setRecipients(normalizeRecipients(recipients));
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

    private static List<String> normalizeRecipients(List<String> recipients) {
        if (recipients == null || recipients.isEmpty()) {
            return List.of();
        }
        var normalized = new LinkedHashSet<String>();
        for (var recipient : recipients) {
            var username = normalizeText(recipient);
            if (username != null) {
                normalized.add(username);
            }
        }
        return List.copyOf(normalized);
    }

    private static String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        var normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
