package run.halo.links.rss;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LinkFeedItemSummary {

    @Schema(description = "Number of all cached RSS or Atom feed items where hidden = 1.",
        requiredMode = REQUIRED)
    private long hiddenCount;

    @Schema(description = "Number of cached RSS or Atom feed items where hidden = 0 and favorite = 1.",
        requiredMode = REQUIRED)
    private long favoriteCount;

    @Schema(description = "Number of cached RSS or Atom feed items where hidden = 0 and read_later = 1.",
        requiredMode = REQUIRED)
    private long readLaterCount;
}
