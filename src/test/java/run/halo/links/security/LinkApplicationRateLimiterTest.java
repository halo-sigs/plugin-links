package run.halo.links.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Flux;
import run.halo.links.support.MutableClock;

class LinkApplicationRateLimiterTest {

    @Test
    void shouldReportCeilingRoundedRetrySecondsAndAllowExactBoundary() {
        var clock = new MutableClock(Instant.parse("2026-07-25T00:00:00Z"));
        var limiter = new LinkApplicationRateLimiter(clock);
        var request = requestFrom("192.0.2.10");

        assertThat(limiter.admit(request).allowed()).isTrue();

        clock.advance(Duration.ofMillis(500));
        var rejected = limiter.admit(request);
        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.retryAfterSeconds()).isEqualTo(60);

        clock.advance(Duration.ofMillis(59_500));
        assertThat(limiter.admit(request).allowed()).isTrue();
    }

    @Test
    void shouldAtomicallyAllowOnlyOneConcurrentRequestPerIp() {
        var limiter = new LinkApplicationRateLimiter(
            Clock.fixed(Instant.parse("2026-07-25T00:00:00Z"), ZoneOffset.UTC));
        var request = requestFrom("192.0.2.10");

        long admitted = Flux.range(0, 64)
            .parallel(8)
            .runOn(reactor.core.scheduler.Schedulers.parallel())
            .map(ignored -> limiter.admit(request).allowed())
            .sequential()
            .filter(Boolean::booleanValue)
            .count()
            .block();

        assertThat(admitted).isEqualTo(1);
        assertThat(limiter.trackedIpCount()).isEqualTo(1);
    }

    @Test
    void shouldRemoveExpiredEntriesBeforeAdmittingANewIpAtTheBound() {
        var clock = new MutableClock(Instant.parse("2026-07-25T00:00:00Z"));
        var limiter = new LinkApplicationRateLimiter(clock);
        for (int i = 0; i < 10_000; i++) {
            assertThat(limiter.admit(requestFrom("198.18." + (i / 256) + "." + (i % 256)))
                .allowed()).isTrue();
        }

        clock.advance(Duration.ofMinutes(1));
        assertThat(limiter.admit(requestFrom("192.0.2.10")).allowed()).isTrue();

        assertThat(limiter.trackedIpCount()).isEqualTo(1);
    }

    private static ServerRequest requestFrom(String ip) {
        var request = org.mockito.Mockito.mock(ServerRequest.class);
        when(request.remoteAddress())
            .thenReturn(java.util.Optional.of(new InetSocketAddress(ip, 8080)));
        return request;
    }
}
