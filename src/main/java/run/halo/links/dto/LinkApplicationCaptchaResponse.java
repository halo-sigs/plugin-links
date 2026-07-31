package run.halo.links.dto;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Explicit CAPTCHA challenge for cookie-free clients.")
public record LinkApplicationCaptchaResponse(
    @Schema(description = "Opaque challenge identifier.", requiredMode = REQUIRED)
    String challengeId,

    @Schema(description = "PNG image encoded as a data URL.", requiredMode = REQUIRED,
        example = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAA")
    String image,

    @Schema(description = "Challenge lifetime in seconds.", requiredMode = REQUIRED,
        example = "300")
    long expiresInSeconds
) {
}
