package run.halo.links.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.plugin.ReactiveSettingFetcher;
import run.halo.links.dto.LinkApplicationSettings;
import run.halo.links.endpoint.LinkApplicationSettingsFetcher;
import run.halo.links.extension.LinkApplication;

@ExtendWith(MockitoExtension.class)
class LinkApplicationCapacityServiceTest {

    @Mock
    ReactiveExtensionClient client;

    @Mock
    ReactiveSettingFetcher settingFetcher;

    LinkApplicationCapacityService service;

    @BeforeEach
    void setUp() {
        service = new LinkApplicationCapacityService(client,
            new LinkApplicationSettingsFetcher(settingFetcher));
    }

    @Test
    void shouldUseIndexedPendingCountAtBoundary() {
        givenSettings(100);
        when(client.countBy(eq(LinkApplication.class), any(ListOptions.class)))
            .thenReturn(Mono.just(99L), Mono.just(100L));

        StepVerifier.create(service.isAvailable())
            .expectNext(true)
            .verifyComplete();
        StepVerifier.create(service.isAvailable())
            .expectNext(false)
            .verifyComplete();
    }

    @Test
    void shouldCountPendingApplicationsAcrossOriginsOnly() {
        givenSettings(2);
        var formPending = application(LinkApplication.Status.PENDING,
            LinkApplication.OriginType.FORM);
        var commentPending = application(LinkApplication.Status.PENDING,
            LinkApplication.OriginType.COMMENT);
        var otherStatuses = List.of(
            application(LinkApplication.Status.APPROVING, LinkApplication.OriginType.FORM),
            application(LinkApplication.Status.APPROVED, LinkApplication.OriginType.COMMENT),
            application(LinkApplication.Status.REJECTED, LinkApplication.OriginType.FORM)
        );

        StepVerifier.create(service.isAvailable(List.of(
                formPending,
                otherStatuses.get(0),
                otherStatuses.get(1),
                otherStatuses.get(2)
            )))
            .expectNext(true)
            .verifyComplete();
        StepVerifier.create(service.isAvailable(List.of(formPending, commentPending)))
            .expectNext(false)
            .verifyComplete();
    }

    @Test
    void shouldNotMutateApplicationsWhenCapacityIsLowered() {
        givenSettings(1);
        var pending = application(LinkApplication.Status.PENDING,
            LinkApplication.OriginType.FORM);
        var applications = List.of(pending);

        StepVerifier.create(service.isAvailable(applications))
            .expectNext(false)
            .verifyComplete();

        assertThat(applications).containsExactly(pending);
        assertThat(pending.getSpec().getStatus()).isEqualTo(LinkApplication.Status.PENDING);
    }

    @Test
    void shouldFailClosedWhenSettingsOrCountAreUnavailable() {
        var invalid = enabledSettings(0);
        when(settingFetcher.fetch(LinkApplicationSettingsFetcher.SETTING_GROUP,
            LinkApplicationSettings.class))
            .thenReturn(Mono.just(invalid), Mono.just(enabledSettings(100)));
        when(client.countBy(eq(LinkApplication.class), any(ListOptions.class)))
            .thenReturn(Mono.error(new IllegalStateException("count unavailable")));

        StepVerifier.create(service.isAvailable())
            .expectError(LinkApplicationCapacityService.CapacityUnavailableException.class)
            .verify();
        StepVerifier.create(service.isAvailable())
            .expectErrorMessage("count unavailable")
            .verify();
    }

    private void givenSettings(int capacity) {
        when(settingFetcher.fetch(LinkApplicationSettingsFetcher.SETTING_GROUP,
            LinkApplicationSettings.class)).thenReturn(Mono.just(enabledSettings(capacity)));
    }

    private static LinkApplicationSettings enabledSettings(int capacity) {
        var settings = new LinkApplicationSettings();
        settings.setEnabled(true);
        var security = new LinkApplicationSettings.Security();
        security.setPendingCapacity(BigDecimal.valueOf(capacity));
        settings.setSecurity(security);
        return settings;
    }

    private static LinkApplication application(LinkApplication.Status status,
        LinkApplication.OriginType originType) {
        var application = new LinkApplication();
        var metadata = new Metadata();
        metadata.setName(status.name().toLowerCase() + "-" + originType.name().toLowerCase());
        application.setMetadata(metadata);
        var spec = new LinkApplication.LinkApplicationSpec();
        spec.setStatus(status);
        var origin = new LinkApplication.Origin();
        origin.setType(originType);
        spec.setOrigin(origin);
        application.setSpec(spec);
        return application;
    }
}
