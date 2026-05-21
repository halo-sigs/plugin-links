package run.halo.links.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

@Data
@Schema(description = "Request body used to reorder links or link groups.")
public class SortRequest {

    @Schema(description = "Ordered metadata names; each item is assigned a priority matching its position.")
    private List<String> names;
}
