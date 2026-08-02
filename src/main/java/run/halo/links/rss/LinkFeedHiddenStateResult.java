package run.halo.links.rss;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LinkFeedHiddenStateResult {
    @Schema(description = "Number of distinct requested item IDs.")
    private long requestedCount;

    @Schema(description = "Number of existing items whose hidden state changed.")
    private long updatedCount;
}
