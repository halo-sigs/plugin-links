package run.halo.links.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.security.web.server.csrf.CsrfWebFilter;
import org.springframework.security.web.server.csrf.DefaultCsrfToken;
import org.springframework.security.web.server.csrf.ServerCsrfTokenRepository;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.links.extension.LinkApplication;
import run.halo.app.plugin.PluginContext;
import run.halo.app.plugin.ReactiveSettingFetcher;
import run.halo.links.dto.LinkApplicationSettings;
import run.halo.links.endpoint.LinkApplicationSettingsFetcher;
import run.halo.links.finders.LinkFinder;
import run.halo.links.security.LinkApplicationRateLimiter;
import run.halo.links.security.captcha.LinkApplicationCaptchaService;
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

    @Mock
    LinkApplicationCaptchaService captchaService;

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
        when(captchaService.verify(any(), any())).thenReturn(validCaptcha());
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
        var order = inOrder(captchaService, rateLimiter, applicationService);
        order.verify(captchaService).verify(any(), any());
        order.verify(rateLimiter).isAllowed(any());
        order.verify(applicationService).create(any());
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
        verify(captchaService, never()).verify(any(), any());
        verify(applicationService, never()).create(any());
    }

    @Test
    void shouldIssueEnabledCaptchaWithNoStoreHeadersAndSecureCookie() {
        when(applicationSettingsFetcher.fetch()).thenReturn(Mono.just(enabledSettings()));
        when(captchaService.issue(any())).thenReturn(Mono.just(
            new LinkApplicationCaptchaService.IssueResult(
                LinkApplicationCaptchaService.IssueStatus.ISSUED,
                new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47},
                ResponseCookie.from("link_application_captcha", "opaque")
                    .path("/links")
                    .maxAge(Duration.ofMinutes(5))
                    .httpOnly(true)
                    .sameSite("Lax")
                    .secure(true)
                    .build(),
                0
            )));
        var client = WebTestClient.bindToRouterFunction(router().linkTemplateRoute()).build();

        client.get()
            .uri("https://example.test/links/captcha")
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType("image/png")
            .expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL,
                "no-store, no-cache, must-revalidate")
            .expectHeader().value(HttpHeaders.SET_COOKIE, value -> {
                assertThat(value).contains("link_application_captcha=opaque");
                assertThat(value).contains("Path=/links");
                assertThat(value).contains("HttpOnly");
                assertThat(value).contains("Secure");
                assertThat(value).contains("SameSite=Lax");
            })
            .expectBody(byte[].class)
            .isEqualTo(new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47});
    }

    @Test
    void shouldReturnNotFoundWithoutIssuingCaptchaWhenDisabled() {
        when(applicationSettingsFetcher.fetch())
            .thenReturn(Mono.just(LinkApplicationSettings.defaults()));
        var client = WebTestClient.bindToRouterFunction(router().linkTemplateRoute()).build();

        client.get().uri("/links/captcha").exchange().expectStatus().isNotFound();

        verify(captchaService, never()).issue(any());
    }

    @Test
    void shouldMapCaptchaGenerationLimitsAndFailuresToHttpErrors() {
        when(applicationSettingsFetcher.fetch()).thenReturn(Mono.just(enabledSettings()));
        when(captchaService.issue(any())).thenReturn(
            Mono.just(new LinkApplicationCaptchaService.IssueResult(
                LinkApplicationCaptchaService.IssueStatus.RATE_LIMITED, null, null, 42)),
            Mono.just(new LinkApplicationCaptchaService.IssueResult(
                LinkApplicationCaptchaService.IssueStatus.UNAVAILABLE, null, null, 0)));
        var client = WebTestClient.bindToRouterFunction(router().linkTemplateRoute()).build();

        client.get().uri("/links/captcha").exchange()
            .expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
            .expectHeader().valueEquals(HttpHeaders.RETRY_AFTER, "42");
        client.get().uri("/links/captcha").exchange()
            .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void shouldRejectCaptchaWithoutReflectingFieldsOrConsumingSubmissionAllowance() {
        when(applicationSettingsFetcher.fetch()).thenReturn(Mono.just(enabledSettings()));
        when(captchaService.verify(any(), any())).thenReturn(new
            LinkApplicationCaptchaService.VerificationResult(false, expiredCaptchaCookie()));
        var client = WebTestClient.bindToRouterFunction(router().linkTemplateRoute()).build();

        client.post()
            .uri("/links/apply")
            .body(BodyInserters.fromFormData("url", "https://secret.example")
                .with("displayName", "Secret")
                .with("captchaCode", "WRONG"))
            .exchange()
            .expectStatus().is3xxRedirection()
            .expectHeader().valueEquals("Location",
                "/links?applied=error&field=captchaCode&message=%E9%AA%8C%E8%AF%81%E7%A0%81%E9%94%99%E8%AF%AF%E6%88%96%E5%B7%B2%E8%BF%87%E6%9C%9F%EF%BC%8C%E8%AF%B7%E9%87%8D%E6%96%B0%E8%BE%93%E5%85%A5")
            .expectHeader().value(HttpHeaders.SET_COOKIE, value -> {
                assertThat(value).contains("link_application_captcha=");
                assertThat(value).contains("Max-Age=0");
                assertThat(value).doesNotContain("WRONG");
            });

        verify(rateLimiter, never()).isAllowed(any());
        verify(applicationService, never()).create(any());
    }

    @Test
    void shouldExpireCaptchaCookieWhenValidatedSubmissionIsRateLimited() {
        when(applicationSettingsFetcher.fetch()).thenReturn(Mono.just(enabledSettings()));
        when(captchaService.verify(any(), any())).thenReturn(validCaptcha());
        when(rateLimiter.isAllowed(any())).thenReturn(false);
        var client = WebTestClient.bindToRouterFunction(router().linkTemplateRoute()).build();

        client.post()
            .uri("/links/apply")
            .body(BodyInserters.fromFormData("url", "https://example.com")
                .with("displayName", "Example")
                .with("captchaCode", "ABCDE"))
            .exchange()
            .expectStatus().is3xxRedirection()
            .expectHeader().valueEquals("Location",
                "/links?applied=error&message=%E6%8F%90%E4%BA%A4%E8%BF%87%E4%BA%8E%E9%A2%91%E7%B9%81%EF%BC%8C%E8%AF%B7%E7%A8%8D%E5%90%8E%E5%86%8D%E8%AF%95")
            .expectHeader().valueMatches(HttpHeaders.SET_COOKIE, ".*Max-Age=0.*");

        verify(applicationService, never()).create(any());
    }

    @Test
    void shouldExpireCaptchaCookieWhenApplicationCreationFails() {
        when(applicationSettingsFetcher.fetch()).thenReturn(Mono.just(enabledSettings()));
        when(captchaService.verify(any(), any())).thenReturn(validCaptcha());
        when(rateLimiter.isAllowed(any())).thenReturn(true);
        when(applicationService.create(any()))
            .thenReturn(Mono.error(new IllegalStateException("create failed")));
        var client = WebTestClient.bindToRouterFunction(router().linkTemplateRoute()).build();

        client.post()
            .uri("/links/apply")
            .body(BodyInserters.fromFormData("url", "https://example.com")
                .with("displayName", "Example")
                .with("captchaCode", "ABCDE"))
            .exchange()
            .expectStatus().is5xxServerError()
            .expectHeader().valueMatches(HttpHeaders.SET_COOKIE, ".*Max-Age=0.*");
    }

    @Test
    void shouldKeepRealCsrfFilterAheadOfAnonymousCaptchaAndApplicationHandling() {
        when(applicationSettingsFetcher.fetch()).thenReturn(Mono.just(enabledSettings()));
        when(captchaService.issue(any())).thenReturn(Mono.just(
            new LinkApplicationCaptchaService.IssueResult(
                LinkApplicationCaptchaService.IssueStatus.ISSUED,
                new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47},
                ResponseCookie.from("link_application_captcha", "opaque").build(),
                0
            )));
        when(captchaService.verify(any(), any())).thenReturn(validCaptcha());
        when(rateLimiter.isAllowed(any())).thenReturn(true);
        when(applicationService.create(any())).thenReturn(Mono.just(
            new LinkApplicationService.CreateResult(
                LinkApplicationService.CreateStatus.CREATED,
                null, null, null, null
            )
        ));
        var csrfFilter = new CsrfWebFilter();
        csrfFilter.setCsrfTokenRepository(new FixedCsrfTokenRepository());
        var client = WebTestClient.bindToRouterFunction(router().linkTemplateRoute())
            .webFilter(csrfFilter)
            .build();

        client.get().uri("/links/captcha").exchange().expectStatus().isOk();

        client.post()
            .uri("/links/apply")
            .body(BodyInserters.fromFormData("url", "https://example.com")
                .with("displayName", "Example")
                .with("captchaCode", "ABCDE")
                .with("_csrf", "invalid"))
            .exchange()
            .expectStatus().isForbidden();
        verify(captchaService, never()).verify(any(), any());

        client.post()
            .uri("/links/apply")
            .body(BodyInserters.fromFormData("url", "https://example.com")
                .with("displayName", "Example")
                .with("captchaCode", "ABCDE")
                .with("_csrf", maskedCsrfToken()))
            .exchange()
            .expectStatus().is3xxRedirection()
            .expectHeader().valueEquals("Location", "/links?applied=success");
        verify(applicationService).create(any());
    }

    private LinkRouter router() {
        return new LinkRouter(linkFinder, linkPublicQueryService, pluginContext, settingFetcher,
            applicationSettingsFetcher, applicationService, rateLimiter, captchaService);
    }

    private static LinkApplicationSettings enabledSettings() {
        var settings = new LinkApplicationSettings();
        settings.setEnabled(true);
        return settings.normalized();
    }

    private static LinkApplicationCaptchaService.VerificationResult validCaptcha() {
        return new LinkApplicationCaptchaService.VerificationResult(true, expiredCaptchaCookie());
    }

    private static ResponseCookie expiredCaptchaCookie() {
        return ResponseCookie.from("link_application_captcha", "")
            .path("/links")
            .maxAge(Duration.ZERO)
            .httpOnly(true)
            .sameSite("Lax")
            .build();
    }

    private static String maskedCsrfToken() {
        byte[] token = FixedCsrfTokenRepository.TOKEN_VALUE.getBytes(StandardCharsets.UTF_8);
        byte[] masked = new byte[token.length * 2];
        System.arraycopy(token, 0, masked, token.length, token.length);
        return Base64.getUrlEncoder().encodeToString(masked);
    }

    private static final class FixedCsrfTokenRepository implements ServerCsrfTokenRepository {

        private static final String TOKEN_VALUE = "known-csrf-token";
        private static final CsrfToken TOKEN =
            new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", TOKEN_VALUE);

        @Override
        public Mono<CsrfToken> generateToken(
            org.springframework.web.server.ServerWebExchange exchange) {
            return Mono.just(TOKEN);
        }

        @Override
        public Mono<Void> saveToken(org.springframework.web.server.ServerWebExchange exchange,
            CsrfToken token) {
            return Mono.empty();
        }

        @Override
        public Mono<CsrfToken> loadToken(
            org.springframework.web.server.ServerWebExchange exchange) {
            return Mono.just(TOKEN);
        }
    }
}
