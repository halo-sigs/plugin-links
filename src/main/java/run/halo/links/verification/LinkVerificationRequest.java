package run.halo.links.verification;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

@Data
@Schema(description = "Request body for triggering link verification.")
public class LinkVerificationRequest {

    @Schema(description = "Metadata names of links to verify. Takes precedence over groupName.")
    private List<String> names;

    @Schema(description = "Metadata name of the link group to verify when names is empty.")
    private String groupName;
}
