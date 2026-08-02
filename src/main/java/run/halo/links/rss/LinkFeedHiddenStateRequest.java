package run.halo.links.rss;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

@Data
public class LinkFeedHiddenStateRequest {
    @Schema(description = "Stable cached feed item IDs to update.",
        requiredMode = Schema.RequiredMode.REQUIRED)
    private List<String> ids;

    @Schema(description = "Hidden state to apply.", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean hidden;
}
