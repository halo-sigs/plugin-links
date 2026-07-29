package run.halo.links.security.captcha;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import run.halo.links.support.MutableClock;

class LinkApplicationCaptchaStoreTest {

    @Test
    void shouldTrimCompareCaseInsensitivelyAndConsumeEveryAttempt() {
        var store = store(10);

        String correct = store.issue("AbC23", null);
        assertThat(store.verifyAndConsume(correct, " abc23 ")).isTrue();
        assertThat(store.verifyAndConsume(correct, "AbC23")).isFalse();

        String incorrect = store.issue("AbC23", null);
        assertThat(store.verifyAndConsume(incorrect, "xxxxx")).isFalse();
        assertThat(store.verifyAndConsume(incorrect, "AbC23")).isFalse();

        String malformed = store.issue("AbC23", null);
        assertThat(store.verifyAndConsume(malformed, "ＡＢＣ２３")).isFalse();
        assertThat(store.verifyAndConsume(malformed, "AbC23")).isFalse();
    }

    @Test
    void shouldExpireAtFiveMinuteBoundaryAndCleanExpiredEntriesLazily() {
        var clock = new MutableClock(Instant.parse("2026-07-29T00:00:00Z"));
        var store = store(clock, 1);
        String expired = store.issue("ABCDE", null);

        clock.advance(Duration.ofMinutes(5));

        assertThat(store.verifyAndConsume(expired, "ABCDE")).isFalse();
        assertThat(store.issue("FGHJK", null)).isNotBlank();
        assertThat(store.size()).isEqualTo(1);
    }

    @Test
    void shouldNotEvictLiveChallengeWhenCapacityIsFull() {
        assertThat(LinkApplicationCaptchaStore.MAX_CHALLENGES).isEqualTo(10_000);
        var store = store(1);
        String first = store.issue("ABCDE", null);

        assertThatThrownBy(() -> store.issue("FGHJK", null))
            .isInstanceOf(LinkApplicationCaptchaStore.CapacityExceededException.class);
        assertThat(store.verifyAndConsume(first, "ABCDE")).isTrue();
    }

    @Test
    void shouldGenerateOpaqueCryptographicIdentifier() {
        var store = new LinkApplicationCaptchaStore();

        String identifier = store.issue("ABCDE", null);

        assertThat(identifier)
            .hasSize(32)
            .matches("[A-Za-z0-9_-]+")
            .isNotEqualTo("ABCDE");
    }

    @Test
    void shouldInvalidatePreviousCookieChallengeOnRefresh() {
        var store = store(1);
        String previous = store.issue("ABCDE", null);

        String current = store.issue("FGHJK", previous);

        assertThat(store.verifyAndConsume(previous, "ABCDE")).isFalse();
        assertThat(store.verifyAndConsume(current, "FGHJK")).isTrue();
    }

    @Test
    void shouldAllowOnlyOneConcurrentVerificationWinner() {
        var store = store(10);
        String id = store.issue("ABCDE", null);

        long successes = Flux.range(0, 64)
            .parallel(8)
            .runOn(Schedulers.parallel())
            .map(ignored -> store.verifyAndConsume(id, "abcde"))
            .sequential()
            .filter(Boolean::booleanValue)
            .count()
            .block();

        assertThat(successes).isEqualTo(1);
    }

    @Test
    void shouldClearAllChallengesOnShutdown() {
        var store = store(10);
        store.issue("ABCDE", null);

        store.clear();

        assertThat(store.size()).isZero();
    }

    private static LinkApplicationCaptchaStore store(int capacity) {
        return store(new MutableClock(Instant.parse("2026-07-29T00:00:00Z")), capacity);
    }

    private static LinkApplicationCaptchaStore store(MutableClock clock, int capacity) {
        var sequence = new AtomicInteger();
        return new LinkApplicationCaptchaStore(clock, capacity,
            () -> "challenge-" + sequence.incrementAndGet());
    }
}
