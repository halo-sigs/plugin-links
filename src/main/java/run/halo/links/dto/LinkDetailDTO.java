package run.halo.links.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @author LIlGG
 */
@Data
@Schema(description = "Metadata discovered from a remote website URL.")
public class LinkDetailDTO {

    @Schema(description = "Title discovered from the website document.")
    @JsonProperty(value = "title", required = true)
    private String title;

    @Schema(description = "Description discovered from the website meta description.")
    @JsonProperty("description")
    private String description;

    @Schema(description = "Icon URL discovered from the website favicon metadata.")
    @JsonProperty("icon")
    private String icon;

    @Schema(description = "Preview image URL discovered from Open Graph metadata.")
    @JsonProperty("image")
    private String image;
}
