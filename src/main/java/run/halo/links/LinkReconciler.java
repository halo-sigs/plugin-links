package run.halo.links;

import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import run.halo.app.extension.ExtensionClient;
import run.halo.app.extension.ExtensionUtil;
import run.halo.app.extension.controller.Controller;
import run.halo.app.extension.controller.ControllerBuilder;
import run.halo.app.extension.controller.Reconciler;
import run.halo.links.extension.Link;
import run.halo.links.rss.LinkFeedItemStore;

@Component
@RequiredArgsConstructor
public class LinkReconciler implements Reconciler<Reconciler.Request> {

    static final String FINALIZER = "link.halo.run/rss-cache-cleanup";

    private final ExtensionClient client;

    private final LinkFeedItemStore itemStore;

    @Override
    public Result reconcile(Request request) {
        client.fetch(Link.class, request.name()).ifPresent(link -> {
            var metadata = link.getMetadata();
            if (metadata == null) {
                return;
            }
            if (ExtensionUtil.isDeleted(link)) {
                itemStore.deleteByLinkName(request.name());
                if (ExtensionUtil.removeFinalizers(metadata, Set.of(FINALIZER))) {
                    client.update(link);
                }
                return;
            }
            if (ExtensionUtil.addFinalizers(metadata, Set.of(FINALIZER))) {
                client.update(link);
            }
        });
        return Result.doNotRetry();
    }

    @Override
    public Controller setupWith(ControllerBuilder builder) {
        return builder
            .extension(new Link())
            .syncAllOnStart(true)
            .build();
    }
}
