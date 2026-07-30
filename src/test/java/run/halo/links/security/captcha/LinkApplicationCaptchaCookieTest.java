package run.halo.links.security.captcha;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import run.halo.links.support.ServerRequestFixtures;

class LinkApplicationCaptchaCookieTest {

    private final LinkApplicationCaptchaCookie cookies = new LinkApplicationCaptchaCookie();

    @Test
    void shouldResolveOpaqueIdentifierAndIssueScopedHttpOnlyCookie() {
        var request = ServerRequestFixtures.request("https", "192.0.2.10",
            Map.of(LinkApplicationCaptchaCookie.COOKIE_NAME, "opaque-id"), Map.of());

        var cookie = cookies.issue("new-opaque-id", request);

        assertThat(cookies.resolve(request)).isEqualTo("opaque-id");
        assertThat(cookie.getName()).isEqualTo(LinkApplicationCaptchaCookie.COOKIE_NAME);
        assertThat(cookie.getValue()).isEqualTo("new-opaque-id");
        assertThat(cookie.getPath()).isEqualTo("/links");
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofMinutes(5));
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
        assertThat(cookie.isSecure()).isTrue();
    }

    @Test
    void shouldOnlySetSecureForEffectiveHttpsRequest() {
        var request = ServerRequestFixtures.request("http", "192.0.2.10", Map.of(), Map.of());

        assertThat(cookies.issue("new-id", request).isSecure()).isFalse();
        assertThat(cookies.expire(request).isSecure()).isFalse();
    }

    @Test
    void shouldExpireCookieAfterEveryVerificationAttempt() {
        var request = ServerRequestFixtures.request("https", "192.0.2.10", Map.of(), Map.of());

        var expired = cookies.expire(request);

        assertThat(expired.getValue()).isEmpty();
        assertThat(expired.getMaxAge()).isEqualTo(Duration.ZERO);
        assertThat(expired.getPath()).isEqualTo("/links");
        assertThat(expired.isHttpOnly()).isTrue();
        assertThat(expired.getSameSite()).isEqualTo("Lax");
    }
}
