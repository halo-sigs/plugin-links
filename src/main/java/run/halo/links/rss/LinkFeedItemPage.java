package run.halo.links.rss;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LinkFeedItemPage {
    private List<LinkFeedItem> items;
    private String nextBeforePublishedAt;
    private String nextBeforeId;
    private boolean hasNext;
}
