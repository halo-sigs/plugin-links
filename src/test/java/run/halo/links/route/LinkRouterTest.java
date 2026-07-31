package run.halo.links.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.security.web.server.csrf.CsrfWebFilter;
import org.springframework.security.web.server.csrf.DefaultCsrfToken;
import org.springframework.security.web.server.csrf.ServerCsrfTokenRepository;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.app.plugin.PluginContext;
import run.halo.app.plugin.ReactiveSettingFetcher;
import run.halo.links.dto.LinkApplicationSettings;
import run.halo.links.endpoint.LinkApplicationSettingsFetcher;
import run.halo.links.extension.LinkApplication;
import run.halo.links.finders.LinkFinder;
import run.halo.links.security.LinkApplicationRateLimiter;
import run.halo.links.security.captcha.LinkApplicationCaptchaCookie;
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
        var settings = new LinkBaseSettings();
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
        var settings = new LinkBaseSettings();
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
    void shouldCreateFormOriginApplicationThroughSharedServiceInSecurityOrder() {
        stubEnabledForm();
        when(applicationService.create(any())).thenReturn(created());
        var client = client();

        client.post()
            .uri("/links/apply/submit")
            .accept(MediaType.APPLICATION_JSON)
            .body(BodyInserters.fromFormData("url", " https://example.com ")
                .with("displayName", " Example ")
                .with("captchaCode", "ABCDE"))
            .exchange()
            .expectStatus().is3xxRedirection()
            .expectHeader().valueEquals(HttpHeaders.LOCATION, "/links?applied=success")
            .expectHeader().doesNotExist(HttpHeaders.VARY);

        var submission = ArgumentCaptor.forClass(LinkApplicationService.Submission.class);
        verify(applicationService).create(submission.capture());
        assertThat(submission.getValue().url()).isEqualTo("https://example.com");
        assertThat(submission.getValue().displayName()).isEqualTo("Example");
        assertThat(submission.getValue().origin().getType())
            .isEqualTo(LinkApplication.OriginType.FORM);
        var order = inOrder(captchaService, rateLimiter, applicationService);
        order.verify(captchaService).verifyChallenge(
            nullable(String.class), nullable(String.class));
        order.verify(rateLimiter).admit(any());
        order.verify(applicationService).create(any());
    }

    @ParameterizedTest
    @MethodSource("acceptHeaders")
    void shouldAlwaysUseFormRedirectRegardlessOfAccept(String accept) {
        stubEnabledForm();
        when(applicationService.create(any())).thenReturn(created());

        client().post()
            .uri("/links/apply/submit")
            .header(HttpHeaders.ACCEPT, accept)
            .body(BodyInserters.fromFormData("url", "https://example.com")
                .with("displayName", "Example")
                .with("captchaCode", "ABCDE"))
            .exchange()
            .expectStatus().is3xxRedirection()
            .expectHeader().valueEquals(HttpHeaders.LOCATION, "/links?applied=success")
            .expectHeader().doesNotExist(HttpHeaders.VARY)
            .expectHeader().doesNotExist(HttpHeaders.CACHE_CONTROL);
    }

    @Test
    void shouldRejectUnsupportedMediaTypeWithoutNegotiatingAResponse() {
        client().post()
            .uri("/links/apply/submit")
            .accept(MediaType.APPLICATION_JSON)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{}")
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
            .expectHeader().doesNotExist(HttpHeaders.VARY)
            .expectBody().isEmpty();

        verify(applicationSettingsFetcher, never()).fetch();
        verify(captchaService, never()).verifyChallenge(
            nullable(String.class), nullable(String.class));
        verify(rateLimiter, never()).admit(any());
        verify(applicationService, never()).create(any());
    }

    @Test
    void shouldRejectMissingContentType() {
        client().post()
            .uri("/links/apply/submit")
            .accept(MediaType.TEXT_HTML)
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
            .expectBody().isEmpty();

        verify(applicationSettingsFetcher, never()).fetch();
    }

    @Test
    void shouldGateDisabledFormBeforeCaptchaAndRateLimit() {
        when(applicationSettingsFetcher.fetch())
            .thenReturn(Mono.just(LinkApplicationSettings.defaults()));

        client().post()
            .uri("/links/apply/submit")
            .accept(MediaType.APPLICATION_JSON)
            .body(BodyInserters.fromFormData("url", "not-a-url"))
            .exchange()
            .expectStatus().is3xxRedirection()
            .expectHeader().valueEquals(HttpHeaders.LOCATION,
                "/links?applied=disabled&message="
                    + "%E5%8F%8B%E9%93%BE%E7%94%B3%E8%AF%B7%E5%8A%9F%E8%83%BD%E6%9A%82"
                    + "%E6%9C%AA%E5%BC%80%E6%94%BE")
            .expectHeader().doesNotExist(HttpHeaders.VARY);

        verify(captchaService, never()).verifyChallenge(
            nullable(String.class), nullable(String.class));
        verify(rateLimiter, never()).admit(any());
        verify(applicationService, never()).create(any());
    }

    @Test
    void shouldIssueEnabledCaptchaWithNoStoreHeadersAndSecureCookie() {
        when(applicationSettingsFetcher.fetch()).thenReturn(Mono.just(enabledSettings()));
        when(captchaService.issue(any(), nullable(String.class))).thenReturn(Mono.just(
            new LinkApplicationCaptchaService.IssueResult(
                LinkApplicationCaptchaService.IssueStatus.ISSUED,
                "opaque",
                new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47},
                300,
                0
            )));

        client().get()
            .uri("https://example.test/links/apply/captcha")
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType("image/png")
            .expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL,
                "no-store, no-cache, must-revalidate")
            .expectHeader().value(HttpHeaders.SET_COOKIE, value -> {
                assertThat(value).contains("link_application_captcha=opaque");
                assertThat(value).contains("Path=/links");
                assertThat(value).contains("Max-Age=300");
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

        client().get().uri("/links/apply/captcha").exchange().expectStatus().isNotFound();

        verify(captchaService, never()).issue(any(), nullable(String.class));
    }

    @Test
    void shouldMapCaptchaGenerationLimitsAndFailuresToHttpErrors() {
        when(applicationSettingsFetcher.fetch()).thenReturn(Mono.just(enabledSettings()));
        when(captchaService.issue(any(), nullable(String.class))).thenReturn(
            Mono.just(new LinkApplicationCaptchaService.IssueResult(
                LinkApplicationCaptchaService.IssueStatus.RATE_LIMITED, null, null, 0, 42)),
            Mono.just(new LinkApplicationCaptchaService.IssueResult(
                LinkApplicationCaptchaService.IssueStatus.UNAVAILABLE, null, null, 0, 0)));

        client().get().uri("/links/apply/captcha").exchange()
            .expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
            .expectHeader().valueEquals(HttpHeaders.RETRY_AFTER, "42");
        client().get().uri("/links/apply/captcha").exchange()
            .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void shouldNotRouteLegacyApplicationEndpoints() {
        var client = client();

        client.post()
            .uri("/links/apply")
            .body(BodyInserters.fromFormData("url", "https://example.com"))
            .exchange()
            .expectStatus().isNotFound();
        client.get().uri("/links/captcha").exchange().expectStatus().isNotFound();

        verify(applicationSettingsFetcher, never()).fetch();
    }

    @Test
    void shouldRejectCaptchaAndExpireCookieBeforeConsumingSubmissionAllowance() {
        when(applicationSettingsFetcher.fetch()).thenReturn(Mono.just(enabledSettings()));
        when(captchaService.verifyChallenge(nullable(String.class), nullable(String.class)))
            .thenReturn(false);

        client().post()
            .uri("/links/apply/submit")
            .cookie("link_application_captcha", "opaque")
            .body(BodyInserters.fromFormData("url", "https://secret.example")
                .with("displayName", "Secret")
                .with("captchaCode", "WRONG"))
            .exchange()
            .expectStatus().is3xxRedirection()
            .expectHeader().valueEquals(HttpHeaders.LOCATION,
                "/links?applied=error&field=captchaCode&message="
                    + "%E9%AA%8C%E8%AF%81%E7%A0%81%E9%94%99%E8%AF%AF%E6%88%96%E5%B7%B2%E8%BF%87"
                    + "%E6%9C%9F%EF%BC%8C%E8%AF%B7%E9%87%8D%E6%96%B0%E8%BE%93%E5%85%A5")
            .expectHeader().value(HttpHeaders.SET_COOKIE, value -> {
                assertThat(value).contains("link_application_captcha=");
                assertThat(value).contains("Max-Age=0");
                assertThat(value).doesNotContain("WRONG");
            });

        verify(captchaService).verifyChallenge("opaque", "WRONG");
        verify(rateLimiter, never()).admit(any());
        verify(applicationService, never()).create(any());
    }

    @Test
    void shouldPreserveDuplicateRedirectAndSubmittedValue() {
        stubEnabledForm();
        when(applicationService.create(any())).thenReturn(Mono.just(
            new LinkApplicationService.CreateResult(
                LinkApplicationService.CreateStatus.DUPLICATE,
                null, "url", "https://secret.example", "该链接已提交申请"
            )
        ));

        client().post()
            .uri("/links/apply/submit")
            .accept(MediaType.APPLICATION_JSON)
            .body(BodyInserters.fromFormData("url", "https://secret.example")
                .with("displayName", "Secret")
                .with("captchaCode", "ABCDE"))
            .exchange()
            .expectStatus().is3xxRedirection()
            .expectHeader().valueEquals(HttpHeaders.LOCATION,
                "/links?applied=error&field=url&message="
                    + "%E8%AF%A5%E9%93%BE%E6%8E%A5%E5%B7%B2%E6%8F%90%E4%BA%A4%E7%94%B3%E8%AF%B7"
                    + "&value=https://secret.example")
            .expectHeader().doesNotExist(HttpHeaders.VARY);
    }

    @Test
    void shouldPreserveValidationRedirectAndSubmittedValue() {
        stubEnabledForm();
        when(applicationService.create(any())).thenReturn(Mono.just(
            new LinkApplicationService.CreateResult(
                LinkApplicationService.CreateStatus.INVALID,
                null, "url", "not-a-url", "URL格式错误"
            )
        ));

        client().post()
            .uri("/links/apply/submit")
            .body(BodyInserters.fromFormData("url", "not-a-url")
                .with("displayName", "Example")
                .with("captchaCode", "ABCDE"))
            .exchange()
            .expectStatus().is3xxRedirection()
            .expectHeader().valueEquals(HttpHeaders.LOCATION,
                "/links?applied=error&field=url&message="
                    + "URL%E6%A0%BC%E5%BC%8F%E9%94%99%E8%AF%AF&value=not-a-url");
    }

    @Test
    void shouldRedirectWhenValidatedSubmissionIsRateLimited() {
        when(applicationSettingsFetcher.fetch()).thenReturn(Mono.just(enabledSettings()));
        when(captchaService.verifyChallenge(nullable(String.class), nullable(String.class)))
            .thenReturn(true);
        when(rateLimiter.admit(any()))
            .thenReturn(new LinkApplicationRateLimiter.Admission(false, 42));

        client().post()
            .uri("/links/apply/submit")
            .body(BodyInserters.fromFormData("url", "https://example.com")
                .with("displayName", "Example")
                .with("captchaCode", "ABCDE"))
            .exchange()
            .expectStatus().is3xxRedirection()
            .expectHeader().valueEquals(HttpHeaders.LOCATION,
                "/links?applied=error&message="
                    + "%E6%8F%90%E4%BA%A4%E8%BF%87%E4%BA%8E%E9%A2%91%E7%B9%81%EF%BC%8C%E8%AF%B7"
                    + "%E7%A8%8D%E5%90%8E%E5%86%8D%E8%AF%95")
            .expectHeader().doesNotExist(HttpHeaders.RETRY_AFTER)
            .expectHeader().valueMatches(HttpHeaders.SET_COOKIE, ".*Max-Age=0.*");

        verify(applicationService, never()).create(any());
    }

    @Test
    void shouldRedirectWhenPendingCapacityIsFull() {
        stubEnabledForm();
        when(applicationService.create(any())).thenReturn(Mono.just(
            new LinkApplicationService.CreateResult(
                LinkApplicationService.CreateStatus.CAPACITY_REACHED,
                null, null, null, null
            )
        ));

        client().post()
            .uri("/links/apply/submit")
            .body(BodyInserters.fromFormData("url", "https://example.com")
                .with("displayName", "Example")
                .with("captchaCode", "ABCDE"))
            .exchange()
            .expectStatus().is3xxRedirection()
            .expectHeader().valueEquals(HttpHeaders.LOCATION,
                "/links?applied=error&message="
                    + "%E5%BE%85%E5%AE%A1%E6%A0%B8%E7%94%B3%E8%AF%B7%E6%95%B0%E9%87%8F%E5%B7%B2"
                    + "%E8%BE%BE%E4%B8%8A%E9%99%90%EF%BC%8C%E8%AF%B7%E7%A8%8D%E5%90%8E%E5%86%8D"
                    + "%E8%AF%95");
    }

    @Test
    void shouldRedirectWhenApplicationCreationIsUnavailable() {
        stubEnabledForm();
        when(applicationService.create(any()))
            .thenReturn(Mono.error(new IllegalStateException("create failed")));

        client().post()
            .uri("/links/apply/submit")
            .body(BodyInserters.fromFormData("url", "https://example.com")
                .with("displayName", "Example")
                .with("captchaCode", "ABCDE"))
            .exchange()
            .expectStatus().is3xxRedirection()
            .expectHeader().valueEquals(HttpHeaders.LOCATION,
                "/links?applied=error&message="
                    + "%E6%9A%82%E6%97%B6%E6%97%A0%E6%B3%95%E6%8F%90%E4%BA%A4%EF%BC%8C%E8%AF%B7"
                    + "%E7%A8%8D%E5%90%8E%E5%86%8D%E8%AF%95");
    }

    @Test
    void shouldKeepRealCsrfFilterAheadOfFormHandling() {
        when(applicationSettingsFetcher.fetch()).thenReturn(Mono.just(enabledSettings()));
        when(captchaService.issue(any(), nullable(String.class))).thenReturn(Mono.just(
            new LinkApplicationCaptchaService.IssueResult(
                LinkApplicationCaptchaService.IssueStatus.ISSUED,
                "opaque",
                new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47},
                300,
                0
            )));
        when(captchaService.verifyChallenge(nullable(String.class), nullable(String.class)))
            .thenReturn(true);
        when(rateLimiter.admit(any())).thenReturn(allowedAdmission());
        when(applicationService.create(any())).thenReturn(created());
        var csrfFilter = new CsrfWebFilter();
        csrfFilter.setCsrfTokenRepository(new FixedCsrfTokenRepository());
        var client = WebTestClient.bindToRouterFunction(router().linkTemplateRoute())
            .webFilter(csrfFilter)
            .build();

        client.get().uri("/links/apply/captcha").exchange().expectStatus().isOk();

        client.post()
            .uri("/links/apply/submit")
            .body(BodyInserters.fromFormData("url", "https://example.com")
                .with("displayName", "Example")
                .with("captchaCode", "ABCDE")
                .with("_csrf", "invalid"))
            .exchange()
            .expectStatus().isForbidden();
        verify(captchaService, never()).verifyChallenge(
            nullable(String.class), nullable(String.class));

        client.post()
            .uri("/links/apply/submit")
            .body(BodyInserters.fromFormData("url", "https://example.com")
                .with("displayName", "Example")
                .with("captchaCode", "ABCDE")
                .with("_csrf", maskedCsrfToken()))
            .exchange()
            .expectStatus().is3xxRedirection()
            .expectHeader().valueEquals(HttpHeaders.LOCATION, "/links?applied=success");
        verify(applicationService).create(any());
    }

    private WebTestClient client() {
        return WebTestClient.bindToRouterFunction(router().linkTemplateRoute()).build();
    }

    private LinkRouter router() {
        return new LinkRouter(linkFinder, linkPublicQueryService, pluginContext, settingFetcher,
            applicationSettingsFetcher, applicationService, rateLimiter, captchaService,
            new LinkApplicationCaptchaCookie());
    }

    private void stubEnabledForm() {
        when(applicationSettingsFetcher.fetch()).thenReturn(Mono.just(enabledSettings()));
        when(captchaService.verifyChallenge(nullable(String.class), nullable(String.class)))
            .thenReturn(true);
        when(rateLimiter.admit(any())).thenReturn(allowedAdmission());
    }

    private static LinkApplicationSettings enabledSettings() {
        var settings = new LinkApplicationSettings();
        settings.setEnabled(true);
        return settings.normalized();
    }

    private static LinkApplicationRateLimiter.Admission allowedAdmission() {
        return new LinkApplicationRateLimiter.Admission(true, 0);
    }

    private static Mono<LinkApplicationService.CreateResult> created() {
        return Mono.just(new LinkApplicationService.CreateResult(
            LinkApplicationService.CreateStatus.CREATED,
            null, null, null, null
        ));
    }

    private static Stream<String> acceptHeaders() {
        return Stream.of(
            MediaType.APPLICATION_JSON_VALUE,
            MediaType.TEXT_HTML_VALUE,
            MediaType.TEXT_PLAIN_VALUE,
            "application/json;q=0.9, text/html;q=0.8",
            "*/*"
        );
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
        public Mono<Void> saveToken(
            org.springframework.web.server.ServerWebExchange exchange,
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
