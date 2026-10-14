package run.halo.links.service;

import java.net.URI;
import java.net.URISyntaxException;
import org.apache.commons.lang3.StringUtils;

/**
 * Validates image references stored for logos and homepage covers.
 */
public final class LinkImageReference {

    private LinkImageReference() {
    }

    public static boolean isValid(String value) {
        if (StringUtils.isBlank(value)) {
            return true;
        }
        if (LinkUrlCanonicalizer.canonicalKey(value).isPresent()) {
            return true;
        }
        try {
            var uri = new URI(value.trim());
            return !uri.isAbsolute()
                && uri.getScheme() == null
                && uri.getRawAuthority() == null
                && uri.getRawPath() != null
                && uri.getRawPath().startsWith("/")
                && !uri.getRawPath().startsWith("//");
        } catch (URISyntaxException | IllegalArgumentException e) {
            return false;
        }
    }
}
