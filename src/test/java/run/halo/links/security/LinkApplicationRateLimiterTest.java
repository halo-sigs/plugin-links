package run.halo.links.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Flux;

class LinkApplicationRateLimiterTest {

    @Test
    void shouldAtomicallyAllowOnlyOneConcurrentRequestPerIp() {
        var limiter = new LinkApplicationRateLimiter(
            Clock.fixed(Instant.parse("2026-07-25T00:00:00Z"), ZoneOffset.UTC));
        var request = org.mockito.Mockito.mock(ServerRequest.class);
        when(request.remoteAddress())
            .thenReturn(java.util.Optional.of(new InetSocketAddress("192.0.2.10", 8080)));

        long admitted = Flux.range(0, 64)
            .parallel(8)
            .runOn(reactor.core.scheduler.Schedulers.parallel())
            .map(ignored -> limiter.isAllowed(request))
            .sequential()
            .filter(Boolean::booleanValue)
            .count()
            .block();

        assertThat(admitted).isEqualTo(1);
        assertThat(limiter.trackedIpCount()).isEqualTo(1);
    }
}
