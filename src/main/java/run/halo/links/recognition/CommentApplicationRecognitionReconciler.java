package run.halo.links.recognition;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;
import run.halo.app.core.extension.content.Comment;
import run.halo.app.extension.ExtensionClient;
import run.halo.app.extension.controller.Controller;
import run.halo.app.extension.controller.ControllerBuilder;
import run.halo.app.extension.controller.Reconciler;
import run.halo.links.endpoint.AiFoundationAvailableCondition;

/**
 * Add-event-only controller for automatic comment application recognition.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Conditional(AiFoundationAvailableCondition.class)
public class CommentApplicationRecognitionReconciler
    implements Reconciler<Reconciler.Request> {

    private static final Duration PROCESSING_TIMEOUT = Duration.ofSeconds(35);

    private final ExtensionClient client;
    private final CommentApplicationRecognitionProcessor processor;

    @Override
    public Result reconcile(Request request) {
        try {
            client.fetch(Comment.class, request.name())
                .ifPresent(comment -> processor.process(comment).block(PROCESSING_TIMEOUT));
        } catch (Exception error) {
            log.warn(
                "[plugin-links] Comment application recognition failed: comment={}, error={}",
                request.name(),
                error.toString()
            );
        }
        return Result.doNotRetry();
    }

    @Override
    public Controller setupWith(ControllerBuilder builder) {
        return builder
            .extension(new Comment())
            .syncAllOnStart(false)
            .workerCount(1)
            .onUpdateMatcher(extension -> false)
            .onDeleteMatcher(extension -> false)
            .build();
    }
}
