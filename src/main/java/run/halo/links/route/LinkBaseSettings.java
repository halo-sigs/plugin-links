package run.halo.links.route;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;

@Data
class LinkBaseSettings {

    static final String DEFAULT_TITLE = "链接";

    private String title;

    static LinkBaseSettings defaults() {
        return new LinkBaseSettings();
    }

    String normalizedTitle() {
        return StringUtils.defaultIfBlank(title, DEFAULT_TITLE);
    }
}
