package run.halo.links.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.content.comment.CommentSubject;
import run.halo.app.core.extension.content.Comment;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ListResult;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.PageRequest;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.Ref;
import run.halo.app.plugin.extensionpoint.ExtensionGetter;
import run.halo.links.extension.Link;
import run.halo.links.extension.LinkApplication;
import run.halo.links.service.LinkApplicationApprovalService;
import run.halo.links.verification.LinkVerificationService;
import run.halo.mcpserver.api.McpToolDefinition;
import run.halo.mcpserver.api.McpToolInvocation;
import run.halo.mcpserver.api.McpToolResult;

@ExtendWith(MockitoExtension.class)
class LinkApplicationToolProviderTest {

    @Mock
    ReactiveExtensionClient client;

    @Mock
    LinkApplicationApprovalService approvalService;

    @Mock
    LinkVerificationService verificationService;

    @Mock
    ExtensionGetter extensionGetter;

    @Mock
    CommentSubject<?> commentSubject;

    LinkApplicationToolProvider provider;

    @BeforeEach
    void setUp() {
        provider = new LinkApplicationToolProvider(client, approvalService, verificationService,
            extensionGetter);
    }

    @Test
    void shouldExposeSixToolsWithAccurateAnnotations() {
        assertThat(provider.tools().map(McpToolDefinition::name).collectList().block())
            .containsExactly("list_link_applications", "get_link_application",
                "verify_link_application", "approve_link_application", "reject_link_application",
                "delete_link_application");
        assertThat(tool("get_link_application").annotations().readOnlyHint()).isTrue();
        assertThat(tool("verify_link_application").annotations().readOnlyHint()).isTrue();
        assertThat(tool("verify_link_application").annotations().openWorldHint()).isTrue();
        assertThat(tool("approve_link_application").annotations().readOnlyHint()).isFalse();
        assertThat(tool("approve_link_application").annotations().openWorldHint()).isTrue();
        assertThat(tool("delete_link_application").annotations().destructiveHint()).isTrue();
    }

    @Test
    void shouldPaginateAndFilterWithoutExposingApplicantIdentity() {
        var application = application(LinkApplication.Status.PENDING);
        when(client.listBy(eq(LinkApplication.class), any(), any()))
            .thenReturn(Mono.just(new ListResult<>(2, 10, 42, List.of(application))));

        var result = invoke("list_link_applications", Map.of("page", 2, "size", 10,
            "status", "pending", "originType", "form"));

        assertThat(result.error()).isFalse();
        assertThat(result.structuredContent()).containsEntry("page", 2).containsEntry("size", 10);
        assertThat(((Number) result.structuredContent().get("total")).longValue()).isEqualTo(42);
        assertThat(result.structuredContent().toString()).doesNotContain("private@example.com",
            "internal-secret", "email", "metadata", "approval");
        var options = ArgumentCaptor.forClass(ListOptions.class);
        var page = ArgumentCaptor.forClass(PageRequest.class);
        verify(client).listBy(eq(LinkApplication.class), options.capture(), page.capture());
        assertThat(page.getValue().getPageNumber()).isEqualTo(2);
        assertThat(page.getValue().getPageSize()).isEqualTo(10);
        assertThat(page.getValue().getSort().getOrderFor("metadata.creationTimestamp"))
            .isNotNull();
        assertThat(options.getValue().getFieldSelector().toString())
            .contains("spec.status", "PENDING", "spec.origin.type", "FORM");
    }

    @Test
    void shouldRejectInvalidFiltersAndUnboundedPageSize() {
        for (var args : List.of(Map.<String, Object>of("size", 101),
            Map.<String, Object>of("page", 0),
            Map.<String, Object>of("status", "unknown"),
            Map.<String, Object>of("originType", "unknown"))) {
            assertThat(invoke("list_link_applications", args).error()).isTrue();
        }
        verifyNoInteractions(client);
    }

    @Test
    void shouldOmitSourceCommentUnlessRequested() {
        var application = commentApplication();
        when(client.fetch(LinkApplication.class, "application-a"))
            .thenReturn(Mono.just(application));

        var result = invoke("get_link_application", Map.of("name", "application-a"));

        assertThat(result.error()).isFalse();
        assertThat(result.structuredContent()).containsEntry("commentName", "comment-a")
            .doesNotContainKeys("originComment", "email", "metadata", "approval");
        verify(client, never()).fetch(eq(Comment.class), any());
        verifyNoInteractions(extensionGetter);
    }

    @Test
    void shouldReturnOnlyMinimalSourceCommentAndSubjectContext() {
        when(client.fetch(LinkApplication.class, "application-a"))
            .thenReturn(Mono.just(commentApplication()));
        var comment = new Comment();
        comment.setMetadata(metadata("comment-a"));
        var spec = new Comment.CommentSpec();
        spec.setRaw("Please add my site.");
        spec.setContent("<p>Rendered content must not be returned</p>");
        spec.setIpAddress("192.0.2.10");
        spec.setUserAgent("private-agent");
        spec.setCreationTime(Instant.parse("2026-01-01T00:00:00Z"));
        spec.setApproved(false);
        spec.setHidden(true);
        var ref = new Ref();
        ref.setGroup("content.halo.run");
        ref.setKind("Post");
        ref.setName("post-a");
        spec.setSubjectRef(ref);
        comment.setSpec(spec);
        when(client.fetch(Comment.class, "comment-a")).thenReturn(Mono.just(comment));
        when(extensionGetter.getExtensions(CommentSubject.class))
            .thenReturn(Flux.just(commentSubject));
        when(commentSubject.supports(ref)).thenReturn(true);
        when(commentSubject.getSubjectDisplay("post-a"))
            .thenReturn(Mono.just(new CommentSubject.SubjectDisplay("Article",
                "https://example.com/post-a", "Post")));

        var result = invoke("get_link_application",
            Map.of("name", "application-a", "includeOriginComment", true));

        assertThat(result.error()).isFalse();
        var context = (Map<?, ?>) result.structuredContent().get("originComment");
        assertThat(context.get("raw")).isEqualTo("Please add my site.");
        assertThat(context.get("subject").toString()).contains("Article", "post-a");
        assertThat(context.keySet().stream().map(Object::toString).toList())
            .containsExactly("name", "raw", "creationTime", "approved", "hidden",
                "subjectRef", "subject");
        assertThat(result.structuredContent().toString()).doesNotContain("private@example.com",
            "internal-secret", "Rendered content", "192.0.2.10", "private-agent", "owner");
    }

    @Test
    void shouldStillReturnApplicationWhenSourceCommentWasDeleted() {
        when(client.fetch(LinkApplication.class, "application-a"))
            .thenReturn(Mono.just(commentApplication()));
        when(client.fetch(Comment.class, "comment-a")).thenReturn(Mono.empty());

        var result = invoke("get_link_application",
            Map.of("name", "application-a", "includeOriginComment", true));

        assertThat(result.error()).isFalse();
        assertThat(result.structuredContent()).containsEntry("name", "application-a")
            .doesNotContainKey("originComment");
    }

    @Test
    void shouldVerifyOverrideWithoutChangingApprovalState() {
        var application = application(LinkApplication.Status.PENDING);
        application.getSpec().setBacklink("https://original.example/links");
        when(client.fetch(LinkApplication.class, "application-a"))
            .thenReturn(Mono.just(application));
        var status = new Link.BacklinkStatus();
        status.setState(Link.BacklinkState.FOUND);
        status.setMatchedUrl("https://example.com/");
        when(verificationService.verifyBacklink("https://edited.example/links"))
            .thenReturn(Mono.just(status));

        var result = invoke("verify_link_application", Map.of("name", "application-a",
            "backlink", " https://edited.example/links "));

        assertThat(result.error()).isFalse();
        assertThat(result.structuredContent()).containsEntry("state", "FOUND")
            .containsEntry("found", true);
        assertThat(application.getSpec().getStatus()).isEqualTo(LinkApplication.Status.PENDING);
        assertThat(application.getSpec().getBacklink()).isEqualTo("https://original.example/links");
        verify(client, never()).update(any());
        verifyNoInteractions(approvalService);
    }

    @Test
    void shouldReportMissingBacklinkWithoutFetchingExternalPages() {
        when(client.fetch(LinkApplication.class, "application-a"))
            .thenReturn(Mono.just(application(LinkApplication.Status.PENDING)));

        var result = invoke("verify_link_application", Map.of("name", "application-a"));

        assertThat(result.structuredContent()).containsEntry("state", "NOT_CONFIGURED")
            .containsEntry("found", false);
        verifyNoInteractions(verificationService, approvalService);
        verify(client, never()).update(any());
    }

    @Test
    void shouldDelegateApprovalAndPreserveAbsentFeedOverride() {
        var link = new Link();
        link.setMetadata(metadata("link-a"));
        var spec = new Link.LinkSpec();
        spec.setDisplayName("Approved site");
        spec.setUrl("https://approved.example");
        link.setSpec(spec);
        when(approvalService.approve(eq("application-a"), any())).thenReturn(Mono.just(link));

        var result = invoke("approve_link_application",
            Map.of("name", "application-a", "displayName", "Approved site",
                "groupName", "group-a"));

        assertThat(result.error()).isFalse();
        assertThat(result.structuredContent()).containsEntry("status", "APPROVED");
        assertThat(result.structuredContent().toString()).doesNotContain("internal-secret",
            "metadata");
        var command = ArgumentCaptor.forClass(LinkApplicationApprovalService.ApprovalCommand.class);
        verify(approvalService).approve(eq("application-a"), command.capture());
        assertThat(command.getValue().displayName()).isEqualTo("Approved site");
        assertThat(command.getValue().groupName()).isEqualTo("group-a");
        assertThat(command.getValue().feedUrls()).isNull();
        verifyNoInteractions(client);
    }

    @Test
    void shouldForwardExplicitEmptyFeedOverride() {
        var link = new Link();
        link.setMetadata(metadata("link-a"));
        link.setSpec(new Link.LinkSpec());
        link.getSpec().setUrl("https://example.com");
        link.getSpec().setDisplayName("Example");
        when(approvalService.approve(eq("application-a"), any())).thenReturn(Mono.just(link));

        assertThat(invoke("approve_link_application",
            Map.of("name", "application-a", "feedUrls", List.of())).error()).isFalse();

        var command = ArgumentCaptor.forClass(LinkApplicationApprovalService.ApprovalCommand.class);
        verify(approvalService).approve(eq("application-a"), command.capture());
        assertThat(command.getValue().feedUrls()).isEmpty();
    }

    @Test
    void shouldRejectPendingApplication() {
        var application = application(LinkApplication.Status.PENDING);
        when(client.fetch(LinkApplication.class, "application-a"))
            .thenReturn(Mono.just(application));
        when(client.update(application)).thenReturn(Mono.just(application));

        var result = invoke("reject_link_application", Map.of("name", "application-a"));

        assertThat(result.error()).isFalse();
        assertThat(result.structuredContent()).containsEntry("status", "REJECTED");
        verify(client).update(application);
    }

    @ParameterizedTest
    @EnumSource(value = LinkApplication.Status.class, names = {"APPROVING", "APPROVED", "REJECTED"})
    void shouldRejectOnlyPendingApplications(LinkApplication.Status status) {
        when(client.fetch(LinkApplication.class, "application-a"))
            .thenReturn(Mono.just(application(status)));

        assertThat(invoke("reject_link_application", Map.of("name", "application-a")).error())
            .isTrue();

        verify(client, never()).update(any());
    }

    @Test
    void shouldPreventDeleteWhileApproving() {
        when(client.fetch(LinkApplication.class, "application-a"))
            .thenReturn(Mono.just(application(LinkApplication.Status.APPROVING)));

        assertThat(invoke("delete_link_application", Map.of("name", "application-a")).error())
            .isTrue();

        verify(client, never()).delete(any());
    }

    @ParameterizedTest
    @EnumSource(value = LinkApplication.Status.class, names = {"PENDING", "APPROVED", "REJECTED"})
    void shouldDeleteOnlyApplicationHistory(LinkApplication.Status status) {
        var application = application(status);
        when(client.fetch(LinkApplication.class, "application-a"))
            .thenReturn(Mono.just(application));
        when(client.delete(application)).thenReturn(Mono.just(application));

        var result = invoke("delete_link_application", Map.of("name", "application-a"));

        assertThat(result.error()).isFalse();
        assertThat(result.structuredContent()).containsEntry("deleted", true);
        verify(client).delete(application);
        verifyNoInteractions(approvalService, verificationService);
    }

    @Test
    void shouldReturnErrorForMissingApplication() {
        when(client.fetch(LinkApplication.class, "missing")).thenReturn(Mono.empty());

        for (String name : List.of("get_link_application", "verify_link_application",
            "reject_link_application", "delete_link_application")) {
            assertThat(invoke(name, Map.of("name", "missing")).error()).isTrue();
        }
        verify(client, never()).update(any());
        verify(client, never()).delete(any());
    }

    private McpToolDefinition tool(String name) {
        return provider.tools().filter(tool -> tool.name().equals(name)).blockFirst();
    }

    private McpToolResult invoke(String name, Map<String, Object> arguments) {
        var definition = tool(name);
        return McpSchemaAssertions.assertOutput(definition,
            definition.handler().execute(new McpToolInvocation(name, arguments)).block());
    }

    private static LinkApplication commentApplication() {
        var application = application(LinkApplication.Status.PENDING);
        application.getSpec().getOrigin().setType(LinkApplication.OriginType.COMMENT);
        var comment = new LinkApplication.CommentOrigin();
        comment.setName("comment-a");
        application.getSpec().getOrigin().setComment(comment);
        return application;
    }

    private static LinkApplication application(LinkApplication.Status status) {
        var application = new LinkApplication();
        application.setMetadata(metadata("application-a"));
        var spec = new LinkApplication.LinkApplicationSpec();
        spec.setUrl("https://example.com");
        spec.setDisplayName("Example");
        spec.setEmail("private@example.com");
        spec.setStatus(status);
        var origin = new LinkApplication.Origin();
        origin.setType(LinkApplication.OriginType.FORM);
        spec.setOrigin(origin);
        application.setSpec(spec);
        return application;
    }

    private static Metadata metadata(String name) {
        var metadata = new Metadata();
        metadata.setName(name);
        metadata.setAnnotations(Map.of("private", "internal-secret"));
        return metadata;
    }
}
