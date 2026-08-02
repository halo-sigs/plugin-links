package run.halo.links.rss;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LinkFeedHiddenCount {
    @Schema(description = "Exact number of hidden cached feed items.")
    private long hiddenCount;
}
