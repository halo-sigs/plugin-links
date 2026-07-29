package run.halo.links.security.captcha;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import run.halo.links.support.MutableClock;
import run.halo.links.support.ServerRequestFixtures;

class LinkApplicationCaptchaGenerationLimiterTest {

    @Test
    void shouldAllowTenImagesPerIpAndReturnRetryAfter() {
        assertThat(LinkApplicationCaptchaGenerationLimiter.MAX_TRACKED_IPS).isEqualTo(10_000);
        var clock = new MutableClock(Instant.parse("2026-07-29T00:00:00Z"));
        var limiter = new LinkApplicationCaptchaGenerationLimiter(clock, 10_000);
        var request = ServerRequestFixtures.request("192.0.2.10");

        for (int index = 0; index < 10; index++) {
            assertThat(limiter.admit(request).allowed()).isTrue();
        }
        assertThat(limiter.admit(request))
            .extracting(
                LinkApplicationCaptchaGenerationLimiter.Admission::allowed,
                LinkApplicationCaptchaGenerationLimiter.Admission::retryAfterSeconds)
            .containsExactly(false, 60L);

        clock.advance(Duration.ofSeconds(31));
        assertThat(limiter.admit(request).retryAfterSeconds()).isEqualTo(29);
        clock.advance(Duration.ofSeconds(29));
        assertThat(limiter.admit(request).allowed()).isTrue();
    }

    @Test
    void shouldCleanExpiredEntriesAndEvictOldestTrackingEntryAtCapacity() {
        var clock = new MutableClock(Instant.parse("2026-07-29T00:00:00Z"));
        var limiter = new LinkApplicationCaptchaGenerationLimiter(clock, 2);
        limiter.admit(ServerRequestFixtures.request("192.0.2.1"));
        clock.advance(Duration.ofSeconds(1));
        limiter.admit(ServerRequestFixtures.request("192.0.2.2"));
        limiter.admit(ServerRequestFixtures.request("192.0.2.3"));

        assertThat(limiter.trackedIpCount()).isEqualTo(2);
        assertThat(limiter.isTracked("192.0.2.1")).isFalse();

        clock.advance(Duration.ofMinutes(1));
        limiter.admit(ServerRequestFixtures.request("192.0.2.4"));
        assertThat(limiter.trackedIpCount()).isEqualTo(1);
    }

    @Test
    void shouldUseRemoteAddressInsteadOfForwardingHeaders() {
        var limiter = new LinkApplicationCaptchaGenerationLimiter(
            new MutableClock(Instant.parse("2026-07-29T00:00:00Z")), 10_000);
        var first = ServerRequestFixtures.request("http", "192.0.2.10", Map.of(),
            Map.of("X-Forwarded-For", "198.51.100.1"));
        var second = ServerRequestFixtures.request("http", "192.0.2.10", Map.of(),
            Map.of("X-Forwarded-For", "203.0.113.1"));

        for (int index = 0; index < 10; index++) {
            assertThat(limiter.admit(first).allowed()).isTrue();
        }
        assertThat(limiter.admit(second).allowed()).isFalse();
    }
}
