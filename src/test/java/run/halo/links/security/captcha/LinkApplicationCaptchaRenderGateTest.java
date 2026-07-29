package run.halo.links.security.captcha;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

class LinkApplicationCaptchaRenderGateTest {

    @Test
    void shouldRunAtMostFourDrawingsAndRejectExcessImmediately() throws Exception {
        var gate = new LinkApplicationCaptchaRenderGate(4, Schedulers.boundedElastic());
        var entered = new CountDownLatch(4);
        var release = new CountDownLatch(1);
        var active = new AtomicInteger();
        var maximum = new AtomicInteger();

        var running = reactor.core.publisher.Flux.range(0, 4)
            .flatMap(ignored -> gate.execute(() -> {
                int current = active.incrementAndGet();
                maximum.accumulateAndGet(current, Math::max);
                entered.countDown();
                release.await();
                active.decrementAndGet();
                return true;
            }))
            .subscribe();

        assertThat(entered.await(2, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        assertThatThrownBy(() -> gate.execute(() -> true)
            .block(Duration.ofMillis(250)))
            .isInstanceOf(LinkApplicationCaptchaRenderGate.BusyException.class);

        release.countDown();
        running.dispose();
        assertThat(maximum).hasValue(4);
    }

    @Test
    void shouldReleaseSlotAfterRenderingFailure() {
        var gate = new LinkApplicationCaptchaRenderGate(1, Schedulers.immediate());

        assertThatThrownBy(() -> gate.execute(() -> {
            throw new IllegalStateException("render failed");
        }).block()).isInstanceOf(IllegalStateException.class);
        assertThat(gate.execute(() -> "ok").block()).isEqualTo("ok");
    }
}
