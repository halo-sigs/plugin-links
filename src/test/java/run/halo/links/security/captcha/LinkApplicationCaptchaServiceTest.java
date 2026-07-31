package run.halo.links.security.captcha;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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

        StepVerifier.create(service.issue(request, null))
            .assertNext(result -> {
                assertThat(result.status())
                    .isEqualTo(LinkApplicationCaptchaService.IssueStatus.RATE_LIMITED);
                assertThat(result.retryAfterSeconds()).isEqualTo(37);
            })
            .verifyComplete();
        verify(generator, never()).generate();
    }

    @Test
    void shouldIssueAndVerifyTransportNeutralChallenge() {
        var request = ServerRequestFixtures.request("192.0.2.10");
        when(limiter.admit(request)).thenReturn(
            new LinkApplicationCaptchaGenerationLimiter.Admission(true, 0));
        when(generator.generate()).thenReturn(
            new LinkApplicationCaptchaGenerator.GeneratedCaptcha("ABCDE", new byte[] {1, 2, 3}));
        var service = service(limiter, generator);

        var result = service.issue(request, null).block();

        assertThat(result.status()).isEqualTo(LinkApplicationCaptchaService.IssueStatus.ISSUED);
        assertThat(result.identifier()).isEqualTo("id-1");
        assertThat(result.png()).containsExactly(1, 2, 3);
        assertThat(result.expiresInSeconds()).isEqualTo(300);
        assertThat(service.verifyChallenge(result.identifier(), " abcde ")).isTrue();
        assertThat(service.verifyChallenge(result.identifier(), "ABCDE")).isFalse();
    }

    @Test
    void shouldKeepIndependentCookielessChallengesValid() {
        var request = ServerRequestFixtures.request("192.0.2.10");
        when(limiter.admit(request)).thenReturn(
            new LinkApplicationCaptchaGenerationLimiter.Admission(true, 0));
        when(generator.generate()).thenReturn(
            new LinkApplicationCaptchaGenerator.GeneratedCaptcha("ABCDE", new byte[] {1}),
            new LinkApplicationCaptchaGenerator.GeneratedCaptcha("FGHJK", new byte[] {2}));
        var service = service(limiter, generator);

        var first = service.issue(request, null).block();
        var second = service.issue(request, null).block();

        assertThat(first.identifier()).isNotEqualTo(second.identifier());
        assertThat(service.verifyChallenge(first.identifier(), "ABCDE")).isTrue();
        assertThat(service.verifyChallenge(second.identifier(), "FGHJK")).isTrue();
    }

    @Test
    void shouldInvalidateExplicitPreviousChallengeAfterSuccessfulDrawing() {
        var request = ServerRequestFixtures.request("192.0.2.10");
        when(limiter.admit(request)).thenReturn(
            new LinkApplicationCaptchaGenerationLimiter.Admission(true, 0));
        when(generator.generate()).thenReturn(
            new LinkApplicationCaptchaGenerator.GeneratedCaptcha("ABCDE", new byte[] {1}),
            new LinkApplicationCaptchaGenerator.GeneratedCaptcha("FGHJK", new byte[] {2}));
        var service = service(limiter, generator);
        var previous = service.issue(request, null).block();

        var current = service.issue(request, previous.identifier()).block();

        assertThat(service.verifyChallenge(previous.identifier(), "ABCDE")).isFalse();
        assertThat(service.verifyChallenge(current.identifier(), "FGHJK")).isTrue();
    }

    @Test
    void shouldFailClosedWhenRenderingOrStoreFails() {
        var request = ServerRequestFixtures.request("192.0.2.10");
        when(limiter.admit(request)).thenReturn(
            new LinkApplicationCaptchaGenerationLimiter.Admission(true, 0));
        when(generator.generate()).thenThrow(new IllegalStateException("render failed"));
        var service = service(limiter, generator);

        StepVerifier.create(service.issue(request, null))
            .assertNext(result -> assertThat(result.status())
                .isEqualTo(LinkApplicationCaptchaService.IssueStatus.UNAVAILABLE))
            .verifyComplete();
    }

    private static LinkApplicationCaptchaService service(
        LinkApplicationCaptchaGenerationLimiter limiter,
        LinkApplicationCaptchaGenerator generator) {
        var ids = new AtomicInteger();
        var store = new LinkApplicationCaptchaStore(
            new MutableClock(Instant.parse("2026-07-29T00:00:00Z")), 10,
            () -> "id-" + ids.incrementAndGet());
        return new LinkApplicationCaptchaService(limiter, generator, store,
            new LinkApplicationCaptchaRenderGate(4, Schedulers.immediate()));
    }
}
