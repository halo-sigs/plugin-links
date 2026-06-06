package run.halo.links.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;
import static org.springdoc.webflux.core.fn.SpringdocRouteBuilder.route;

import io.swagger.v3.oas.annotations.enums.ParameterIn;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Document;
import org.springdoc.core.fn.builders.content.Builder;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ListResult;
import run.halo.app.extension.MetadataUtil;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.router.IListRequest;
import run.halo.app.extension.router.selector.FieldSelector;
import run.halo.app.infra.ExternalUrlSupplier;
import run.halo.links.extension.Link;
import run.halo.links.extension.LinkApplication;
import run.halo.links.security.SafeUrlFetcher;

@Component
@RequiredArgsConstructor
public class LinkApplicationEndpoint implements CustomEndpoint {

    private final ReactiveExtensionClient client;
    private final ExternalUrlSupplier externalUrlSupplier;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        final var tag = "console.api.link.halo.run/v1alpha1/LinkApplication";
        return route()
            .GET("linkapplications", this::listLinkApplications,
                builder -> builder.operationId("listLinkApplications")
                    .description("List link applications with optional status filter.")
                    .tag(tag)
                    .parameter(parameterBuilder()
                        .name("status")
                        .in(ParameterIn.QUERY)
                        .description("Filter by application status.")
                        .implementation(String.class)
                    )
                    .response(responseBuilder()
                        .responseCode("200")
                        .implementation(ListResult.generateGenericClass(LinkApplication.class)))
            )
            .GET("linkapplications/{name}", this::getLinkApplication,
                builder -> builder.operationId("getLinkApplication")
                    .description("Get a link application by metadata name.")
                    .tag(tag)
                    .parameter(parameterBuilder()
                        .name("name")
                        .in(ParameterIn.PATH)
                        .description("Metadata name of the link application.")
                        .implementation(String.class)
                        .required(true)
                    )
                    .response(responseBuilder()
                        .responseCode("200")
                        .implementation(LinkApplication.class))
            )
            .DELETE("linkapplications/{name}", this::deleteLinkApplication,
                builder -> builder.operationId("deleteLinkApplication")
                    .description("Delete a link application.")
                    .tag(tag)
                    .parameter(parameterBuilder()
                        .name("name")
                        .in(ParameterIn.PATH)
                        .description("Metadata name of the link application to delete.")
                        .implementation(String.class)
                        .required(true)
                    )
                    .response(responseBuilder().responseCode("200"))
            )
            .POST("linkapplications/{name}/approve", this::approveLinkApplication,
                builder -> builder.operationId("approveLinkApplication")
                    .description("Approve a link application, creating a Link.")
                    .tag(tag)
                    .parameter(parameterBuilder()
                        .name("name")
                        .in(ParameterIn.PATH)
                        .description("Metadata name of the link application to approve.")
                        .implementation(String.class)
                        .required(true)
                    )
                    .requestBody(requestBodyBuilder()
                        .description("Approval request with optional field overrides and group assignment.")
                        .implementation(ApproveRequest.class))
                    .response(responseBuilder()
                        .responseCode("200")
                        .implementation(Link.class))
            )
            .POST("linkapplications/{name}/reject", this::rejectLinkApplication,
                builder -> builder.operationId("rejectLinkApplication")
                    .description("Reject a link application.")
                    .tag(tag)
                    .parameter(parameterBuilder()
                        .name("name")
                        .in(ParameterIn.PATH)
                        .description("Metadata name of the link application to reject.")
                        .implementation(String.class)
                        .required(true)
                    )
                    .response(responseBuilder().responseCode("200"))
            )
            .POST("linkapplications/{name}/verify", this::verifyBacklink,
                builder -> builder.operationId("verifyBacklink")
                    .description("Manually verify the backlink for a link application.")
                    .tag(tag)
                    .parameter(parameterBuilder()
                        .name("name")
                        .in(ParameterIn.PATH)
                        .description("Metadata name of the link application.")
                        .implementation(String.class)
                        .required(true)
                    )
                    .response(responseBuilder()
                        .responseCode("200")
                        .implementation(VerifyResult.class))
            )
            .build();
    }

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion("console.api.link.halo.run/v1alpha1");
    }

    private Mono<ServerResponse> listLinkApplications(ServerRequest request) {
        String status = request.queryParam("status").orElse(null);
        var listOptions = ListOptions.builder().build();
        if (StringUtils.isNotBlank(status)) {
            var fieldSelector = run.halo.app.extension.router.selector.FieldSelector.of(
                run.halo.app.extension.index.query.Queries.equal("spec.status", status)
            );
            listOptions.setFieldSelector(fieldSelector);
        }
        return client.listAll(LinkApplication.class, listOptions,
                Sort.by(Sort.Order.desc("metadata.creationTimestamp")))
            .collectList()
            .flatMap(list -> ServerResponse.ok().bodyValue(
                new ListResult<>(0, 0, list.size(), list)));
    }

    private Mono<ServerResponse> getLinkApplication(ServerRequest request) {
        String name = request.pathVariable("name");
        return client.fetch(LinkApplication.class, name)
            .flatMap(app -> ServerResponse.ok().bodyValue(app))
            .switchIfEmpty(ServerResponse.notFound().build());
    }

    private Mono<ServerResponse> deleteLinkApplication(ServerRequest request) {
        String name = request.pathVariable("name");
        return client.fetch(LinkApplication.class, name)
            .flatMap(client::delete)
            .then(ServerResponse.ok().build())
            .switchIfEmpty(ServerResponse.notFound().build());
    }

    private Mono<ServerResponse> approveLinkApplication(ServerRequest request) {
        String name = request.pathVariable("name");
        return request.bodyToMono(ApproveRequest.class)
            .flatMap(approveReq -> client.fetch(LinkApplication.class, name)
                .flatMap(application -> {
                    var appSpec = application.getSpec();
                    ensurePending(appSpec, "approved");

                    // Create Link
                    Link link = new Link();
                    link.setMetadata(new run.halo.app.extension.Metadata());
                    link.getMetadata().setGenerateName("link-");

                    Link.LinkSpec linkSpec = new Link.LinkSpec();
                    linkSpec.setUrl(getOrDefault(approveReq.getUrl(), appSpec.getUrl()));
                    linkSpec.setDisplayName(
                        getOrDefault(approveReq.getDisplayName(), appSpec.getDisplayName()));
                    linkSpec.setLogo(getOrDefault(approveReq.getLogo(), appSpec.getLogo()));
                    linkSpec.setDescription(
                        getOrDefault(approveReq.getDescription(), appSpec.getDescription()));
                    linkSpec.setGroupName(approveReq.getGroupName());
                    linkSpec.setPriority(0);

                    if (appSpec.getFeedUrls() != null && !appSpec.getFeedUrls().isEmpty()) {
                        Link.RssSpec rss = new Link.RssSpec();
                        rss.setEnabled(true);
                        rss.setFeedUrls(appSpec.getFeedUrls());
                        linkSpec.setRss(rss);
                    }

                    if (StringUtils.isNotBlank(appSpec.getBacklink())) {
                        Link.VerificationSpec verification = new Link.VerificationSpec();
                        verification.setBacklinkScanUrl(appSpec.getBacklink());
                        linkSpec.setVerification(verification);
                    }

                    link.setSpec(linkSpec);

                    return client.create(link)
                        .flatMap(createdLink -> {
                            appSpec.setStatus(LinkApplication.Status.APPROVED);
                            return client.update(application)
                                .thenReturn(createdLink);
                        });
                })
            )
            .flatMap(createdLink -> ServerResponse.ok().bodyValue(createdLink))
            .switchIfEmpty(ServerResponse.notFound().build());
    }

    private Mono<ServerResponse> rejectLinkApplication(ServerRequest request) {
        String name = request.pathVariable("name");
        return client.fetch(LinkApplication.class, name)
            .flatMap(application -> {
                var appSpec = application.getSpec();
                ensurePending(appSpec, "rejected");
                appSpec.setStatus(LinkApplication.Status.REJECTED);
                return client.update(application);
            })
            .flatMap(app -> ServerResponse.ok().build())
            .switchIfEmpty(ServerResponse.notFound().build());
    }

    private Mono<ServerResponse> verifyBacklink(ServerRequest request) {
        String name = request.pathVariable("name");
        return client.fetch(LinkApplication.class, name)
            .flatMap(application -> Mono.fromCallable(() -> {
                String backlink = application.getSpec().getBacklink();
                if (StringUtils.isBlank(backlink)) {
                    return new VerifyResult(false, "未提供反链地址");
                }

                URI targetUri = getExternalUri();
                if (targetUri == null) {
                    return new VerifyResult(false, "Halo 外部访问地址未配置");
                }

                try {
                    SafeUrlFetcher.FetchResult result =
                        SafeUrlFetcher.fetch(backlink.trim(),
                            SafeUrlFetcher.FetchOptions.verificationHtml(null, 5 * 1024 * 1024));
                    if (!isSuccessStatus(result.statusCode())) {
                        return new VerifyResult(false,
                            "反链页面返回 HTTP " + result.statusCode());
                    }
                    Document document = result.document();
                    if (document == null) {
                        document = org.jsoup.Jsoup.parse(result.body(),
                            result.url().toExternalForm());
                    }
                    Optional<String> matched = findBacklink(document, targetUri);
                    if (matched.isPresent()) {
                        return new VerifyResult(true,
                            "反链已找到: " + matched.get());
                    }
                    return new VerifyResult(false, "未在对方页面找到指向本站的链接");
                } catch (Exception e) {
                    return new VerifyResult(false,
                        "验证失败: " + e.getMessage());
                }
            }).subscribeOn(Schedulers.boundedElastic()))
            .flatMap(result -> ServerResponse.ok().bodyValue(result))
            .switchIfEmpty(ServerResponse.notFound().build());
    }

    private URI getExternalUri() {
        try {
            URL externalUrl = externalUrlSupplier.getRaw();
            if (externalUrl == null) {
                return null;
            }
            URI uri = externalUrl.toURI().normalize();
            if (StringUtils.isBlank(uri.getScheme()) || StringUtils.isBlank(uri.getHost())) {
                return null;
            }
            return uri;
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isSuccessStatus(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    private static Optional<String> findBacklink(Document document, URI targetUri) {
        if (document == null) {
            return Optional.empty();
        }
        return document.select("a[href]")
            .stream()
            .map(element -> element.absUrl("href"))
            .filter(href -> StringUtils.isNotBlank(href))
            .map(String::trim)
            .filter(href -> pointsToTarget(href, targetUri))
            .findFirst();
    }

    private static boolean pointsToTarget(String href, URI targetUri) {
        try {
            URI hrefUri = URI.create(href).normalize();
            if (!sameOrigin(hrefUri, targetUri)) {
                return false;
            }
            String targetPath = normalizePath(targetUri.getPath());
            if ("/".equals(targetPath)) {
                return true;
            }
            String hrefPath = normalizePath(hrefUri.getPath());
            return hrefPath.equals(targetPath) || hrefPath.startsWith(targetPath + "/");
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean sameOrigin(URI left, URI right) {
        return Objects.equals(lower(left.getScheme()), lower(right.getScheme()))
            && Objects.equals(lower(left.getHost()), lower(right.getHost()))
            && effectivePort(left) == effectivePort(right);
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() != -1) {
            return uri.getPort();
        }
        String scheme = lower(uri.getScheme());
        if ("http".equals(scheme)) {
            return 80;
        }
        if ("https".equals(scheme)) {
            return 443;
        }
        return -1;
    }

    private static String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        String normalized = path;
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        if (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static String lower(String s) {
        return s == null ? null : s.toLowerCase(java.util.Locale.ROOT);
    }

    private static String getOrDefault(String override, String original) {
        return StringUtils.isNotBlank(override) ? override.trim() : original;
    }

    private static void ensurePending(LinkApplication.LinkApplicationSpec spec, String operation) {
        if (spec.getStatus() != LinkApplication.Status.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Only pending link applications can be " + operation + ".");
        }
    }

    @Data
    public static class ApproveRequest {
        private String url;
        private String displayName;
        private String logo;
        private String description;
        private String groupName;
    }

    @Data
    public static class VerifyResult {
        private final boolean found;
        private final String message;
    }
}
