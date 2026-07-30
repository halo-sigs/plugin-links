package run.halo.links.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;
import static org.springdoc.webflux.core.fn.SpringdocRouteBuilder.route;

import io.swagger.v3.oas.annotations.enums.ParameterIn;
import java.util.List;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.content.Comment;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;
import run.halo.app.extension.ListResult;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.links.dto.LinkApplicationCleanupResult;
import run.halo.links.dto.LinkApplicationOriginComment;
import run.halo.links.extension.Link;
import run.halo.links.extension.LinkApplication;
import run.halo.links.query.LinkApplicationQuery;
import run.halo.links.service.LinkApplicationApprovalService;
import run.halo.links.verification.LinkVerificationService;

@Component
@RequiredArgsConstructor
public class LinkApplicationEndpoint implements CustomEndpoint {

    private final ReactiveExtensionClient client;
    private final LinkApplicationApprovalService approvalService;
    private final LinkVerificationService verificationService;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        final var tag = "console.api.link.halo.run/v1alpha1/LinkApplication";
        return route()
            .GET("linkapplications", this::listLinkApplications, builder -> {
                builder.operationId("listLinkApplications")
                    .description("List paginated link application history.")
                    .tag(tag)
                    .response(responseBuilder()
                        .responseCode("200")
                        .implementation(ListResult.generateGenericClass(LinkApplication.class)));
                LinkApplicationQuery.buildParameters(builder);
            })
            .GET("linkapplications/{name}", this::getLinkApplication,
                builder -> builder.operationId("getLinkApplication")
                    .description("Get a link application by metadata name.")
                    .tag(tag)
                    .parameter(pathNameParameter())
                    .response(responseBuilder().responseCode("200")
                        .implementation(LinkApplication.class)))
            .GET("linkapplications/{name}/origin-comment", this::getOriginComment,
                builder -> builder.operationId("getLinkApplicationOriginComment")
                    .description("Get minimal source Comment context for one link application.")
                    .tag(tag)
                    .parameter(pathNameParameter())
                    .response(responseBuilder().responseCode("200")
                        .implementation(LinkApplicationOriginComment.class)))
            .DELETE("linkapplications/{name}", this::deleteLinkApplication,
                builder -> builder.operationId("deleteLinkApplication")
                    .description("Delete a link application unless approval is in progress.")
                    .tag(tag)
                    .parameter(pathNameParameter())
                    .response(responseBuilder().responseCode("200")))
            .POST("linkapplications/{name}/approve", this::approveLinkApplication,
                builder -> builder.operationId("approveLinkApplication")
                    .description("Approve or resume approving a link application.")
                    .tag(tag)
                    .parameter(pathNameParameter())
                    .requestBody(requestBodyBuilder()
                        .description("Optional approval field overrides.")
                        .implementation(ApproveRequest.class))
                    .response(responseBuilder().responseCode("200")
                        .implementation(Link.class)))
            .POST("linkapplications/{name}/reject", this::rejectLinkApplication,
                builder -> builder.operationId("rejectLinkApplication")
                    .description("Reject a pending link application.")
                    .tag(tag)
                    .parameter(pathNameParameter())
                    .response(responseBuilder().responseCode("200")))
            .POST("linkapplications/{name}/verify", this::verifyBacklink,
                builder -> builder.operationId("verifyBacklink")
                    .description("Manually verify this application's backlink.")
                    .tag(tag)
                    .parameter(pathNameParameter())
                    .requestBody(requestBodyBuilder()
                        .description("Optional backlink URL override.")
                        .implementation(VerifyRequest.class))
                    .response(responseBuilder().responseCode("200")
                        .implementation(VerifyResult.class)))
            .POST("linkapplications/-/cleanup", this::cleanupLinkApplications, builder -> {
                builder.operationId("cleanupLinkApplications")
                    .description("Delete every deletable application matching the list filters.")
                    .tag(tag)
                    .response(responseBuilder().responseCode("200")
                        .implementation(LinkApplicationCleanupResult.class));
                LinkApplicationQuery.buildParameters(builder);
            })
            .build();
    }

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion("console.api.link.halo.run/v1alpha1");
    }

    private Mono<ServerResponse> listLinkApplications(ServerRequest request) {
        var query = new LinkApplicationQuery(request.exchange());
        return client.listBy(LinkApplication.class, query.toListOptions(), query.toPageRequest())
            .flatMap(result -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(result));
    }

    private Mono<ServerResponse> getLinkApplication(ServerRequest request) {
        return client.fetch(LinkApplication.class, request.pathVariable("name"))
            .flatMap(application -> ServerResponse.ok().bodyValue(application))
            .switchIfEmpty(ServerResponse.notFound().build());
    }

    private Mono<ServerResponse> getOriginComment(ServerRequest request) {
        return client.fetch(LinkApplication.class, request.pathVariable("name"))
            .switchIfEmpty(Mono.error(notFound("Link application not found.")))
            .flatMap(application -> {
                var origin = application.getSpec().getOrigin();
                if (origin.getType() != LinkApplication.OriginType.COMMENT
                    || origin.getComment() == null
                    || StringUtils.isBlank(origin.getComment().getName())) {
                    return Mono.error(notFound("Source Comment is unavailable."));
                }
                return client.fetch(Comment.class, origin.getComment().getName())
                    .switchIfEmpty(Mono.error(notFound("Source Comment is unavailable.")));
            })
            .map(comment -> new LinkApplicationOriginComment(
                comment.getMetadata().getName(),
                comment.getSpec().getRaw(),
                comment.getSpec().getSubjectRef(),
                comment.getSpec().getCreationTime()))
            .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<ServerResponse> deleteLinkApplication(ServerRequest request) {
        return client.fetch(LinkApplication.class, request.pathVariable("name"))
            .switchIfEmpty(Mono.error(notFound("Link application not found.")))
            .flatMap(application -> {
                if (application.getSpec() != null
                    && application.getSpec().getStatus() == LinkApplication.Status.APPROVING) {
                    return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT,
                        "Approving applications cannot be deleted."));
                }
                return client.delete(application);
            })
            .then(ServerResponse.ok().build());
    }

    private Mono<ServerResponse> approveLinkApplication(ServerRequest request) {
        return request.bodyToMono(ApproveRequest.class)
            .defaultIfEmpty(new ApproveRequest())
            .flatMap(body -> approvalService.approve(request.pathVariable("name"),
                new LinkApplicationApprovalService.ApprovalCommand(
                    body.getUrl(), body.getDisplayName(), body.getLogo(),
                    body.getDescription(), body.getGroupName(), body.getBacklink(),
                    body.getFeedUrls())))
            .flatMap(link -> ServerResponse.ok().bodyValue(link));
    }

    private Mono<ServerResponse> rejectLinkApplication(ServerRequest request) {
        return client.fetch(LinkApplication.class, request.pathVariable("name"))
            .switchIfEmpty(Mono.error(notFound("Link application not found.")))
            .flatMap(application -> {
                var spec = application.getSpec();
                if (spec == null || spec.getStatus() != LinkApplication.Status.PENDING) {
                    return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT,
                        "Only pending link applications can be rejected."));
                }
                spec.setStatus(LinkApplication.Status.REJECTED);
                return client.update(application);
            })
            .then(ServerResponse.ok().build());
    }

    private Mono<ServerResponse> verifyBacklink(ServerRequest request) {
        return request.bodyToMono(VerifyRequest.class)
            .defaultIfEmpty(new VerifyRequest())
            .flatMap(body -> client.fetch(LinkApplication.class, request.pathVariable("name"))
                .switchIfEmpty(Mono.error(notFound("Link application not found.")))
                .flatMap(application -> {
                    String backlink = body.getBacklink() == null
                        ? application.getSpec().getBacklink()
                        : body.getBacklink();
                    if (StringUtils.isBlank(backlink)) {
                        return Mono.just(new VerifyResult(false, "未提供反链地址"));
                    }
                    return verificationService.verifyBacklink(backlink.trim())
                        .map(status -> switch (status.getState()) {
                            case FOUND -> new VerifyResult(true,
                                "反链已找到: " + status.getMatchedUrl());
                            case MISSING -> new VerifyResult(false,
                                "未在对方页面找到指向本站的链接");
                            case NOT_CONFIGURED -> new VerifyResult(false,
                                "未提供反链地址");
                            case FAILED -> new VerifyResult(false,
                                "验证失败: " + status.getError());
                            case CHECKING -> new VerifyResult(false,
                                "反链验证尚未完成");
                        });
                }))
            .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<ServerResponse> cleanupLinkApplications(ServerRequest request) {
        var query = new LinkApplicationQuery(request.exchange());
        return client.listAll(LinkApplication.class, query.toListOptions(), query.getSort())
            .collectList()
            .flatMap(applications -> Flux.fromIterable(applications)
                .concatMap(application -> {
                    if (application.getSpec() != null
                        && application.getSpec().getStatus()
                        == LinkApplication.Status.APPROVING) {
                        return Mono.just(CleanupOutcome.SKIPPED);
                    }
                    return client.delete(application)
                        .thenReturn(CleanupOutcome.DELETED)
                        .onErrorReturn(CleanupOutcome.FAILED);
                })
                .collectList()
                .map(outcomes -> new LinkApplicationCleanupResult(
                    applications.size(),
                    count(outcomes, CleanupOutcome.DELETED),
                    count(outcomes, CleanupOutcome.FAILED),
                    count(outcomes, CleanupOutcome.SKIPPED))))
            .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private static long count(Iterable<CleanupOutcome> outcomes, CleanupOutcome expected) {
        long count = 0;
        for (var outcome : outcomes) {
            if (outcome == expected) {
                count++;
            }
        }
        return count;
    }

    private static org.springdoc.core.fn.builders.parameter.Builder pathNameParameter() {
        return parameterBuilder()
            .name("name")
            .in(ParameterIn.PATH)
            .description("LinkApplication metadata name.")
            .implementation(String.class)
            .required(true);
    }

    private static ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private enum CleanupOutcome {
        DELETED,
        FAILED,
        SKIPPED
    }

    @Data
    public static class ApproveRequest {
        private String url;
        private String displayName;
        private String logo;
        private String description;
        private String groupName;
        private String backlink;
        private List<String> feedUrls;
    }

    @Data
    public static class VerifyRequest {
        private String backlink;
    }

    @Data
    public static class VerifyResult {
        private final boolean found;
        private final String message;
    }
}
