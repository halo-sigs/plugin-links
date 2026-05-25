package run.halo.links.rss;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LinkFeedUnreadSummary {
    private long totalUnreadCount;
    private Map<String, Long> unreadCountsByLinkName;
}
