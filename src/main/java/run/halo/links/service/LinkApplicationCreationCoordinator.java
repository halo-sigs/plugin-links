package run.halo.links.service;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

/**
 * Serializes application creation by canonical URL within one plugin process.
 */
@Component
public class LinkApplicationCreationCoordinator {

    private final Map<String, Entry> entries = new HashMap<>();

    public <T> Mono<T> coordinate(String key, Supplier<Mono<T>> work) {
        return Mono.defer(() -> {
            var completion = Sinks.<Void>empty();
            Mono<Void> predecessor;
            Entry entry;
            synchronized (entries) {
                entry = entries.computeIfAbsent(key, ignored -> new Entry());
                predecessor = entry.tail;
                entry.tail = completion.asMono();
                entry.references++;
            }
            return predecessor
                .onErrorResume(error -> Mono.empty())
                .then(Mono.defer(work))
                .doFinally(signalType -> {
                    completion.tryEmitEmpty();
                    release(key, entry);
                });
        });
    }

    int trackedKeyCount() {
        synchronized (entries) {
            return entries.size();
        }
    }

    private void release(String key, Entry entry) {
        synchronized (entries) {
            entry.references--;
            if (entry.references == 0) {
                entries.remove(key, entry);
            }
        }
    }

    private static final class Entry {
        private Mono<Void> tail = Mono.empty();
        private int references;
    }
}
