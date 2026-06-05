package run.halo.links.vo;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LinkFeedItemPageVo {
    private List<LinkFeedItemVo> items;
    private String nextBeforePublishedAt;
    private String nextBeforeId;
    private boolean hasNext;
}
