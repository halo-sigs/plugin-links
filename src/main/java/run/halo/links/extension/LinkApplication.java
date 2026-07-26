package run.halo.links.extension;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;

/**
 * @author ryanwang
 * @since 2.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "Link application extension that represents a pending link submission.")
@GVK(group = "core.halo.run", version = "v1alpha1",
    kind = "LinkApplication", plural = "linkapplications", singular = "linkapplication")
public class LinkApplication extends AbstractExtension {

    @Schema(description = "Desired state of the link application.", requiredMode = REQUIRED)
    private LinkApplicationSpec spec;

    @Data
    @Schema(description = "Configurable fields of a link application.")
    public static class LinkApplicationSpec {
        @Schema(description = "Absolute URL that the link points to.", requiredMode = REQUIRED)
        private String url;

        @Schema(description = "Human-readable name displayed for the link.", requiredMode = REQUIRED)
        private String displayName;

        @Schema(description = "Logo or icon URL displayed with the link.")
        private String logo;

        @Schema(description = "Short text that introduces the linked website.")
        private String description;

        @Schema(description = "Contact email of the applicant.")
        private String email;

        @Schema(description = "Absolute URL of the backlink page on the applicant's site.",
            format = "uri", pattern = "^[Hh][Tt][Tt][Pp][Ss]?://\\S+$")
        private String backlink;

        @ArraySchema(
            arraySchema = @Schema(description = "RSS or Atom feed URLs of the applicant's site."),
            schema = @Schema(format = "uri", pattern = "^[Hh][Tt][Tt][Pp][Ss]?://\\S+$")
        )
        private List<String> feedUrls;

        @Schema(description = "Status of the application.", requiredMode = REQUIRED)
        private Status status;

        @Schema(description = "Origin of the application.")
        private Origin origin;

        @Schema(description = "Frozen approval state for resumable approval.")
        private Approval approval;
    }

    @Data
    @Schema(description = "Origin of a link application.")
    public static class Origin {
        @Schema(description = "Where the application originated.", requiredMode = REQUIRED)
        private OriginType type;

        @Schema(description = "Source Comment when the origin type is COMMENT.")
        private CommentOrigin comment;
    }

    @Data
    @Schema(description = "Reference to the source Comment.")
    public static class CommentOrigin {
        @Schema(description = "Metadata name of the source Comment.", requiredMode = REQUIRED)
        private String name;
    }

    @Data
    @Schema(description = "Frozen state of an application approval.")
    public static class Approval {
        @Schema(description = "Metadata name reserved for the formal Link.",
            requiredMode = REQUIRED)
        private String linkName;

        @Schema(description = "Normalized Link fields frozen when approval starts.",
            requiredMode = REQUIRED)
        private ApprovalRequest request;
    }

    @Data
    @Schema(description = "Normalized Link fields used by a resumable approval.")
    public static class ApprovalRequest {
        @Schema(description = "Approved absolute Link URL.", requiredMode = REQUIRED)
        private String url;

        @Schema(description = "Approved Link display name.", requiredMode = REQUIRED)
        private String displayName;

        @Schema(description = "Approved Link logo URL.")
        private String logo;

        @Schema(description = "Approved Link description.")
        private String description;

        @Schema(description = "Metadata name of the selected LinkGroup.")
        private String groupName;
    }

    @Schema(description = "Application status.")
    public enum Status {
        PENDING,
        APPROVING,
        APPROVED,
        REJECTED
    }

    public enum OriginType {
        FORM,
        COMMENT
    }
}
