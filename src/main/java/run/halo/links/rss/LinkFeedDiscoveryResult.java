package run.halo.links.rss;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LinkFeedDiscoveryResult {
    private List<String> feedUrls = List.of();
}
