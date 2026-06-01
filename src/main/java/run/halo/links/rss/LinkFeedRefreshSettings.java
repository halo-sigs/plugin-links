package run.halo.links.rss;

import java.time.Duration;
import lombok.Data;

@Data
public class LinkFeedRefreshSettings {

    static final int DEFAULT_INTERVAL_HOURS = 1;
    static final int DEFAULT_MAX_LINKS_PER_RUN = 50;

    private Boolean enabled;
    private Boolean publicEnabled;
    private Integer intervalHours;
    private Integer maxLinksPerRun;

    static LinkFeedRefreshSettings defaults() {
        return new LinkFeedRefreshSettings();
    }

    LinkFeedRefreshSettings normalized() {
        LinkFeedRefreshSettings settings = new LinkFeedRefreshSettings();
        settings.setEnabled(automaticRefreshEnabled());
        settings.setPublicEnabled(publicFeedEnabled());
        settings.setIntervalHours(positiveOrDefault(intervalHours, DEFAULT_INTERVAL_HOURS));
        settings.setMaxLinksPerRun(positiveOrDefault(maxLinksPerRun, DEFAULT_MAX_LINKS_PER_RUN));
        return settings;
    }

    boolean automaticRefreshEnabled() {
        return enabled == null || Boolean.TRUE.equals(enabled);
    }

    boolean publicFeedEnabled() {
        return Boolean.TRUE.equals(publicEnabled);
    }

    Duration interval() {
        return Duration.ofHours(positiveOrDefault(intervalHours, DEFAULT_INTERVAL_HOURS));
    }

    int maxLinksPerRun() {
        return positiveOrDefault(maxLinksPerRun, DEFAULT_MAX_LINKS_PER_RUN);
    }

    private static int positiveOrDefault(Integer value, int defaultValue) {
        return value == null || value < 1 ? defaultValue : value;
    }
}
