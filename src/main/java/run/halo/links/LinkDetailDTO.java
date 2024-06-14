package run.halo.links;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * @author LIlGG
 */
@Data
public class LinkDetailDTO {
    
    @JsonProperty(value = "title", required = true)
    private String title;
    
    @JsonProperty("description")
    private String description;

    @JsonProperty("icon")
    private String icon;

    @JsonProperty("image")
    private String image;
}
