package run.halo.links;

import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import run.halo.app.core.extension.Plugin;
import run.halo.app.core.extension.content.Comment;
import run.halo.app.extension.ExtensionClient;
import run.halo.app.extension.ExtensionUtil;
import run.halo.app.extension.GroupVersionKind;
import run.halo.app.extension.Ref;
import run.halo.app.extension.controller.Controller;
import run.halo.app.extension.controller.ControllerBuilder;
import run.halo.app.extension.controller.Reconciler;

/**
 * @author LIlGG
 */
@Component
@RequiredArgsConstructor
public class CommentReconciler implements Reconciler<Reconciler.Request> {

    private static final String FINALIZER = "link.halo.run/finalizer";
    private final ExtensionClient client;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public Result reconcile(Request request) {
        client.fetch(Comment.class, request.name()).ifPresent(comment -> {
            if (comment.getMetadata().getDeletionTimestamp() != null) {
                if (ExtensionUtil.removeFinalizers(comment.getMetadata(), Set.of(FINALIZER))) {
                    client.update(comment);
                }
                return;
            }

            var forLink = Ref.groupKindEquals(comment.getSpec().getSubjectRef(),
                GroupVersionKind.fromExtension(Plugin.class));
            if (!forLink) {
                return;
            }

            if (ExtensionUtil.addFinalizers(comment.getMetadata(), Set.of(FINALIZER))) {
                eventPublisher.publishEvent(new LinkHasNewCommentEvent(this, comment));
                client.update(comment);
            }
        });
        return null;
    }

    @Override
    public Controller setupWith(ControllerBuilder builder) {
        return builder
            .extension(new Comment())
            .syncAllOnStart(false)
            .build();
    }
}
