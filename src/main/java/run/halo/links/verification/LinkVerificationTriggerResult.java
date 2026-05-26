package run.halo.links.verification;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

@Data
@Schema(description = "Result returned after link verification work is accepted.")
public class LinkVerificationTriggerResult {

    @Schema(description = "Link names accepted for background verification.")
    private List<String> acceptedNames = List.of();

    @Schema(description = "Requested link names skipped because they could not be resolved.")
    private List<String> skippedNames = List.of();

    @Schema(description = "Link names skipped because verification is already running.")
    private List<String> alreadyRunningNames = List.of();

    @Schema(description = "Number of links accepted for background verification.")
    public int getAcceptedCount() {
        return acceptedNames.size();
    }

    @Schema(description = "Number of requested links skipped because they could not be resolved.")
    public int getSkippedCount() {
        return skippedNames.size();
    }

    @Schema(description = "Number of requested links already running verification.")
    public int getAlreadyRunningCount() {
        return alreadyRunningNames.size();
    }
}
