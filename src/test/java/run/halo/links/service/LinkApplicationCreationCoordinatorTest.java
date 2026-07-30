package run.halo.links.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

class LinkApplicationCreationCoordinatorTest {

    @Test
    void shouldSerializeSameKeyAndReleaseEntry() {
        var coordinator = new LinkApplicationCreationCoordinator();
        var firstMayFinish = Sinks.<Void>empty();
        var started = new AtomicInteger();

        var first = coordinator.coordinate("https://example.com/", () -> {
            started.incrementAndGet();
            return firstMayFinish.asMono().thenReturn("first");
        });
        var second = coordinator.coordinate("https://example.com/", () -> {
            started.incrementAndGet();
            return Mono.just("second");
        });

        StepVerifier.create(Mono.zip(first, second))
            .then(() -> {
                assertThat(started).hasValue(1);
                firstMayFinish.tryEmitEmpty();
            })
            .expectNextMatches(result -> result.getT1().equals("first")
                && result.getT2().equals("second"))
            .verifyComplete();
        assertThat(started).hasValue(2);
        assertThat(coordinator.trackedKeyCount()).isZero();
    }

    @Test
    void shouldReleaseEntryAfterFailure() {
        var coordinator = new LinkApplicationCreationCoordinator();

        StepVerifier.create(coordinator.coordinate("key",
                () -> Mono.error(new IllegalStateException("boom"))))
            .expectErrorMessage("boom")
            .verify();

        assertThat(coordinator.trackedKeyCount()).isZero();
    }

    @Test
    void shouldNotSkipActiveCreationWhenMiddleWaiterIsCancelled() {
        var coordinator = new LinkApplicationCreationCoordinator();
        var firstMayFinish = Sinks.<Void>empty();
        var firstStarted = new AtomicBoolean();
        var secondStarted = new AtomicBoolean();
        var thirdStarted = new AtomicBoolean();
        var thirdResult = Sinks.<String>one();

        coordinator.coordinateCreation(() -> {
            firstStarted.set(true);
            return firstMayFinish.asMono().thenReturn("first");
        }).subscribe();
        assertThat(firstStarted).isTrue();

        var secondSubscription = coordinator.coordinateCreation(() -> {
            secondStarted.set(true);
            return Mono.just("second");
        }).subscribe();
        assertThat(secondStarted).isFalse();
        secondSubscription.dispose();

        coordinator.coordinateCreation(() -> {
            thirdStarted.set(true);
            return Mono.just("third");
        }).subscribe(thirdResult::tryEmitValue, thirdResult::tryEmitError);

        assertThat(thirdStarted).isFalse();
        firstMayFinish.tryEmitEmpty();

        StepVerifier.create(thirdResult.asMono())
            .expectNext("third")
            .verifyComplete();
    }
}
