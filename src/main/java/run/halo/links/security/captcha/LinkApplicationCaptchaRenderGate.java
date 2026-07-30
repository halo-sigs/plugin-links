package run.halo.links.security.captcha;

import java.util.concurrent.Callable;
import java.util.concurrent.Semaphore;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

@Component
public class LinkApplicationCaptchaRenderGate {

    private static final int MAX_CONCURRENT_RENDERINGS = 4;

    private final Semaphore slots;
    private final Scheduler scheduler;

    public LinkApplicationCaptchaRenderGate() {
        this(MAX_CONCURRENT_RENDERINGS, Schedulers.boundedElastic());
    }

    LinkApplicationCaptchaRenderGate(int slots, Scheduler scheduler) {
        this.slots = new Semaphore(slots);
        this.scheduler = scheduler;
    }

    public <T> Mono<T> execute(Callable<T> rendering) {
        return Mono.defer(() -> {
            if (!slots.tryAcquire()) {
                return Mono.error(new BusyException());
            }
            return Mono.fromCallable(rendering)
                .subscribeOn(scheduler)
                .doFinally(signalType -> slots.release())
                // Keep the permit until the callable exits, even if the client cancels.
                .cache();
        });
    }

    public static final class BusyException extends RuntimeException {
    }
}
