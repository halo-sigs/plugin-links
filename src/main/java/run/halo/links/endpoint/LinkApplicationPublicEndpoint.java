package run.halo.links.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.content.Builder.contentBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;
import static org.springdoc.core.fn.builders.schema.Builder.schemaBuilder;
import static org.springdoc.webflux.core.fn.SpringdocRouteBuilder.route;

import java.util.Base64;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.codec.DecodingException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;
import run.halo.links.dto.LinkApplicationCaptchaResponse;
import run.halo.links.dto.LinkApplicationCreatedResponse;
import run.halo.links.dto.LinkApplicationProblemResponse;
import run.halo.links.dto.LinkApplicationRestRequest;
import run.halo.links.security.LinkApplicationRateLimiter;
import run.halo.links.security.captcha.LinkApplicationCaptchaService;
import run.halo.links.service.LinkApplicationService;

@Component
@RequiredArgsConstructor
@Slf4j
public class LinkApplicationPublicEndpoint implements CustomEndpoint {

    private static final String CAPTCHA_PATH = "link-applications/captcha";
    private static final String APPLICATION_PATH = "link-applications";

    private final LinkApplicationSettingsFetcher settingsFetcher;
    private final LinkApplicationCaptchaService captchaService;
    private final LinkApplicationRateLimiter rateLimiter;
    private final LinkApplicationService applicationService;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        final var tag = "api.link.halo.run/v1alpha1/LinkApplication";
        return route()
            .POST(CAPTCHA_PATH, this::issueCaptcha,
                builder -> builder.operationId("createLinkApplicationCaptcha")
                    .description("Create a cookie-free CAPTCHA challenge for a link application.")
                    .tag(tag)
                    .response(jsonResponse("200", LinkApplicationCaptchaResponse.class))
                    .response(problemResponse("429"))
                    .response(problemResponse("503")))
            .POST(APPLICATION_PATH, this::createApplication,
                builder -> builder.operationId("createLinkApplication")
                    .description("Create a visitor link application with an explicit CAPTCHA.")
                    .tag(tag)
                    .requestBody(requestBodyBuilder()
                        .required(true)
                        .content(contentBuilder()
                            .mediaType(MediaType.APPLICATION_JSON_VALUE)
                            .schema(schemaBuilder()
                                .implementation(LinkApplicationRestRequest.class))))
                    .response(jsonResponse("201", LinkApplicationCreatedResponse.class))
                    .response(problemResponse("400"))
                    .response(problemResponse("403"))
                    .response(problemResponse("409"))
                    .response(problemResponse("415"))
                    .response(problemResponse("429"))
                    .response(problemResponse("503")))
            .build();
    }

    private Mono<ServerResponse> issueCaptcha(ServerRequest request) {
        return settingsFetcher.fetchStrict()
            .onErrorMap(this::unavailable)
            .flatMap(settings -> {
                if (!settings.selfSubmissionEnabled()) {
                    return ServerResponse.notFound().build();
                }
                return captchaService.issue(request, null)
                    .flatMap(result -> switch (result.status()) {
                        case ISSUED -> ServerResponse.ok()
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(HttpHeaders.CACHE_CONTROL, "no-store")
                            .bodyValue(new LinkApplicationCaptchaResponse(
                                result.identifier(),
                                "data:image/png;base64,"
                                    + Base64.getEncoder().encodeToString(result.png()),
                                result.expiresInSeconds()
                            ));
                        case RATE_LIMITED ->
                            Mono.error(LinkApplicationProblemException.rateLimited(
                                result.retryAfterSeconds()));
                        case UNAVAILABLE ->
                            Mono.error(LinkApplicationProblemException.unavailable());
                    });
            });
    }

    private Mono<ServerResponse> createApplication(ServerRequest request) {
        if (!isJson(request)) {
            return Mono.error(new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "仅支持 application/json 请求"));
        }
        return settingsFetcher.fetchStrict()
            .onErrorMap(this::unavailable)
            .flatMap(settings -> {
                if (!settings.selfSubmissionEnabled()) {
                    return Mono.error(LinkApplicationProblemException.disabled());
                }
                return decode(request)
                    .flatMap(body -> verifyAndCreate(request, body));
            });
    }

    private Mono<ServerResponse> verifyAndCreate(ServerRequest request,
        LinkApplicationRestRequest body) {
        boolean captchaValid = captchaService.verifyChallenge(
            body.normalizedChallengeId(), body.normalizedCaptchaCode());
        if (!captchaValid) {
            return Mono.error(LinkApplicationProblemException.invalidCaptcha());
        }
        var admission = rateLimiter.admit(request);
        if (!admission.allowed()) {
            return Mono.error(LinkApplicationProblemException.rateLimited(
                admission.retryAfterSeconds()));
        }
        return applicationService.create(body.toSubmission())
            .flatMap(result -> switch (result.status()) {
                case CREATED -> ServerResponse.status(HttpStatus.CREATED)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new LinkApplicationCreatedResponse(
                        result.application().getMetadata().getName(), "PENDING"));
                case INVALID -> Mono.error(
                    LinkApplicationProblemException.invalidApplication(result.message()));
                case DUPLICATE -> Mono.error(LinkApplicationProblemException.duplicate());
                case CAPACITY_REACHED ->
                    Mono.error(LinkApplicationProblemException.capacityReached());
            })
            .onErrorMap(this::unavailable);
    }

    private Mono<LinkApplicationRestRequest> decode(ServerRequest request) {
        return request.bodyToMono(LinkApplicationRestRequest.class)
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "请求体不能为空")))
            .onErrorMap(error -> error instanceof ServerWebInputException
                    || error instanceof DecodingException,
                error -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "请求 JSON 格式错误"));
    }

    private Throwable unavailable(Throwable error) {
        if (error instanceof ResponseStatusException) {
            return error;
        }
        log.error("[plugin-links] REST link application operation unavailable: errorType={}",
            error.getClass().getName());
        return LinkApplicationProblemException.unavailable();
    }

    private static boolean isJson(ServerRequest request) {
        return request.headers().contentType()
            .filter(MediaType.APPLICATION_JSON::isCompatibleWith)
            .isPresent();
    }

    private static org.springdoc.core.fn.builders.apiresponse.Builder jsonResponse(String code,
        Class<?> implementation) {
        return responseBuilder()
            .responseCode(code)
            .content(contentBuilder()
                .mediaType(MediaType.APPLICATION_JSON_VALUE)
                .schema(schemaBuilder().implementation(implementation)));
    }

    private static org.springdoc.core.fn.builders.apiresponse.Builder problemResponse(String code) {
        return responseBuilder()
            .responseCode(code)
            .content(contentBuilder()
                .mediaType(MediaType.APPLICATION_PROBLEM_JSON_VALUE)
                .schema(schemaBuilder().implementation(LinkApplicationProblemResponse.class)));
    }

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion("api.link.halo.run/v1alpha1");
    }
}
