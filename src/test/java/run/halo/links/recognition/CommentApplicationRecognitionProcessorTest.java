package run.halo.links.recognition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.app.core.extension.Plugin;
import run.halo.app.core.extension.content.Comment;
import run.halo.app.core.extension.content.Post;
import run.halo.app.core.extension.content.SinglePage;
import run.halo.app.extension.GroupVersionKind;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.Ref;
import run.halo.app.plugin.PluginContext;
import run.halo.app.plugin.ReactiveSettingFetcher;
import run.halo.links.dto.LinkApplicationSettings;
import run.halo.links.dto.LinkCommentRecognitionRequest;
import run.halo.links.dto.LinkCommentRecognitionResult;
import run.halo.links.endpoint.LinkApplicationSettingsFetcher;
import run.halo.links.extension.LinkApplication;
import run.halo.links.route.LinkBaseSettings;
import run.halo.links.service.LinkApplicationService;
import run.halo.links.service.ai.LinkAiService;

@ExtendWith(MockitoExtension.class)
class CommentApplicationRecognitionProcessorTest {

    @Mock
    LinkApplicationSettingsFetcher applicationSettingsFetcher;

    @Mock
    LinkAiService aiService;

    @Mock
    LinkApplicationService applicationService;

    @Mock
    ReactiveExtensionClient extensionClient;

    @Mock
    ReactiveSettingFetcher settingFetcher;

    @Mock
    PluginContext pluginContext;

    CommentApplicationRecognitionProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new CommentApplicationRecognitionProcessor(
            applicationSettingsFetcher,
            aiService,
            applicationService,
            extensionClient,
            settingFetcher,
            pluginContext
        );
    }

    @Test
    void shouldCreateCommentOriginApplicationForMatchingPost() {
        var settings = settings(source(LinkApplicationSettings.SourceType.POST, "post-a"));
        when(applicationSettingsFetcher.fetch()).thenReturn(Mono.just(settings));
        when(aiService.isOperational("model-a")).thenReturn(Mono.just(true));
        var post = new Post();
        var postSpec = new Post.PostSpec();
        postSpec.setTitle("Post A");
        post.setSpec(postSpec);
        when(extensionClient.fetch(Post.class, "post-a")).thenReturn(Mono.just(post));
        when(aiService.recognize(any(), org.mockito.ArgumentMatchers.eq("model-a")))
            .thenReturn(Mono.just(new LinkCommentRecognitionResult(
                true,
                "https://example.com",
                "Example",
                "https://example.com/logo.png",
                "A blog",
                "https://example.com/links",
                List.of("https://example.com/feed.xml")
            )));
        when(applicationService.create(any())).thenReturn(Mono.just(
            new LinkApplicationService.CreateResult(
                LinkApplicationService.CreateStatus.CREATED,
                null, null, null, null
            )
        ));
        var comment = comment("comment-a", Ref.of("post-a",
            GroupVersionKind.fromExtension(Post.class)));

        StepVerifier.create(processor.process(comment))
            .expectNext(CommentApplicationRecognitionProcessor.ProcessOutcome.CREATED)
            .verifyComplete();

        var recognitionRequest = ArgumentCaptor.forClass(LinkCommentRecognitionRequest.class);
        verify(aiService).recognize(recognitionRequest.capture(),
            org.mockito.ArgumentMatchers.eq("model-a"));
        assertThat(recognitionRequest.getValue()).isEqualTo(
            new LinkCommentRecognitionRequest(
                "raw application",
                "POST",
                "Post A",
                "Alice",
                "https://owner.example"
            )
        );

        var submission = ArgumentCaptor.forClass(LinkApplicationService.Submission.class);
        verify(applicationService).create(submission.capture());
        var value = submission.getValue();
        assertThat(value.email()).isEqualTo("alice@example.com");
        assertThat(value.origin().getType()).isEqualTo(LinkApplication.OriginType.COMMENT);
        assertThat(value.origin().getComment().getName()).isEqualTo("comment-a");
    }

    @Test
    void shouldMatchLinksAndSinglePageSources() {
        when(pluginContext.getName()).thenReturn("PluginLinks");

        var linksSettings = settings(source(LinkApplicationSettings.SourceType.LINKS, null));
        when(applicationSettingsFetcher.fetch()).thenReturn(Mono.just(linksSettings));
        when(aiService.isOperational("model-a")).thenReturn(Mono.just(true));
        var baseSettings = new LinkBaseSettings();
        baseSettings.setTitle("Friends");
        when(settingFetcher.fetch("base", LinkBaseSettings.class))
            .thenReturn(Mono.just(baseSettings));
        when(aiService.recognize(any(), any())).thenReturn(Mono.just(negative()));

        var linksComment = comment("comment-links", Ref.of("PluginLinks",
            GroupVersionKind.fromExtension(Plugin.class)));
        StepVerifier.create(processor.process(linksComment))
            .expectNext(CommentApplicationRecognitionProcessor.ProcessOutcome.NEGATIVE)
            .verifyComplete();

        var pageSettings = settings(source(LinkApplicationSettings.SourceType.SINGLE_PAGE, "page-a"));
        when(applicationSettingsFetcher.fetch()).thenReturn(Mono.just(pageSettings));
        var page = new SinglePage();
        var pageSpec = new SinglePage.SinglePageSpec();
        pageSpec.setTitle("About");
        page.setSpec(pageSpec);
        when(extensionClient.fetch(SinglePage.class, "page-a")).thenReturn(Mono.just(page));

        var pageComment = comment("comment-page", Ref.of("page-a",
            GroupVersionKind.fromExtension(SinglePage.class)));
        StepVerifier.create(processor.process(pageComment))
            .expectNext(CommentApplicationRecognitionProcessor.ProcessOutcome.NEGATIVE)
            .verifyComplete();
    }

    @Test
    void shouldSkipUnmatchedOrUnavailableCommentsWithoutCallingModel() {
        when(applicationSettingsFetcher.fetch()).thenReturn(Mono.just(
            settings(source(LinkApplicationSettings.SourceType.POST, "post-a"))));
        var unmatched = comment("comment-a", Ref.of("post-b",
            GroupVersionKind.fromExtension(Post.class)));

        StepVerifier.create(processor.process(unmatched))
            .expectNext(CommentApplicationRecognitionProcessor.ProcessOutcome.SKIPPED)
            .verifyComplete();
        verify(aiService, never()).recognize(any(), any());
    }

    @Test
    void shouldSkipWhenApplicationMasterIsDisabled() {
        when(applicationSettingsFetcher.fetch())
            .thenReturn(Mono.just(LinkApplicationSettings.defaults()));
        var comment = comment("comment-a", Ref.of("post-a",
            GroupVersionKind.fromExtension(Post.class)));

        StepVerifier.create(processor.process(comment))
            .expectNext(CommentApplicationRecognitionProcessor.ProcessOutcome.SKIPPED)
            .verifyComplete();

        verify(aiService, never()).isOperational(any());
        verify(aiService, never()).recognize(any(), any());
        verify(applicationService, never()).create(any());
    }

    @Test
    void shouldUseOwnerWebsiteAndDisplayNameFallbacksAfterPositiveDecision() {
        when(applicationSettingsFetcher.fetch()).thenReturn(Mono.just(
            settings(source(LinkApplicationSettings.SourceType.POST, "post-a"))));
        when(aiService.isOperational("model-a")).thenReturn(Mono.just(true));
        var post = new Post();
        var postSpec = new Post.PostSpec();
        postSpec.setTitle("Post A");
        post.setSpec(postSpec);
        when(extensionClient.fetch(Post.class, "post-a")).thenReturn(Mono.just(post));
        when(aiService.recognize(any(), any())).thenReturn(Mono.just(
            new LinkCommentRecognitionResult(true, null, null, null, null,
                null, List.of())
        ));
        when(applicationService.create(any())).thenReturn(Mono.just(
            new LinkApplicationService.CreateResult(
                LinkApplicationService.CreateStatus.CREATED,
                null, null, null, null
            )
        ));

        StepVerifier.create(processor.process(comment("comment-a", Ref.of("post-a",
                GroupVersionKind.fromExtension(Post.class)))))
            .expectNext(CommentApplicationRecognitionProcessor.ProcessOutcome.CREATED)
            .verifyComplete();

        var submission = ArgumentCaptor.forClass(LinkApplicationService.Submission.class);
        verify(applicationService).create(submission.capture());
        assertThat(submission.getValue().url()).isEqualTo("https://owner.example");
        assertThat(submission.getValue().displayName()).isEqualTo("Alice");
    }

    @Test
    void shouldNotCreateApplicationWithoutUsableUrl() {
        when(applicationSettingsFetcher.fetch()).thenReturn(Mono.just(
            settings(source(LinkApplicationSettings.SourceType.POST, "post-a"))));
        when(aiService.isOperational("model-a")).thenReturn(Mono.just(true));
        var post = new Post();
        post.setSpec(new Post.PostSpec());
        when(extensionClient.fetch(Post.class, "post-a")).thenReturn(Mono.just(post));
        when(aiService.recognize(any(), any())).thenReturn(Mono.just(
            new LinkCommentRecognitionResult(true, "not-a-url", null, null,
                null, null, List.of())
        ));
        var comment = comment("comment-a", Ref.of("post-a",
            GroupVersionKind.fromExtension(Post.class)));
        comment.getSpec().getOwner().setAnnotations(Map.of());

        StepVerifier.create(processor.process(comment))
            .expectNext(CommentApplicationRecognitionProcessor.ProcessOutcome.INVALID)
            .verifyComplete();
        verify(applicationService, never()).create(any());
    }

    @Test
    void shouldUseUrlHostAndNotCopyEmailForHaloUserOwner() {
        when(applicationSettingsFetcher.fetch()).thenReturn(Mono.just(
            settings(source(LinkApplicationSettings.SourceType.POST, "post-a"))));
        when(aiService.isOperational("model-a")).thenReturn(Mono.just(true));
        var post = new Post();
        post.setSpec(new Post.PostSpec());
        when(extensionClient.fetch(Post.class, "post-a")).thenReturn(Mono.just(post));
        when(aiService.recognize(any(), any())).thenReturn(Mono.just(
            new LinkCommentRecognitionResult(true,
                "https://Blog.Example/path", null, null, null, null, List.of())
        ));
        when(applicationService.create(any())).thenReturn(Mono.just(
            new LinkApplicationService.CreateResult(
                LinkApplicationService.CreateStatus.CREATED,
                null, null, null, null
            )
        ));
        var comment = comment("comment-a", Ref.of("post-a",
            GroupVersionKind.fromExtension(Post.class)));
        comment.getSpec().getOwner().setKind("User");
        comment.getSpec().getOwner().setName("alice");
        comment.getSpec().getOwner().setDisplayName(" ");
        comment.getSpec().getOwner().setAnnotations(Map.of());

        StepVerifier.create(processor.process(comment))
            .expectNext(CommentApplicationRecognitionProcessor.ProcessOutcome.CREATED)
            .verifyComplete();

        var submission = ArgumentCaptor.forClass(LinkApplicationService.Submission.class);
        verify(applicationService).create(submission.capture());
        assertThat(submission.getValue().displayName()).isEqualTo("blog.example");
        assertThat(submission.getValue().email()).isNull();
    }

    private static LinkApplicationSettings settings(
        LinkApplicationSettings.RecognitionSource source) {
        var settings = new LinkApplicationSettings();
        settings.setEnabled(true);
        var recognition = new LinkApplicationSettings.CommentRecognition();
        recognition.setEnabled(true);
        recognition.setModelName("model-a");
        recognition.setSources(List.of(source));
        settings.setCommentRecognition(recognition);
        return settings.normalized();
    }

    private static LinkApplicationSettings.RecognitionSource source(
        LinkApplicationSettings.SourceType type,
        String name) {
        var source = new LinkApplicationSettings.RecognitionSource();
        source.setType(type);
        source.setName(name);
        return source;
    }

    private static LinkCommentRecognitionResult negative() {
        return new LinkCommentRecognitionResult(false, null, null, null,
            null, null, List.of());
    }

    private static Comment comment(String name, Ref subjectRef) {
        var comment = new Comment();
        var metadata = new Metadata();
        metadata.setName(name);
        comment.setMetadata(metadata);
        var spec = new Comment.CommentSpec();
        spec.setRaw("raw application");
        spec.setContent("<p>raw application</p>");
        spec.setSubjectRef(subjectRef);
        spec.setApproved(false);
        spec.setHidden(true);
        var owner = new Comment.CommentOwner();
        owner.setKind(Comment.CommentOwner.KIND_EMAIL);
        owner.setName("alice@example.com");
        owner.setDisplayName("Alice");
        owner.setAnnotations(Map.of(
            Comment.CommentOwner.WEBSITE_ANNO, "https://owner.example"
        ));
        spec.setOwner(owner);
        comment.setSpec(spec);
        return comment;
    }
}
