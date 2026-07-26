package run.halo.links.recognition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import run.halo.app.core.extension.content.Comment;
import run.halo.app.extension.ExtensionClient;
import run.halo.app.extension.controller.Controller;
import run.halo.app.extension.controller.ControllerBuilder;
import run.halo.app.extension.controller.Reconciler;

@ExtendWith(MockitoExtension.class)
class CommentApplicationRecognitionReconcilerTest {

    @Mock
    ExtensionClient client;

    @Mock
    CommentApplicationRecognitionProcessor processor;

    @Mock
    ControllerBuilder controllerBuilder;

    @Mock
    Controller controller;

    @Test
    void shouldProcessNewCommentWithoutRetryingFailures() {
        var comment = new Comment();
        when(client.fetch(Comment.class, "comment-a")).thenReturn(Optional.of(comment));
        when(processor.process(comment)).thenReturn(reactor.core.publisher.Mono.error(
            new IllegalStateException("AI unavailable")));
        var reconciler = new CommentApplicationRecognitionReconciler(client, processor);

        assertThat(reconciler.reconcile(new Reconciler.Request("comment-a")))
            .isEqualTo(Reconciler.Result.doNotRetry());
        verify(processor).process(comment);
    }

    @Test
    void shouldConfigureAddOnlySingleWorkerWithoutStartupSync() {
        when(controllerBuilder.extension(any(Comment.class))).thenReturn(controllerBuilder);
        when(controllerBuilder.syncAllOnStart(anyBoolean())).thenReturn(controllerBuilder);
        when(controllerBuilder.workerCount(anyInt())).thenReturn(controllerBuilder);
        when(controllerBuilder.onUpdateMatcher(any())).thenReturn(controllerBuilder);
        when(controllerBuilder.onDeleteMatcher(any())).thenReturn(controllerBuilder);
        when(controllerBuilder.build()).thenReturn(controller);
        var reconciler = new CommentApplicationRecognitionReconciler(client, processor);

        assertThat(reconciler.setupWith(controllerBuilder)).isSameAs(controller);
        verify(controllerBuilder).syncAllOnStart(false);
        verify(controllerBuilder).workerCount(1);
        verify(controllerBuilder).onUpdateMatcher(any());
        verify(controllerBuilder).onDeleteMatcher(any());
    }
}
