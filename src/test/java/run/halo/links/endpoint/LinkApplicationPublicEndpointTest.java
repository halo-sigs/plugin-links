package run.halo.links.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.codec.DecodingException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.reactive.function.server.MockServerRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.app.extension.Metadata;
import run.halo.links.dto.LinkApplicationRestRequest;
import run.halo.links.dto.LinkApplicationSettings;
import run.halo.links.extension.LinkApplication;
import run.halo.links.security.LinkApplicationRateLimiter;
import run.halo.links.security.captcha.LinkApplicationCaptchaService;
import run.halo.links.service.LinkApplicationService;

@ExtendWith(MockitoExtension.class)
class LinkApplicationPublicEndpointTest {

    @Mock
    LinkApplicationSettingsFetcher settingsFetcher;

    @Mock
    LinkApplicationCaptchaService captchaService;

    @Mock
    LinkApplicationRateLimiter rateLimiter;

    @Mock
    LinkApplicationService applicationService;

    @Test
    void shouldPublishTheAgreedPublicApiGroupAndExactRelativePaths() {
        var endpoint = endpoint();
        assertThat(endpoint.groupVersion().toString())
            .isEqualTo("api.link.halo.run/v1alpha1");
        var client = client(endpoint);

        client.get().uri("/link-applications/captcha").exchange()
            .expectStatus().isNotFound();
        client.get().uri("/link-applications").exchange()
            .expectStatus().isNotFound();
    }

    @Test
    void shouldIssueCookielessCaptchaAsNoStoreDataUrl() {
        when(settingsFetcher.fetchStrict()).thenReturn(Mono.just(enabledSettings()));
        when(captchaService.issue(any(), any())).thenReturn(Mono.just(
            new LinkApplicationCaptchaService.IssueResult(
                LinkApplicationCaptchaService.IssueStatus.ISSUED,
                "challenge-1", new byte[] {1, 2, 3}, 300, 0)));

        client(endpoint()).post()
            .uri("/link-applications/captcha")
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
            .expectHeader().doesNotExist(HttpHeaders.SET_COOKIE)
            .expectBody()
            .jsonPath("$.challengeId").isEqualTo("challenge-1")
            .jsonPath("$.image").isEqualTo("data:image/png;base64,AQID")
            .jsonPath("$.expiresInSeconds").isEqualTo(300);

        verify(captchaService).issue(any(), org.mockito.ArgumentMatchers.isNull());
    }

    @Test
    void shouldNotIssueCaptchaWhenVisitorSubmissionIsDisabled() {
        when(settingsFetcher.fetchStrict())
            .thenReturn(Mono.just(LinkApplicationSettings.defaults()));

        client(endpoint()).post()
            .uri("/link-applications/captcha")
            .exchange()
            .expectStatus().isNotFound();

        verify(captchaService, never()).issue(any(), any());
    }

    @Test
    void shouldCreatePendingFormOriginApplicationWithNormalizedKnownFields() {
        when(settingsFetcher.fetchStrict()).thenReturn(Mono.just(enabledSettings()));
        when(captchaService.verifyChallenge("challenge-1", "ABCDE")).thenReturn(true);
        when(rateLimiter.admit(any())).thenReturn(allowedAdmission());
        when(applicationService.create(any())).thenReturn(Mono.just(created("link-app-1")));

        client(endpoint()).post()
            .uri("/link-applications")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {
                  "url": " https://example.com ",
                  "displayName": " Example ",
                  "logo": " ",
                  "description": " Description ",
                  "email": null,
                  "backlink": " https://example.com/links ",
                  "feedUrls": [" https://example.com/feed.xml ", " ", null],
                  "challengeId": " challenge-1 ",
                  "captchaCode": " ABCDE ",
                  "futureProperty": true
                }
                """)
            .exchange()
            .expectStatus().isCreated()
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectHeader().doesNotExist(HttpHeaders.LOCATION)
            .expectHeader().doesNotExist(HttpHeaders.SET_COOKIE)
            .expectBody()
            .jsonPath("$.id").isEqualTo("link-app-1")
            .jsonPath("$.status").isEqualTo("PENDING")
            .jsonPath("$.url").doesNotExist()
            .jsonPath("$.displayName").doesNotExist();

        var submission = ArgumentCaptor.forClass(LinkApplicationService.Submission.class);
        verify(applicationService).create(submission.capture());
        assertThat(submission.getValue().url()).isEqualTo("https://example.com");
        assertThat(submission.getValue().displayName()).isEqualTo("Example");
        assertThat(submission.getValue().logo()).isNull();
        assertThat(submission.getValue().description()).isEqualTo("Description");
        assertThat(submission.getValue().email()).isNull();
        assertThat(submission.getValue().backlink()).isEqualTo("https://example.com/links");
        assertThat(submission.getValue().feedUrls())
            .containsExactly("https://example.com/feed.xml");
        assertThat(submission.getValue().origin().getType())
            .isEqualTo(LinkApplication.OriginType.FORM);

        var order = inOrder(settingsFetcher, captchaService, rateLimiter, applicationService);
        order.verify(settingsFetcher).fetchStrict();
        order.verify(captchaService).verifyChallenge("challenge-1", "ABCDE");
        order.verify(rateLimiter).admit(any());
        order.verify(applicationService).create(any());
    }

    @Test
    void shouldRejectNonJsonBeforeSecurityOrCreation() {
        var request = request("/link-applications", MediaType.APPLICATION_FORM_URLENCODED,
            Mono.just(new LinkApplicationRestRequest()));

        expectProblem(invoke(endpoint(), request), HttpStatus.UNSUPPORTED_MEDIA_TYPE, null,
            "仅支持 application/json 请求");

        verify(settingsFetcher, never()).fetchStrict();
        verify(captchaService, never()).verifyChallenge(any(), any());
        verify(applicationService, never()).create(any());
    }

    @Test
    void shouldMapMalformedJsonToNativeBadRequestBeforeCaptcha() {
        when(settingsFetcher.fetchStrict()).thenReturn(Mono.just(enabledSettings()));
        var request = request("/link-applications", MediaType.APPLICATION_JSON,
            Mono.error(new DecodingException("secret parser detail")));

        expectProblem(invoke(endpoint(), request), HttpStatus.BAD_REQUEST, null,
            "请求 JSON 格式错误");

        verify(captchaService, never()).verifyChallenge(any(), any());
        verify(applicationService, never()).create(any());
    }

    @Test
    void shouldRejectMissingChallengeAsInvalidCaptchaWithoutRateAdmission() {
        when(settingsFetcher.fetchStrict()).thenReturn(Mono.just(enabledSettings()));
        var body = validRequest();
        body.setChallengeId(" ");
        when(captchaService.verifyChallenge(null, "ABCDE")).thenReturn(false);

        expectProblem(invoke(endpoint(), jsonRequest(body)), HttpStatus.BAD_REQUEST,
            LinkApplicationProblemException.INVALID_CAPTCHA,
            "验证码错误或已过期，请重新获取");

        verify(rateLimiter, never()).admit(any());
        verify(applicationService, never()).create(any());
    }

    @Test
    void shouldExposeHaloStyleErrorsForSharedFieldValidation() {
        stubValidatedRequest();
        when(applicationService.create(any())).thenReturn(Mono.just(
            new LinkApplicationService.CreateResult(
                LinkApplicationService.CreateStatus.INVALID,
                null, "url", null, "URL不能为空")));
        var body = validRequest();
        body.setUrl(" ");

        expectProblem(invoke(endpoint(), jsonRequest(body)), HttpStatus.BAD_REQUEST,
            LinkApplicationProblemException.INVALID_APPLICATION, "提交内容不符合要求",
            "errors", List.of("URL不能为空"));
    }

    @Test
    void shouldMapDuplicateAndCapacityToDistinctConflictTypes() {
        stubValidatedRequest();
        when(applicationService.create(any())).thenReturn(
            Mono.just(new LinkApplicationService.CreateResult(
                LinkApplicationService.CreateStatus.DUPLICATE,
                null, "url", "https://secret.example", "该链接已提交申请")),
            Mono.just(new LinkApplicationService.CreateResult(
                LinkApplicationService.CreateStatus.CAPACITY_REACHED,
                null, null, null, null)));

        expectProblem(invoke(endpoint(), jsonRequest(validRequest())), HttpStatus.CONFLICT,
            LinkApplicationProblemException.DUPLICATE_APPLICATION, "该链接已提交申请");
        expectProblem(invoke(endpoint(), jsonRequest(validRequest())), HttpStatus.CONFLICT,
            LinkApplicationProblemException.CAPACITY_REACHED,
            "待审核申请数量已达上限，请稍后再试");
    }

    @Test
    void shouldReturnBodyOnlyPositiveRetryTimingAfterCaptchaConsumption() {
        when(settingsFetcher.fetchStrict()).thenReturn(Mono.just(enabledSettings()));
        when(captchaService.verifyChallenge("challenge-1", "ABCDE")).thenReturn(true);
        when(rateLimiter.admit(any()))
            .thenReturn(new LinkApplicationRateLimiter.Admission(false, 0));

        expectProblem(invoke(endpoint(), jsonRequest(validRequest())),
            HttpStatus.TOO_MANY_REQUESTS,
            LinkApplicationProblemException.REQUEST_NOT_PERMITTED,
            "请求过于频繁，请稍后再试", "retryAfterSeconds", 1L);

        verify(applicationService, never()).create(any());
    }

    @Test
    void shouldMapDisabledAndOperationalFailuresWithoutSensitiveDetail() {
        when(settingsFetcher.fetchStrict()).thenReturn(
            Mono.just(LinkApplicationSettings.defaults()),
            Mono.error(new IllegalStateException("database password secret")));

        expectProblem(invoke(endpoint(), jsonRequest(validRequest())), HttpStatus.FORBIDDEN,
            LinkApplicationProblemException.APPLICATION_DISABLED, "友链申请功能暂未开放");
        expectProblem(invoke(endpoint(), jsonRequest(validRequest())),
            HttpStatus.SERVICE_UNAVAILABLE,
            LinkApplicationProblemException.APPLICATION_UNAVAILABLE,
            "服务暂时不可用，请稍后再试");
    }

    @Test
    void shouldMapUnexpectedCreationFailureToUnavailable() {
        stubValidatedRequest();
        when(applicationService.create(any()))
            .thenReturn(Mono.error(new IllegalStateException("submitted secret")));

        expectProblem(invoke(endpoint(), jsonRequest(validRequest())),
            HttpStatus.SERVICE_UNAVAILABLE,
            LinkApplicationProblemException.APPLICATION_UNAVAILABLE,
            "服务暂时不可用，请稍后再试");
    }

    @Test
    void shouldMapCaptchaRateAndAvailabilityFailuresToProblems() {
        when(settingsFetcher.fetchStrict()).thenReturn(Mono.just(enabledSettings()));
        when(captchaService.issue(any(), any())).thenReturn(
            Mono.just(new LinkApplicationCaptchaService.IssueResult(
                LinkApplicationCaptchaService.IssueStatus.RATE_LIMITED,
                null, null, 0, 12)),
            Mono.just(new LinkApplicationCaptchaService.IssueResult(
                LinkApplicationCaptchaService.IssueStatus.UNAVAILABLE,
                null, null, 0, 0)));
        var endpoint = endpoint();

        expectProblem(invoke(endpoint, request("/link-applications/captcha", null, Mono.empty())),
            HttpStatus.TOO_MANY_REQUESTS,
            LinkApplicationProblemException.REQUEST_NOT_PERMITTED,
            "请求过于频繁，请稍后再试", "retryAfterSeconds", 12L);
        expectProblem(invoke(endpoint, request("/link-applications/captcha", null, Mono.empty())),
            HttpStatus.SERVICE_UNAVAILABLE,
            LinkApplicationProblemException.APPLICATION_UNAVAILABLE,
            "服务暂时不可用，请稍后再试");
    }

    private void stubValidatedRequest() {
        when(settingsFetcher.fetchStrict()).thenReturn(Mono.just(enabledSettings()));
        when(captchaService.verifyChallenge("challenge-1", "ABCDE")).thenReturn(true);
        when(rateLimiter.admit(any())).thenReturn(allowedAdmission());
    }

    private LinkApplicationPublicEndpoint endpoint() {
        return new LinkApplicationPublicEndpoint(settingsFetcher, captchaService, rateLimiter,
            applicationService);
    }

    private static WebTestClient client(LinkApplicationPublicEndpoint endpoint) {
        return WebTestClient.bindToRouterFunction(endpoint.endpoint()).build();
    }

    private static Mono<ServerResponse> invoke(LinkApplicationPublicEndpoint endpoint,
        ServerRequest request) {
        return endpoint.endpoint().route(request)
            .switchIfEmpty(Mono.error(new AssertionError("No matching route")))
            .flatMap(handler -> handler.handle(request));
    }

    private static MockServerRequest jsonRequest(LinkApplicationRestRequest body) {
        return request("/link-applications", MediaType.APPLICATION_JSON, Mono.just(body));
    }

    private static MockServerRequest request(String path, MediaType contentType, Object body) {
        var httpRequest = MockServerHttpRequest.method(HttpMethod.POST, path);
        var requestBuilder = MockServerRequest.builder()
            .method(HttpMethod.POST)
            .uri(URI.create(path));
        if (contentType != null) {
            httpRequest.header(HttpHeaders.CONTENT_TYPE, contentType.toString());
            requestBuilder.header(HttpHeaders.CONTENT_TYPE, contentType.toString());
        }
        return requestBuilder
            .exchange(MockServerWebExchange.from(httpRequest.build()))
            .body(body);
    }

    private static LinkApplicationRestRequest validRequest() {
        var body = new LinkApplicationRestRequest();
        body.setUrl("https://example.com");
        body.setDisplayName("Example");
        body.setChallengeId("challenge-1");
        body.setCaptchaCode("ABCDE");
        return body;
    }

    private static LinkApplicationSettings enabledSettings() {
        var settings = new LinkApplicationSettings();
        settings.setEnabled(true);
        return settings.normalized();
    }

    private static LinkApplicationRateLimiter.Admission allowedAdmission() {
        return new LinkApplicationRateLimiter.Admission(true, 0);
    }

    private static LinkApplicationService.CreateResult created(String name) {
        var application = new LinkApplication();
        var metadata = new Metadata();
        metadata.setName(name);
        application.setMetadata(metadata);
        return new LinkApplicationService.CreateResult(
            LinkApplicationService.CreateStatus.CREATED,
            application, null, null, null);
    }

    private static void expectProblem(Mono<ServerResponse> response, HttpStatus status,
        String type, String detail) {
        StepVerifier.create(response)
            .expectErrorSatisfies(error -> assertProblem(error, status, type, detail))
            .verify();
    }

    private static void expectProblem(Mono<ServerResponse> response, HttpStatus status,
        String type, String detail, String property, Object value) {
        StepVerifier.create(response)
            .expectErrorSatisfies(error -> {
                var problem = assertProblem(error, status, type, detail);
                assertThat(problem.getBody().getProperties())
                    .containsEntry(property, value);
            })
            .verify();
    }

    private static ResponseStatusException assertProblem(Throwable error, HttpStatus status,
        String type, String detail) {
        assertThat(error).isInstanceOf(ResponseStatusException.class);
        var problem = (ResponseStatusException) error;
        assertThat(problem.getStatusCode()).isEqualTo(status);
        assertThat(problem.getBody().getType())
            .isEqualTo(type == null ? null : URI.create(type));
        assertThat(problem.getBody().getDetail()).isEqualTo(detail);
        if (problem.getBody().getProperties() != null) {
            assertThat(problem.getBody().getProperties())
                .doesNotContainKeys("status", "code", "field");
        }
        return problem;
    }
}
