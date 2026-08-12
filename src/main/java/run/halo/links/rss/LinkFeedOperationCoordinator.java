package run.halo.links.rss;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
public class LinkFeedOperationCoordinator {

    private final Map<String, OperationLock> operations = new ConcurrentHashMap<>();

    public <T> Mono<T> coordinate(String linkName, Supplier<Mono<T>> operation) {
        return Mono.usingWhen(
            Mono.fromCallable(() -> acquire(linkName)).subscribeOn(Schedulers.boundedElastic()),
            ignored -> Mono.defer(operation),
            lease -> Mono.fromRunnable(() -> release(lease))
        );
    }

    public <T> T coordinateBlocking(String linkName, Supplier<T> operation) {
        Lease lease = acquire(linkName);
        try {
            return operation.get();
        } finally {
            release(lease);
        }
    }

    private Lease acquire(String linkName) {
        OperationLock operationLock = operations.compute(linkName, (ignored, current) -> {
            OperationLock selected = current == null ? new OperationLock() : current;
            selected.references++;
            return selected;
        });
        try {
            operationLock.semaphore.acquire();
            return new Lease(linkName, operationLock);
        } catch (InterruptedException error) {
            releaseReference(linkName, operationLock);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for an RSS Link operation.",
                error);
        }
    }

    private void release(Lease lease) {
        lease.operationLock.semaphore.release();
        releaseReference(lease.linkName, lease.operationLock);
    }

    private void releaseReference(String linkName, OperationLock operationLock) {
        operations.computeIfPresent(linkName, (ignored, current) -> {
            if (current != operationLock) {
                return current;
            }
            current.references--;
            return current.references == 0 ? null : current;
        });
    }

    private static final class OperationLock {

        private final Semaphore semaphore = new Semaphore(1);
        private int references;
    }

    private record Lease(String linkName, OperationLock operationLock) {
    }
}
