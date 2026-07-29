package run.halo.links.security.captcha;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.Font;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;
import run.halo.links.support.MutableClock;
import run.halo.links.support.ServerRequestFixtures;

@ExtendWith(MockitoExtension.class)
class LinkApplicationCaptchaServiceTest {

    @Mock
    LinkApplicationCaptchaGenerationLimiter limiter;

    @Mock
    LinkApplicationCaptchaGenerator generator;

    @Test
    void shouldRejectRateLimitedRequestBeforeDrawing() {
        var request = ServerRequestFixtures.request("192.0.2.10");
        when(limiter.admit(request)).thenReturn(
            new LinkApplicationCaptchaGenerationLimiter.Admission(false, 37));
        var service = service(limiter, generator);

        StepVerifier.create(service.issue(request))
            .assertNext(result -> {
                assertThat(result.status())
                    .isEqualTo(LinkApplicationCaptchaService.IssueStatus.RATE_LIMITED);
                assertThat(result.retryAfterSeconds()).isEqualTo(37);
            })
            .verifyComplete();
        verify(generator, never()).generate();
    }

    @Test
    void shouldIssueImageAndReplacePreviousChallengeOnlyAfterSuccessfulDrawing() {
        var request = ServerRequestFixtures.request("https", "192.0.2.10",
            Map.of(LinkApplicationCaptchaCookie.COOKIE_NAME, "previous"), Map.of());
        when(limiter.admit(request)).thenReturn(
            new LinkApplicationCaptchaGenerationLimiter.Admission(true, 0));
        when(generator.generate()).thenReturn(
            new LinkApplicationCaptchaGenerator.GeneratedCaptcha("ABCDE", new byte[] {1, 2, 3}));
        var service = service(limiter, generator);

        var result = service.issue(request).block();

        assertThat(result.status()).isEqualTo(LinkApplicationCaptchaService.IssueStatus.ISSUED);
        assertThat(result.png()).containsExactly(1, 2, 3);
        assertThat(result.cookie().isSecure()).isTrue();
        assertThat(service.verify(requestWithCookie(result.cookie().getValue()), "abcde").valid())
            .isTrue();
    }

    @Test
    void shouldFailClosedWhenRenderingOrStoreFails() {
        var request = ServerRequestFixtures.request("192.0.2.10");
        when(limiter.admit(request)).thenReturn(
            new LinkApplicationCaptchaGenerationLimiter.Admission(true, 0));
        when(generator.generate()).thenThrow(new IllegalStateException("render failed"));
        var service = service(limiter, generator);

        StepVerifier.create(service.issue(request))
            .assertNext(result -> assertThat(result.status())
                .isEqualTo(LinkApplicationCaptchaService.IssueStatus.UNAVAILABLE))
            .verifyComplete();
    }

    @Test
    void shouldConsumeMissingWrongAndCorrectAnswersAndAlwaysExpireCookie() {
        var request = ServerRequestFixtures.request("192.0.2.10");
        when(limiter.admit(request)).thenReturn(
            new LinkApplicationCaptchaGenerationLimiter.Admission(true, 0));
        when(generator.generate()).thenReturn(
            new LinkApplicationCaptchaGenerator.GeneratedCaptcha("ABCDE", new byte[] {1}));
        var service = service(limiter, generator);
        var issued = service.issue(request).block();
        var cookieRequest = requestWithCookie(issued.cookie().getValue());

        var wrong = service.verify(cookieRequest, "XXXXX");

        assertThat(wrong.valid()).isFalse();
        assertThat(wrong.expiredCookie().getMaxAge()).isEqualTo(java.time.Duration.ZERO);
        assertThat(service.verify(cookieRequest, "ABCDE").valid()).isFalse();
        assertThat(service.verify(request, null).valid()).isFalse();
    }

    private static LinkApplicationCaptchaService service(
        LinkApplicationCaptchaGenerationLimiter limiter,
        LinkApplicationCaptchaGenerator generator) {
        var ids = new AtomicInteger();
        var store = new LinkApplicationCaptchaStore(
            new MutableClock(Instant.parse("2026-07-29T00:00:00Z")), 10,
            () -> "id-" + ids.incrementAndGet());
        return new LinkApplicationCaptchaService(limiter, generator, store,
            new LinkApplicationCaptchaCookie(),
            new LinkApplicationCaptchaRenderGate(4, Schedulers.immediate()));
    }

    private static org.springframework.web.reactive.function.server.ServerRequest requestWithCookie(
        String identifier) {
        return ServerRequestFixtures.request("http", "192.0.2.10",
            Map.of(LinkApplicationCaptchaCookie.COOKIE_NAME, identifier), Map.of());
    }
}
