package run.halo.links.route;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RequestPredicates.POST;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;
import static run.halo.app.extension.index.query.Queries.equal;
import static run.halo.app.extension.index.query.Queries.isNull;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.Sort;
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
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.plugin.PluginContext;
import run.halo.app.plugin.ReactiveSettingFetcher;
import run.halo.links.extension.LinkApplication;
import run.halo.links.finders.LinkFinder;
import run.halo.links.security.LinkApplicationRateLimiter;
import run.halo.links.service.LinkPublicQueryService;
import run.halo.links.vo.LinkGroupVo;
import run.halo.links.vo.LinkVo;

@Component
@RequiredArgsConstructor
public class LinkRouter {

    private static final Duration BLOCKING_TIMEOUT = Duration.ofSeconds(10);
    private static final String TEMPLATE_ID = "_templateId";

    private final LinkFinder linkFinder;
    private final LinkPublicQueryService linkPublicQueryService;
    private final PluginContext pluginContext;
    private final ReactiveSettingFetcher settingFetcher;
    private final ReactiveExtensionClient extensionClient;
    private final LinkApplicationRateLimiter rateLimiter = new LinkApplicationRateLimiter();

    @Bean
    RouterFunction<ServerResponse> linkTemplateRoute() {
        return route(GET("/links"), listHandler())
            .andRoute(POST("/links/apply").and(
                contentType(MediaType.APPLICATION_FORM_URLENCODED)), applyHandler());
    }

    private static org.springframework.web.reactive.function.server.RequestPredicate contentType(
        MediaType mediaType) {
        return org.springframework.web.reactive.function.server.RequestPredicates.contentType(
            mediaType);
    }

    private HandlerFunction<ServerResponse> applyHandler() {
        return request -> {
            if (!rateLimiter.isAllowed(request)) {
                return redirectWithError("提交过于频繁，请稍后再试");
            }

            return request.formData()
                .flatMap(formData -> {
                    String url = getFormValue(formData, "url");
                    String displayName = getFormValue(formData, "displayName");

                    // Validate required fields
                if (StringUtils.isBlank(url)) {
                    return redirectWithFieldError("url", url, "URL不能为空");
                }
                if (StringUtils.isBlank(displayName)) {
                    return redirectWithFieldError("displayName", displayName, "网站名称不能为空");
                }

                // Validate URL format
                try {
                    new URL(url);
                } catch (MalformedURLException e) {
                    return redirectWithFieldError("url", url, "URL格式错误");
                }

                String normalizedUrl = url.trim();

                // Check for duplicates (PENDING or REJECTED)
                return checkDuplicate(normalizedUrl)
                    .flatMap(isDuplicate -> {
                        if (isDuplicate) {
                            return redirectWithFieldError("url", normalizedUrl,
                                "该链接已提交申请");
                        }

                        // Create LinkApplication
                        LinkApplication application = new LinkApplication();
                        application.setMetadata(new run.halo.app.extension.Metadata());
                        application.getMetadata().setGenerateName("link-app-");

                        LinkApplication.LinkApplicationSpec spec =
                            new LinkApplication.LinkApplicationSpec();
                        spec.setUrl(normalizedUrl);
                        spec.setDisplayName(displayName.trim());
                        spec.setLogo(getFormValue(formData, "logo"));
                        spec.setDescription(getFormValue(formData, "description"));
                        spec.setEmail(getFormValue(formData, "email"));
                        spec.setBacklink(getFormValue(formData, "backlink"));

                        String feedUrlsStr = getFormValue(formData, "feedUrls");
                        if (StringUtils.isNotBlank(feedUrlsStr)) {
                            spec.setFeedUrls(
                                List.of(feedUrlsStr.split("\\r?\\n"))
                                    .stream()
                                    .map(String::trim)
                                    .filter(StringUtils::isNotBlank)
                                    .toList()
                            );
                        }

                        spec.setStatus(LinkApplication.Status.PENDING);
                        application.setSpec(spec);

                        return extensionClient.create(application)
                            .then(redirectSuccess());
                    });
            });
        };
    }

    private static String getFormValue(MultiValueMap<String, String> formData, String key) {
        List<String> values = formData.get(key);
        if (values == null || values.isEmpty()) {
            return null;
        }
        String value = values.get(0);
        return StringUtils.isBlank(value) ? null : value.trim();
    }

    private Mono<Boolean> checkDuplicate(String url) {
        var options = ListOptions.builder()
            .andQuery(equal("spec.url", url))
            .andQuery(equal("spec.status", LinkApplication.Status.PENDING.name())
                .or(equal("spec.status", LinkApplication.Status.REJECTED.name())))
            .build();
        return extensionClient.listAll(LinkApplication.class, options, Sort.unsorted())
            .hasElements();
    }

    private static Mono<ServerResponse> redirectSuccess() {
        return ServerResponse.seeOther(
            UriComponentsBuilder.fromPath("/links")
                .queryParam("applied", "success")
                .build().toUri()
        ).build();
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
        return ServerResponse.seeOther(builder.build().toUri()).build();
    }

    private static Mono<ServerResponse> redirectWithError(String message) {
        return ServerResponse.seeOther(
            UriComponentsBuilder.fromPath("/links")
                .queryParam("applied", "error")
                .queryParam("message", message)
                .build().toUri()
        ).build();
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

            return csrfTokenMono
                .map(CsrfToken::getToken)
                .defaultIfEmpty("")
                .map(csrfToken -> {
                    Map<String, Object> model = new HashMap<>();
                    model.put("links", links);
                    model.put("simpleGroups", simpleGroups);
                    model.put("groups", groups);
                    model.put("group", group);
                    model.put("pluginName", pluginContext.getName());
                    model.put("linksTitle", linksTitle);
                    model.put("csrfToken", csrfToken);
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
        return this.settingFetcher.getSettingValue("base")
            .map(setting -> setting.get("title").asText())
            .defaultIfEmpty("链接");
    }

    static Sort defaultLinkSort() {
        return Sort.by(
            Sort.Order.asc("spec.priority"),
            Sort.Order.asc("metadata.creationTimestamp"),
            Sort.Order.asc("metadata.name")
        );
    }
}
