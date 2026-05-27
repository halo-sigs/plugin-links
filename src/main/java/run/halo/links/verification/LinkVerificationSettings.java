package run.halo.links.verification;

import java.time.Duration;
import lombok.Data;

@Data
public class LinkVerificationSettings {

    static final int DEFAULT_INTERVAL_HOURS = 24;
    static final int DEFAULT_MAX_LINKS_PER_RUN = 50;

    private Boolean enabled;
    private Integer intervalHours;
    private Integer maxLinksPerRun;
    private Boolean checkBacklink;

    static LinkVerificationSettings defaults() {
        return new LinkVerificationSettings();
    }

    LinkVerificationSettings normalized() {
        LinkVerificationSettings settings = new LinkVerificationSettings();
        settings.setEnabled(Boolean.TRUE.equals(enabled));
        settings.setIntervalHours(positiveOrDefault(intervalHours, DEFAULT_INTERVAL_HOURS));
        settings.setMaxLinksPerRun(positiveOrDefault(maxLinksPerRun, DEFAULT_MAX_LINKS_PER_RUN));
        settings.setCheckBacklink(Boolean.TRUE.equals(checkBacklink));
        return settings;
    }

    boolean automaticVerificationEnabled() {
        return Boolean.TRUE.equals(enabled);
    }

    Duration interval() {
        return Duration.ofHours(positiveOrDefault(intervalHours, DEFAULT_INTERVAL_HOURS));
    }

    int maxLinksPerRun() {
        return positiveOrDefault(maxLinksPerRun, DEFAULT_MAX_LINKS_PER_RUN);
    }

    boolean includeBacklink() {
        return Boolean.TRUE.equals(checkBacklink);
    }

    private static int positiveOrDefault(Integer value, int defaultValue) {
        return value == null || value < 1 ? defaultValue : value;
    }
}
