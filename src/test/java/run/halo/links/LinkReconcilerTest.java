package run.halo.links;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import run.halo.app.extension.ExtensionClient;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.controller.Controller;
import run.halo.app.extension.controller.ControllerBuilder;
import run.halo.app.extension.controller.Reconciler;
import run.halo.links.extension.Link;
import run.halo.links.rss.LinkFeedItemStore;

@ExtendWith(MockitoExtension.class)
class LinkReconcilerTest {

    @Mock
    ExtensionClient client;

    @Mock
    LinkFeedItemStore itemStore;

    @Mock
    ControllerBuilder controllerBuilder;

    @Mock
    Controller controller;

    @Test
    void shouldIgnoreMissingLink() {
        LinkReconciler reconciler = new LinkReconciler(client, itemStore);
        when(client.fetch(Link.class, "missing")).thenReturn(Optional.empty());

        Reconciler.Result result = reconciler.reconcile(new Reconciler.Request("missing"));

        assertThat(result).isEqualTo(Reconciler.Result.doNotRetry());
        verifyNoInteractions(itemStore);
    }

    @Test
    void shouldAddFinalizerToLiveLink() {
        Link link = link("link-a");
        LinkReconciler reconciler = new LinkReconciler(client, itemStore);
        when(client.fetch(Link.class, "link-a")).thenReturn(Optional.of(link));

        Reconciler.Result result = reconciler.reconcile(new Reconciler.Request("link-a"));

        assertThat(result).isEqualTo(Reconciler.Result.doNotRetry());
        assertThat(link.getMetadata().getFinalizers()).containsExactly(LinkReconciler.FINALIZER);
        verify(client).update(link);
        verifyNoInteractions(itemStore);
    }

    @Test
    void shouldLeaveLiveLinkUnchangedWhenFinalizerAlreadyExists() {
        Link link = link("link-a");
        link.getMetadata().setFinalizers(Set.of(LinkReconciler.FINALIZER));
        LinkReconciler reconciler = new LinkReconciler(client, itemStore);
        when(client.fetch(Link.class, "link-a")).thenReturn(Optional.of(link));

        Reconciler.Result result = reconciler.reconcile(new Reconciler.Request("link-a"));

        assertThat(result).isEqualTo(Reconciler.Result.doNotRetry());
        verifyNoInteractions(itemStore);
        verify(client).fetch(Link.class, "link-a");
        verifyNoMoreInteractions(client);
    }

    @Test
    void shouldCleanupFeedItemsBeforeRemovingFinalizerFromDeletedLink() {
        Link link = deletedLink("link-a");
        LinkReconciler reconciler = new LinkReconciler(client, itemStore);
        when(client.fetch(Link.class, "link-a")).thenReturn(Optional.of(link));

        Reconciler.Result result = reconciler.reconcile(new Reconciler.Request("link-a"));

        assertThat(result).isEqualTo(Reconciler.Result.doNotRetry());
        assertThat(link.getMetadata().getFinalizers()).isEmpty();
        InOrder inOrder = inOrder(itemStore, client);
        inOrder.verify(itemStore).deleteByLinkName("link-a");
        inOrder.verify(client).update(link);
    }

    @Test
    void shouldFinalizeDeletedLinkWhenNoCachedItemsExist() {
        Link link = deletedLink("link-empty");
        LinkReconciler reconciler = new LinkReconciler(client, itemStore);
        when(client.fetch(Link.class, "link-empty")).thenReturn(Optional.of(link));

        Reconciler.Result result = reconciler.reconcile(new Reconciler.Request("link-empty"));

        assertThat(result).isEqualTo(Reconciler.Result.doNotRetry());
        verify(itemStore).deleteByLinkName("link-empty");
        verify(client).update(link);
    }

    @Test
    void setupWithShouldSyncExistingLinksOnStart() {
        LinkReconciler reconciler = new LinkReconciler(client, itemStore);
        when(controllerBuilder.extension(any(Link.class))).thenReturn(controllerBuilder);
        when(controllerBuilder.syncAllOnStart(anyBoolean())).thenReturn(controllerBuilder);
        when(controllerBuilder.build()).thenReturn(controller);

        Controller result = reconciler.setupWith(controllerBuilder);

        assertThat(result).isSameAs(controller);
        verify(controllerBuilder).syncAllOnStart(true);
    }

    private static Link link(String name) {
        Link link = new Link();
        Metadata metadata = new Metadata();
        metadata.setName(name);
        link.setMetadata(metadata);
        return link;
    }

    private static Link deletedLink(String name) {
        Link link = link(name);
        link.getMetadata().setDeletionTimestamp(Instant.now());
        link.getMetadata().setFinalizers(Set.of(LinkReconciler.FINALIZER));
        return link;
    }
}
