package run.halo.links.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.test.web.reactive.server.WebTestClient;
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

@ExtendWith(MockitoExtension.class)
class LinkApplicationEndpointTest {

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

    WebTestClient webClient;

    @BeforeEach
    void setUp() {
        var endpoint = new LinkApplicationEndpoint(client, approvalService, verificationService,
            extensionGetter);
        webClient = WebTestClient.bindToRouterFunction(endpoint.endpoint()).build();
    }

    @Test
    void shouldUseRealPageRequestAndReturnServerTotal() {
        when(client.listBy(eq(LinkApplication.class), any(), any()))
            .thenReturn(Mono.just(new ListResult<>(2, 20, 45, List.of())));

        webClient.get()
            .uri("/linkapplications?page=2&size=20&status=pending&originType=form")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.page").isEqualTo(2)
            .jsonPath("$.size").isEqualTo(20)
            .jsonPath("$.total").isEqualTo(45);

        var pageRequest = ArgumentCaptor.forClass(PageRequest.class);
        var listOptions = ArgumentCaptor.forClass(ListOptions.class);
        org.mockito.Mockito.verify(client).listBy(eq(LinkApplication.class),
            listOptions.capture(), pageRequest.capture());
        assertThat(pageRequest.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageRequest.getValue().getPageSize()).isEqualTo(20);
        assertThat(pageRequest.getValue().getSort().getOrderFor("metadata.creationTimestamp"))
            .isNotNull();
        assertThat(listOptions.getValue().getFieldSelector()).isNotNull();
    }

    @Test
    void shouldReturnResolvedOriginCommentSubject() {
        var application = application("application-a", LinkApplication.Status.PENDING);
        var origin = new LinkApplication.Origin();
        origin.setType(LinkApplication.OriginType.COMMENT);
        var commentOrigin = new LinkApplication.CommentOrigin();
        commentOrigin.setName("comment-a");
        origin.setComment(commentOrigin);
        application.getSpec().setOrigin(origin);
        var comment = new Comment();
        comment.setMetadata(metadata("comment-a"));
        var commentSpec = new Comment.CommentSpec();
        commentSpec.setRaw("raw comment");
        var subjectRef = new Ref();
        subjectRef.setGroup("content.halo.run");
        subjectRef.setKind("Post");
        subjectRef.setName("post-a");
        commentSpec.setSubjectRef(subjectRef);
        commentSpec.setCreationTime(Instant.parse("2026-07-25T00:00:00Z"));
        comment.setSpec(commentSpec);
        when(client.fetch(LinkApplication.class, "application-a"))
            .thenReturn(Mono.just(application));
        when(client.fetch(Comment.class, "comment-a")).thenReturn(Mono.just(comment));
        when(extensionGetter.getExtensions(CommentSubject.class))
            .thenReturn(Flux.just(commentSubject));
        when(commentSubject.supports(subjectRef)).thenReturn(true);
        when(commentSubject.getSubjectDisplay("post-a"))
            .thenReturn(Mono.just(new CommentSubject.SubjectDisplay(
                "Public post", "https://example.com/posts/public-post", "文章")));

        webClient.get()
            .uri("/linkapplications/application-a/origin-comment")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.name").isEqualTo("comment-a")
            .jsonPath("$.raw").isEqualTo("raw comment")
            .jsonPath("$.creationTime").isEqualTo("2026-07-25T00:00:00Z")
            .jsonPath("$.subject.title").isEqualTo("Public post")
            .jsonPath("$.subject.url").isEqualTo("https://example.com/posts/public-post")
            .jsonPath("$.subject.kindName").isEqualTo("文章")
            .jsonPath("$.content").doesNotExist()
            .jsonPath("$.owner").doesNotExist();

        verify(client).fetch(Comment.class, "comment-a");
    }

    @Test
    void shouldReturnOriginCommentWhenSubjectDisplayIsUnavailable() {
        var application = application("application-a", LinkApplication.Status.PENDING);
        var origin = new LinkApplication.Origin();
        origin.setType(LinkApplication.OriginType.COMMENT);
        var commentOrigin = new LinkApplication.CommentOrigin();
        commentOrigin.setName("comment-a");
        origin.setComment(commentOrigin);
        application.getSpec().setOrigin(origin);
        var comment = new Comment();
        comment.setMetadata(metadata("comment-a"));
        var commentSpec = new Comment.CommentSpec();
        commentSpec.setRaw("raw comment");
        var subjectRef = new Ref();
        subjectRef.setGroup("content.halo.run");
        subjectRef.setKind("Post");
        subjectRef.setName("post-a");
        commentSpec.setSubjectRef(subjectRef);
        commentSpec.setCreationTime(Instant.parse("2026-07-25T00:00:00Z"));
        comment.setSpec(commentSpec);
        when(client.fetch(LinkApplication.class, "application-a"))
            .thenReturn(Mono.just(application));
        when(client.fetch(Comment.class, "comment-a")).thenReturn(Mono.just(comment));
        when(extensionGetter.getExtensions(CommentSubject.class))
            .thenReturn(Flux.just(commentSubject));
        when(commentSubject.supports(subjectRef)).thenReturn(true);
        when(commentSubject.getSubjectDisplay("post-a")).thenReturn(Mono.empty());

        webClient.get()
            .uri("/linkapplications/application-a/origin-comment")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.name").isEqualTo("comment-a")
            .jsonPath("$.raw").isEqualTo("raw comment")
            .jsonPath("$.creationTime").isEqualTo("2026-07-25T00:00:00Z")
            .jsonPath("$.subject").doesNotExist()
            .jsonPath("$.content").doesNotExist()
            .jsonPath("$.owner").doesNotExist();
    }

    @Test
    void shouldReturnNotFoundWhenSourceCommentWasDeleted() {
        var application = application("application-a", LinkApplication.Status.PENDING);
        var origin = new LinkApplication.Origin();
        origin.setType(LinkApplication.OriginType.COMMENT);
        var commentOrigin = new LinkApplication.CommentOrigin();
        commentOrigin.setName("comment-a");
        origin.setComment(commentOrigin);
        application.getSpec().setOrigin(origin);
        when(client.fetch(LinkApplication.class, "application-a"))
            .thenReturn(Mono.just(application));
        when(client.fetch(Comment.class, "comment-a")).thenReturn(Mono.empty());

        webClient.get()
            .uri("/linkapplications/application-a/origin-comment")
            .exchange()
            .expectStatus().isNotFound();
    }

    @Test
    void shouldSkipApprovingAndReportPartialCleanupFailure() {
        var pending = application("pending", LinkApplication.Status.PENDING);
        var approving = application("approving", LinkApplication.Status.APPROVING);
        var approved = application("approved", LinkApplication.Status.APPROVED);
        when(client.listAll(eq(LinkApplication.class), any(ListOptions.class), any(Sort.class)))
            .thenReturn(Flux.just(pending, approving, approved));
        when(client.delete(pending)).thenReturn(Mono.just(pending));
        when(client.delete(approved)).thenReturn(Mono.error(new IllegalStateException("failed")));

        webClient.post()
            .uri("/linkapplications/-/cleanup?originType=form")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.matched").isEqualTo(3)
            .jsonPath("$.deleted").isEqualTo(1)
            .jsonPath("$.failed").isEqualTo(1)
            .jsonPath("$.skipped").isEqualTo(1);
    }

    @Test
    void shouldRejectIndividualDeleteWhileApproving() {
        var application = application("application-a", LinkApplication.Status.APPROVING);
        when(client.fetch(LinkApplication.class, "application-a"))
            .thenReturn(Mono.just(application));

        webClient.delete()
            .uri("/linkapplications/application-a")
            .exchange()
            .expectStatus().isEqualTo(409);
    }

    @Test
    void shouldVerifyBacklinkOverrideFromReviewForm() {
        var application = application("application-a", LinkApplication.Status.PENDING);
        application.getSpec().setBacklink("https://original.example/links");
        var status = new Link.BacklinkStatus();
        status.setState(Link.BacklinkState.FOUND);
        status.setMatchedUrl("https://edited.example/");
        when(client.fetch(LinkApplication.class, "application-a"))
            .thenReturn(Mono.just(application));
        when(verificationService.verifyBacklink("https://edited.example/links"))
            .thenReturn(Mono.just(status));

        webClient.post()
            .uri("/linkapplications/application-a/verify")
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
            .bodyValue("{\"backlink\":\"https://edited.example/links\"}")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.found").isEqualTo(true);
    }

    private static LinkApplication application(String name, LinkApplication.Status status) {
        var application = new LinkApplication();
        application.setMetadata(metadata(name));
        var spec = new LinkApplication.LinkApplicationSpec();
        spec.setUrl("https://" + name + ".example");
        spec.setDisplayName(name);
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
        return metadata;
    }
}
