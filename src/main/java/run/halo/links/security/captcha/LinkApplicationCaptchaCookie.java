package run.halo.links.security.captcha;

import java.time.Duration;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;

@Component
public class LinkApplicationCaptchaCookie {

    static final String COOKIE_NAME = "link_application_captcha";
    private static final String COOKIE_PATH = "/links";

    public String resolve(ServerRequest request) {
        var cookie = request.cookies().getFirst(COOKIE_NAME);
        return cookie == null ? null : cookie.getValue();
    }

    public ResponseCookie issue(String identifier, ServerRequest request) {
        return cookie(identifier, LinkApplicationCaptchaStore.CHALLENGE_TTL, request);
    }

    public ResponseCookie expire(ServerRequest request) {
        return cookie("", Duration.ZERO, request);
    }

    private static ResponseCookie cookie(String value, Duration maxAge, ServerRequest request) {
        return ResponseCookie.from(COOKIE_NAME, value)
            .path(COOKIE_PATH)
            .maxAge(maxAge)
            .httpOnly(true)
            .sameSite("Lax")
            .secure("https".equalsIgnoreCase(request.uri().getScheme()))
            .build();
    }
}
