package run.halo.links.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
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
import run.halo.links.finders.LinkFinder;
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

    private LinkRouter router() {
        return new LinkRouter(linkFinder, linkPublicQueryService, pluginContext, settingFetcher,
            applicationService);
    }
}
