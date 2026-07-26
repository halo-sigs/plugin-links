package run.halo.links.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.links.extension.LinkApplication;
import run.halo.app.plugin.PluginContext;
import run.halo.app.plugin.ReactiveSettingFetcher;
import run.halo.links.dto.LinkApplicationSettings;
import run.halo.links.endpoint.LinkApplicationSettingsFetcher;
import run.halo.links.finders.LinkFinder;
import run.halo.links.security.LinkApplicationRateLimiter;
import run.halo.links.service.LinkApplicationService;
import run.halo.links.service.LinkPublicQueryService;

@ExtendWith(MockitoExtension.class)
class LinkRouterTest {

    @Mock
    LinkFinder linkFinder;

    @Mock
    LinkPublicQueryService linkPublicQueryService;

    @Mock
    PluginContext pluginContext;

    @Mock
    ReactiveSettingFetcher settingFetcher;

    @Mock
    LinkApplicationService applicationService;

    @Mock
    LinkApplicationSettingsFetcher applicationSettingsFetcher;

    @Mock
    LinkApplicationRateLimiter rateLimiter;

    @Test
    void shouldReadTitleFromBaseSettings() {
        LinkBaseSettings settings = new LinkBaseSettings();
        settings.setTitle("友链");
        when(settingFetcher.fetch(LinkRouter.BASE_SETTING_GROUP, LinkBaseSettings.class))
            .thenReturn(Mono.just(settings));

        StepVerifier.create(router().getLinkTitle())
            .assertNext(title -> assertThat(title).isEqualTo("友链"))
            .verifyComplete();
    }

    @Test
    void shouldUseDefaultTitleWhenSettingsAreMissing() {
        when(settingFetcher.fetch(LinkRouter.BASE_SETTING_GROUP, LinkBaseSettings.class))
            .thenReturn(Mono.empty());

        StepVerifier.create(router().getLinkTitle())
            .assertNext(title -> assertThat(title).isEqualTo(LinkBaseSettings.DEFAULT_TITLE))
            .verifyComplete();
    }

    @Test
    void shouldUseDefaultTitleWhenTitleIsBlank() {
        LinkBaseSettings settings = new LinkBaseSettings();
        settings.setTitle(" ");
        when(settingFetcher.fetch(LinkRouter.BASE_SETTING_GROUP, LinkBaseSettings.class))
            .thenReturn(Mono.just(settings));

        StepVerifier.create(router().getLinkTitle())
            .assertNext(title -> assertThat(title).isEqualTo(LinkBaseSettings.DEFAULT_TITLE))
            .verifyComplete();
    }

    @Test
    void shouldUseDefaultTitleWhenSettingsCannotBeLoaded() {
        when(settingFetcher.fetch(LinkRouter.BASE_SETTING_GROUP, LinkBaseSettings.class))
            .thenReturn(Mono.error(new IllegalStateException()));

        StepVerifier.create(router().getLinkTitle())
            .assertNext(title -> assertThat(title).isEqualTo(LinkBaseSettings.DEFAULT_TITLE))
            .verifyComplete();
    }

    @Test
    void shouldCreateFormOriginApplicationThroughSharedService() {
        when(applicationSettingsFetcher.fetch()).thenReturn(Mono.just(enabledSettings()));
        when(rateLimiter.isAllowed(any())).thenReturn(true);
        when(applicationService.create(any())).thenReturn(Mono.just(
            new LinkApplicationService.CreateResult(
                LinkApplicationService.CreateStatus.CREATED,
                null, null, null, null
            )
        ));
        var client = WebTestClient.bindToRouterFunction(router().linkTemplateRoute()).build();

        client.post()
            .uri("/links/apply")
            .body(BodyInserters.fromFormData("url", "https://example.com")
                .with("displayName", "Example"))
            .exchange()
            .expectStatus().is3xxRedirection()
            .expectHeader().valueEquals("Location", "/links?applied=success");

        var submission = ArgumentCaptor.forClass(LinkApplicationService.Submission.class);
        verify(applicationService).create(submission.capture());
        assertThat(submission.getValue().origin().getType())
            .isEqualTo(LinkApplication.OriginType.FORM);
    }

    @Test
    void shouldGateDisabledFormBeforeRateLimitingOrValidation() {
        when(applicationSettingsFetcher.fetch())
            .thenReturn(Mono.just(LinkApplicationSettings.defaults()));
        var client = WebTestClient.bindToRouterFunction(router().linkTemplateRoute()).build();

        client.post()
            .uri("/links/apply")
            .body(BodyInserters.fromFormData("url", "not-a-url"))
            .exchange()
            .expectStatus().is3xxRedirection()
            .expectHeader().valueEquals("Location",
                "/links?applied=disabled&message=%E5%8F%8B%E9%93%BE%E7%94%B3%E8%AF%B7%E5%8A%9F%E8%83%BD%E6%9A%82%E6%9C%AA%E5%BC%80%E6%94%BE");

        verify(rateLimiter, never()).isAllowed(any());
        verify(applicationService, never()).create(any());
    }

    private LinkRouter router() {
        return new LinkRouter(linkFinder, linkPublicQueryService, pluginContext, settingFetcher,
            applicationSettingsFetcher, applicationService, rateLimiter);
    }

    private static LinkApplicationSettings enabledSettings() {
        var settings = new LinkApplicationSettings();
        settings.setEnabled(true);
        return settings.normalized();
    }
}
