package run.halo.links.route;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RequestPredicates.POST;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;
import static run.halo.app.extension.index.query.Queries.equal;
import static run.halo.app.extension.index.query.Queries.isNull;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.server.HandlerFunction;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.util.UriComponentsBuilder;
import org.thymeleaf.context.LazyContextVariable;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ListOptions;
import run.halo.app.plugin.PluginContext;
import run.halo.app.plugin.ReactiveSettingFetcher;
import run.halo.links.extension.LinkApplication;
import run.halo.links.endpoint.LinkApplicationSettingsFetcher;
import run.halo.links.finders.LinkFinder;
import run.halo.links.security.LinkApplicationRateLimiter;
import run.halo.links.security.captcha.LinkApplicationCaptchaService;
import run.halo.links.service.LinkApplicationService;
import run.halo.links.service.LinkPublicQueryService;
import run.halo.links.vo.LinkGroupVo;
import run.halo.links.vo.LinkVo;

@Component
@RequiredArgsConstructor
@Slf4j
public class LinkRouter {

    private static final Duration BLOCKING_TIMEOUT = Duration.ofSeconds(10);
    static final String BASE_SETTING_GROUP = "base";
    private static final String TEMPLATE_ID = "_templateId";

    private final LinkFinder linkFinder;
    private final LinkPublicQueryService linkPublicQueryService;
    private final PluginContext pluginContext;
    private final ReactiveSettingFetcher settingFetcher;
    private final LinkApplicationSettingsFetcher applicationSettingsFetcher;
    private final LinkApplicationService applicationService;
    private final LinkApplicationRateLimiter rateLimiter;
    private final LinkApplicationCaptchaService captchaService;

    @Bean
    RouterFunction<ServerResponse> linkTemplateRoute() {
        return route(GET("/links"), listHandler())
            .andRoute(GET("/links/apply/captcha"), captchaHandler())
            .andRoute(POST("/links/apply/submit"), applyHandler());
    }

    private HandlerFunction<ServerResponse> applyHandler() {
        return request -> {
            var responseMode = selectResponseMode(request);
            if (responseMode == ResponseMode.NOT_ACCEPTABLE) {
                return ServerResponse.status(HttpStatus.NOT_ACCEPTABLE)
                    .header(HttpHeaders.VARY, HttpHeaders.ACCEPT)
                    .build();
            }
            boolean jsonPreferred = responseMode == ResponseMode.JSON;
            boolean formUrlEncoded = request.headers().contentType()
                .filter(MediaType.APPLICATION_FORM_URLENCODED::isCompatibleWith)
                .isPresent();
            if (!formUrlEncoded) {
                return jsonPreferred
                    ? jsonResponse(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                        "UNSUPPORTED_MEDIA_TYPE", null,
                        "仅支持 application/x-www-form-urlencoded 表单提交")
                    : ServerResponse.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                        .header(HttpHeaders.VARY, HttpHeaders.ACCEPT)
                        .build();
            }
            return applicationSettingsFetcher.fetch()
            .flatMap(settings -> {
                if (!settings.selfSubmissionEnabled()) {
                    return jsonPreferred
                        ? jsonResponse(HttpStatus.FORBIDDEN,
                            "APPLICATION_DISABLED", null, "友链申请功能暂未开放")
                        : redirectDisabled();
                }
                return request.formData()
                .flatMap(formData -> {
                    var verification = captchaService.verify(request,
                        getFormValue(formData, "captchaCode"));
                    request.exchange().getResponse().addCookie(verification.expiredCookie());
                    if (!verification.valid()) {
                        return jsonPreferred
                            ? jsonResponse(HttpStatus.UNPROCESSABLE_CONTENT,
                                "INVALID_CAPTCHA", "captchaCode",
                                "验证码错误或已过期，请重新输入")
                            : redirectCaptchaError();
                    }
                    var admission = rateLimiter.admit(request);
                    if (!admission.allowed()) {
                        return jsonPreferred
                            ? jsonRateLimited(admission.retryAfterSeconds())
                            : redirectWithError("提交过于频繁，请稍后再试");
                    }
                    String url = getFormValue(formData, "url");
                    String displayName = getFormValue(formData, "displayName");

                    var origin = new LinkApplication.Origin();
                    origin.setType(LinkApplication.OriginType.FORM);
                    var submission = new LinkApplicationService.Submission(
                        url,
                        displayName,
                        getFormValue(formData, "logo"),
                        getFormValue(formData, "description"),
                        getFormValue(formData, "email"),
                        getFormValue(formData, "backlink"),
                        parseFeedUrls(getFormValue(formData, "feedUrls")),
                        origin
                    );
                    return applicationService.create(submission)
                        .flatMap(result -> switch (result.status()) {
                            case CREATED -> jsonPreferred
                                ? jsonCreated()
                                : redirectSuccess();
                            case CAPACITY_REACHED -> jsonPreferred
                                ? jsonResponse(HttpStatus.CONFLICT,
                                    "CAPACITY_REACHED", null,
                                    "待审核申请数量已达上限，请稍后再试")
                                : redirectWithError("待审核申请数量已达上限，请稍后再试");
                            case DUPLICATE -> jsonPreferred
                                ? jsonResponse(HttpStatus.CONFLICT,
                                    "DUPLICATE_APPLICATION", result.field(), result.message())
                                : redirectWithFieldError(
                                    result.field(), result.value(), result.message());
                            case INVALID -> jsonPreferred
                                ? jsonResponse(HttpStatus.UNPROCESSABLE_CONTENT,
                                    "VALIDATION_FAILED", result.field(), result.message())
                                : redirectWithFieldError(result.field(), result.value(),
                                    result.message());
                        })
                        .doOnError(error -> log.error(
                            "[plugin-links] Failed to create link application: errorType={}",
                            error.getClass().getName()))
                        .onErrorResume(error -> jsonPreferred
                            ? jsonResponse(HttpStatus.SERVICE_UNAVAILABLE,
                                "APPLICATION_UNAVAILABLE", null,
                                "暂时无法提交，请稍后再试")
                            : redirectWithError("暂时无法提交，请稍后再试"));
                });
            });
        };
    }

    private static ResponseMode selectResponseMode(ServerRequest request) {
        var accepted = request.headers().accept();
        if (accepted.isEmpty()) {
            return ResponseMode.HTML;
        }
        double jsonQuality = acceptedQuality(accepted, MediaType.APPLICATION_JSON);
        double htmlQuality = acceptedQuality(accepted, MediaType.TEXT_HTML);
        if (jsonQuality > 0 && jsonQuality > htmlQuality) {
            return ResponseMode.JSON;
        }
        if (htmlQuality > 0) {
            return ResponseMode.HTML;
        }
        return ResponseMode.NOT_ACCEPTABLE;
    }

    private static double acceptedQuality(List<MediaType> accepted, MediaType produced) {
        int bestSpecificity = -1;
        double bestQuality = 0;
        for (var candidate : accepted) {
            if (!candidate.isCompatibleWith(produced)) {
                continue;
            }
            int specificity = candidate.isWildcardType()
                ? 0
                : candidate.isWildcardSubtype() ? 1 : 2;
            if (specificity > bestSpecificity) {
                bestSpecificity = specificity;
                bestQuality = candidate.getQualityValue();
            } else if (specificity == bestSpecificity) {
                bestQuality = Math.max(bestQuality, candidate.getQualityValue());
            }
        }
        return bestQuality;
    }

    private HandlerFunction<ServerResponse> captchaHandler() {
        return request -> applicationSettingsFetcher.fetch()
            .flatMap(settings -> {
                if (!settings.selfSubmissionEnabled()) {
                    return ServerResponse.notFound().build();
                }
                return captchaService.issue(request).flatMap(result -> switch (result.status()) {
                    case ISSUED -> ServerResponse.ok()
                        .contentType(MediaType.IMAGE_PNG)
                        .header(HttpHeaders.CACHE_CONTROL,
                            "no-store, no-cache, must-revalidate")
                        .cookie(result.cookie())
                        .bodyValue(result.png());
                    case RATE_LIMITED -> ServerResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                        .header(HttpHeaders.RETRY_AFTER,
                            Long.toString(result.retryAfterSeconds()))
                        .build();
                    case UNAVAILABLE ->
                        ServerResponse.status(HttpStatus.SERVICE_UNAVAILABLE).build();
                });
            });
    }

    private static String getFormValue(MultiValueMap<String, String> formData, String key) {
        List<String> values = formData.get(key);
        if (values == null || values.isEmpty()) {
            return null;
        }
        String value = values.get(0);
        return StringUtils.isBlank(value) ? null : value.trim();
    }

    private static List<String> parseFeedUrls(String value) {
        if (StringUtils.isBlank(value)) {
            return List.of();
        }
        return List.of(value.split("\\r?\\n")).stream()
            .map(String::trim)
            .filter(StringUtils::isNotBlank)
            .toList();
    }

    private static Mono<ServerResponse> redirectSuccess() {
        return ServerResponse.seeOther(
            UriComponentsBuilder.fromPath("/links")
                .queryParam("applied", "success")
                .build().toUri()
        ).header(HttpHeaders.VARY, HttpHeaders.ACCEPT).build();
    }

    private static Mono<ServerResponse> jsonCreated() {
        return jsonResponse(HttpStatus.CREATED, "success",
            "APPLICATION_CREATED", null, "申请提交成功", null);
    }

    private static Mono<ServerResponse> jsonRateLimited(long retryAfterSeconds) {
        return jsonResponse(HttpStatus.TOO_MANY_REQUESTS, "error",
            "RATE_LIMITED", null, "提交过于频繁，请稍后再试", retryAfterSeconds);
    }

    private static Mono<ServerResponse> jsonResponse(HttpStatus status, String code, String field,
        String message) {
        return jsonResponse(status, "error", code, field, message, null);
    }

    private static Mono<ServerResponse> jsonResponse(HttpStatus httpStatus, String status,
        String code, String field, String message, Long retryAfterSeconds) {
        var builder = ServerResponse.status(httpStatus)
            .contentType(MediaType.APPLICATION_JSON)
            .header(HttpHeaders.VARY, HttpHeaders.ACCEPT)
            .header(HttpHeaders.CACHE_CONTROL, "no-store");
        if (retryAfterSeconds != null) {
            builder.header(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
        }
        return builder.bodyValue(new ApplicationJsonResponse(status, code, field, message));
    }

    private static Mono<ServerResponse> redirectWithFieldError(String field, String value,
        String message) {
        var builder = UriComponentsBuilder.fromPath("/links")
            .queryParam("applied", "error")
            .queryParam("field", field)
            .queryParam("message", message);
        if (StringUtils.isNotBlank(value)) {
            builder.queryParam("value", value);
        }
        return ServerResponse.seeOther(builder.build().toUri())
            .header(HttpHeaders.VARY, HttpHeaders.ACCEPT)
            .build();
    }

    private static Mono<ServerResponse> redirectWithError(String message) {
        return ServerResponse.seeOther(
            UriComponentsBuilder.fromPath("/links")
                .queryParam("applied", "error")
                .queryParam("message", message)
                .build().toUri()
        ).header(HttpHeaders.VARY, HttpHeaders.ACCEPT).build();
    }

    private static Mono<ServerResponse> redirectCaptchaError() {
        return ServerResponse.seeOther(
            UriComponentsBuilder.fromPath("/links")
                .queryParam("applied", "error")
                .queryParam("field", "captchaCode")
                .queryParam("message", "验证码错误或已过期，请重新输入")
                .build().toUri()
        ).header(HttpHeaders.VARY, HttpHeaders.ACCEPT).build();
    }

    private static Mono<ServerResponse> redirectDisabled() {
        return ServerResponse.seeOther(
            UriComponentsBuilder.fromPath("/links")
                .queryParam("applied", "disabled")
                .queryParam("message", "友链申请功能暂未开放")
                .build().toUri()
        ).header(HttpHeaders.VARY, HttpHeaders.ACCEPT).build();
    }

    private HandlerFunction<ServerResponse> listHandler() {
        return request -> {
            String group = queryParam(request, "group");

            var links = new LazyContextVariable<List<LinkVo>>() {
                @Override
                protected List<LinkVo> loadValue() {
                    return loadLinks(group).block(BLOCKING_TIMEOUT);
                }
            };

            var simpleGroups = new LazyContextVariable<List<LinkGroupVo>>() {
                @Override
                protected List<LinkGroupVo> loadValue() {
                    return linkPublicQueryService.listAllGroups(ListOptions.builder().build())
                        .block(BLOCKING_TIMEOUT);
                }
            };

            var groups = new LazyContextVariable<List<LinkGroupVo>>() {
                @Override
                protected List<LinkGroupVo> loadValue() {
                    return linkFinder.groupBy().collectList().block(BLOCKING_TIMEOUT);
                }
            };

            var linksTitle = new LazyContextVariable<String>() {
                @Override
                protected String loadValue() {
                    return getLinkTitle().block(BLOCKING_TIMEOUT);
                }
            };

            @SuppressWarnings("unchecked")
            Mono<CsrfToken> csrfTokenMono = request.exchange()
                .getAttributeOrDefault(CsrfToken.class.getName(), Mono.empty());

            var applicationEnabledMono = applicationSettingsFetcher.fetch()
                .map(settings -> settings.selfSubmissionEnabled());

            return Mono.zip(
                    csrfTokenMono.map(CsrfToken::getToken).defaultIfEmpty(""),
                    applicationEnabledMono
                )
                .map(tuple -> {
                    Map<String, Object> model = new HashMap<>();
                    model.put("links", links);
                    model.put("simpleGroups", simpleGroups);
                    model.put("groups", groups);
                    model.put("group", group);
                    model.put("pluginName", pluginContext.getName());
                    model.put("linksTitle", linksTitle);
                    model.put("csrfToken", tuple.getT1());
                    model.put("linkApplicationEnabled", tuple.getT2());
                    model.put(TEMPLATE_ID, "links");
                    return model;
                })
                .flatMap(model -> ServerResponse.ok().render("links", model));
        };
    }

    private Mono<List<LinkVo>> loadLinks(String group) {
        var options = ListOptions.builder();
        options.andQuery(isNull("metadata.deletionTimestamp"));
        if (StringUtils.isNotBlank(group)) {
            options.andQuery(equal("spec.groupName", group));
        }
        return linkPublicQueryService.listAll(options.build(), defaultLinkSort());
    }

    private static String queryParam(ServerRequest request, String name) {
        return request.queryParam(name)
            .filter(StringUtils::isNotBlank)
            .orElse(null);
    }

    Mono<String> getLinkTitle() {
        return this.settingFetcher.fetch(BASE_SETTING_GROUP, LinkBaseSettings.class)
            .defaultIfEmpty(LinkBaseSettings.defaults())
            .map(LinkBaseSettings::normalizedTitle)
            .onErrorReturn(LinkBaseSettings.DEFAULT_TITLE);
    }

    static Sort defaultLinkSort() {
        return Sort.by(
            Sort.Order.asc("spec.priority"),
            Sort.Order.asc("metadata.creationTimestamp"),
            Sort.Order.asc("metadata.name")
        );
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record ApplicationJsonResponse(
        String status,
        String code,
        String field,
        String message
    ) {
    }

    private enum ResponseMode {
        HTML,
        JSON,
        NOT_ACCEPTABLE
    }
}
