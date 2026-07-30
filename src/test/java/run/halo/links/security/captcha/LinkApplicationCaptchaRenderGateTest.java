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

    @Test
    void shouldHoldSlotUntilCancelledRenderingActuallyStops() throws Exception {
        var gate = new LinkApplicationCaptchaRenderGate(1, Schedulers.boundedElastic());
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var exited = new CountDownLatch(1);

        var running = gate.execute(() -> {
            entered.countDown();
            try {
                boolean released = false;
                while (!released) {
                    try {
                        release.await();
                        released = true;
                    } catch (InterruptedException ignored) {
                        // Simulate Java2D/ImageIO work that does not terminate on interruption.
                    }
                }
                return true;
            } finally {
                exited.countDown();
            }
        }).subscribe();

        assertThat(entered.await(2, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        running.dispose();
        try {
            assertThatThrownBy(() -> gate.execute(() -> true)
                .block(Duration.ofMillis(250)))
                .isInstanceOf(LinkApplicationCaptchaRenderGate.BusyException.class);
        } finally {
            release.countDown();
        }
        assertThat(exited.await(2, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        assertThat(gate.execute(() -> "ok").block()).isEqualTo("ok");
    }

    @Test
    void shouldEventuallyReleaseSlotWhenCancelledBeforeRenderingStarts() throws Exception {
        var scheduler = Schedulers.newSingle("captcha-render-gate-test");
        var schedulerBlocked = new CountDownLatch(1);
        var releaseScheduler = new CountDownLatch(1);
        scheduler.schedule(() -> {
            schedulerBlocked.countDown();
            try {
                releaseScheduler.await();
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            }
        });

        try {
            assertThat(schedulerBlocked.await(2, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            var gate = new LinkApplicationCaptchaRenderGate(1, scheduler);
            var queued = gate.execute(() -> true).subscribe();

            queued.dispose();
            releaseScheduler.countDown();

            boolean slotBecameAvailable = false;
            for (int attempt = 0; attempt < 200 && !slotBecameAvailable; attempt++) {
                try {
                    slotBecameAvailable = Boolean.TRUE.equals(gate.execute(() -> true).block());
                } catch (LinkApplicationCaptchaRenderGate.BusyException ignored) {
                    Thread.sleep(10);
                }
            }
            assertThat(slotBecameAvailable).isTrue();
        } finally {
            releaseScheduler.countDown();
            scheduler.dispose();
        }
    }
}
