package run.halo.links.route;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;

@Data
public class LinkBaseSettings {

    public static final String DEFAULT_TITLE = "链接";

    private String title;

    public static LinkBaseSettings defaults() {
        return new LinkBaseSettings();
    }

    public String normalizedTitle() {
        return StringUtils.defaultIfBlank(title, DEFAULT_TITLE);
    }
}
