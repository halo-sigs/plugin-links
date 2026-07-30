package run.halo.links.service;

import static run.halo.app.extension.index.query.Queries.equal;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.links.dto.LinkApplicationSettings;
import run.halo.links.endpoint.LinkApplicationSettingsFetcher;
import run.halo.links.extension.LinkApplication;

/**
 * Evaluates the shared pending LinkApplication capacity.
 */
@Component
@RequiredArgsConstructor
public class LinkApplicationCapacityService {

    private final ReactiveExtensionClient client;
    private final LinkApplicationSettingsFetcher settingsFetcher;

    public Mono<Boolean> isAvailable() {
        return requireEnabledSettings()
            .flatMap(settings -> client.countBy(LinkApplication.class, pendingOptions())
                .map(pendingCount -> pendingCount < settings.pendingCapacity()));
    }

    public Mono<Boolean> isAvailable(List<LinkApplication> applications) {
        return requireEnabledSettings()
            .map(settings -> pendingCount(applications) < settings.pendingCapacity());
    }

    private Mono<LinkApplicationSettings> requireEnabledSettings() {
        return settingsFetcher.fetch()
            .flatMap(settings -> settings.applicationEnabled()
                ? Mono.just(settings)
                : Mono.error(new CapacityUnavailableException()));
    }

    private static long pendingCount(List<LinkApplication> applications) {
        return applications.stream()
            .filter(application -> application.getSpec() != null)
            .filter(application ->
                application.getSpec().getStatus() == LinkApplication.Status.PENDING)
            .count();
    }

    private static ListOptions pendingOptions() {
        return ListOptions.builder()
            .andQuery(equal("spec.status", LinkApplication.Status.PENDING.name()))
            .build();
    }

    public static class CapacityUnavailableException extends RuntimeException {

        public CapacityUnavailableException() {
            super("Pending application capacity is unavailable");
        }
    }
}
