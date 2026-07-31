package run.halo.links.dto;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import run.halo.links.extension.LinkApplication;
import run.halo.links.service.LinkApplicationService;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Cookie-free link application request.",
    requiredProperties = {"url", "displayName", "challengeId", "captchaCode"})
public class LinkApplicationRestRequest {

    @Schema(description = "Absolute HTTP or HTTPS URL of the site.", requiredMode = REQUIRED)
    private String url;

    @Schema(description = "Human-readable site name.", requiredMode = REQUIRED)
    private String displayName;

    @Schema(description = "Optional logo URL.")
    private String logo;

    @Schema(description = "Optional site description.")
    private String description;

    @Schema(description = "Optional applicant email.")
    private String email;

    @Schema(description = "Optional backlink page URL.")
    private String backlink;

    @ArraySchema(
        arraySchema = @Schema(description = "Optional RSS or Atom feed URLs."),
        schema = @Schema(type = "string", format = "uri")
    )
    private List<String> feedUrls;

    @Schema(description = "Opaque identifier returned by the CAPTCHA operation.",
        requiredMode = REQUIRED)
    private String challengeId;

    @Schema(description = "Answer shown in the CAPTCHA image.", requiredMode = REQUIRED)
    private String captchaCode;

    public String normalizedChallengeId() {
        return normalizeOptional(challengeId);
    }

    public String normalizedCaptchaCode() {
        return normalizeOptional(captchaCode);
    }

    public LinkApplicationService.Submission toSubmission() {
        var origin = new LinkApplication.Origin();
        origin.setType(LinkApplication.OriginType.FORM);
        return new LinkApplicationService.Submission(
            normalizeOptional(url),
            normalizeOptional(displayName),
            normalizeOptional(logo),
            normalizeOptional(description),
            normalizeOptional(email),
            normalizeOptional(backlink),
            normalizeList(feedUrls),
            origin
        );
    }

    private static String normalizeOptional(String value) {
        return StringUtils.isBlank(value) ? null : value.trim();
    }

    private static List<String> normalizeList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
            .filter(StringUtils::isNotBlank)
            .map(String::trim)
            .toList();
    }
}
