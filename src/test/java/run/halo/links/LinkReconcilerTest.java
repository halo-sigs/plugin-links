package run.halo.links;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
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
import run.halo.links.rss.LinkFeedStorageUnavailableException;

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
    void shouldCleanupDisabledSubscriptionAndPreserveFeedUrls() {
        Link link = disabledLink("link-disabled");
        link.getMetadata().setFinalizers(Set.of(LinkReconciler.FINALIZER));
        Link.RssStatus rssStatus = new Link.RssStatus();
        rssStatus.setItemCount(3L);
        link.getStatus().setRss(rssStatus);
        LinkReconciler reconciler = new LinkReconciler(client, itemStore);
        when(client.fetch(Link.class, "link-disabled")).thenReturn(Optional.of(link));

        Reconciler.Result result = reconciler.reconcile(
            new Reconciler.Request("link-disabled"));

        assertThat(result).isEqualTo(Reconciler.Result.doNotRetry());
        assertThat(link.getSpec().getRss().getFeedUrls())
            .containsExactly("https://example.com/feed.xml");
        assertThat(link.getStatus().getRss()).isNull();
        assertThat(link.getMetadata().getFinalizers()).containsExactly(LinkReconciler.FINALIZER);
        InOrder inOrder = inOrder(itemStore, client);
        inOrder.verify(itemStore).deleteByLinkName("link-disabled");
        inOrder.verify(client).update(link);
    }

    @Test
    void shouldAddFinalizerBeforeCleaningDisabledSubscription() {
        Link link = disabledLink("link-unprotected");
        Link.RssStatus rssStatus = new Link.RssStatus();
        link.getStatus().setRss(rssStatus);
        LinkReconciler reconciler = new LinkReconciler(client, itemStore);
        when(client.fetch(Link.class, "link-unprotected")).thenReturn(Optional.of(link));

        Reconciler.Result result = reconciler.reconcile(
            new Reconciler.Request("link-unprotected"));

        assertThat(result).isEqualTo(Reconciler.Result.doNotRetry());
        assertThat(link.getMetadata().getFinalizers()).containsExactly(LinkReconciler.FINALIZER);
        assertThat(link.getStatus().getRss()).isSameAs(rssStatus);
        verify(client).update(link);
        verifyNoInteractions(itemStore);
    }

    @Test
    void shouldCleanupLinkWithoutRssConfiguration() {
        Link link = link("link-without-rss");
        link.getSpec().setRss(null);
        link.getMetadata().setFinalizers(Set.of(LinkReconciler.FINALIZER));
        link.getStatus().setRss(new Link.RssStatus());
        LinkReconciler reconciler = new LinkReconciler(client, itemStore);
        when(client.fetch(Link.class, "link-without-rss")).thenReturn(Optional.of(link));

        Reconciler.Result result = reconciler.reconcile(
            new Reconciler.Request("link-without-rss"));

        assertThat(result).isEqualTo(Reconciler.Result.doNotRetry());
        assertThat(link.getStatus().getRss()).isNull();
        InOrder inOrder = inOrder(itemStore, client);
        inOrder.verify(itemStore).deleteByLinkName("link-without-rss");
        inOrder.verify(client).update(link);
    }

    @Test
    void shouldIdempotentlyRepairDisabledLinkOnStartup() {
        Link link = disabledLink("link-repaired");
        link.getMetadata().setFinalizers(Set.of(LinkReconciler.FINALIZER));
        LinkReconciler reconciler = new LinkReconciler(client, itemStore);
        when(client.fetch(Link.class, "link-repaired")).thenReturn(Optional.of(link));

        Reconciler.Result result = reconciler.reconcile(
            new Reconciler.Request("link-repaired"));

        assertThat(result).isEqualTo(Reconciler.Result.doNotRetry());
        verify(itemStore).deleteByLinkName("link-repaired");
        verify(client).fetch(Link.class, "link-repaired");
        verifyNoMoreInteractions(client);
    }

    @Test
    void shouldKeepRssStatusWhenDisabledLinkCleanupFails() {
        Link link = disabledLink("link-pending-cleanup");
        link.getMetadata().setFinalizers(Set.of(LinkReconciler.FINALIZER));
        Link.RssStatus rssStatus = new Link.RssStatus();
        rssStatus.setItemCount(3L);
        link.getStatus().setRss(rssStatus);
        LinkReconciler reconciler = new LinkReconciler(client, itemStore);
        when(client.fetch(Link.class, "link-pending-cleanup")).thenReturn(Optional.of(link));
        doThrow(new LinkFeedStorageUnavailableException("unavailable"))
            .when(itemStore).deleteByLinkName("link-pending-cleanup");

        assertThatThrownBy(() -> reconciler.reconcile(
            new Reconciler.Request("link-pending-cleanup")))
            .isInstanceOf(LinkFeedStorageUnavailableException.class);
        assertThat(link.getStatus().getRss()).isSameAs(rssStatus);
        assertThat(link.getSpec().getRss().getFeedUrls())
            .containsExactly("https://example.com/feed.xml");
        verify(client).fetch(Link.class, "link-pending-cleanup");
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
    void shouldKeepFinalizerWhenFeedStorageIsUnavailable() {
        Link link = deletedLink("link-pending");
        LinkReconciler reconciler = new LinkReconciler(client, itemStore);
        when(client.fetch(Link.class, "link-pending")).thenReturn(Optional.of(link));
        doThrow(new LinkFeedStorageUnavailableException("unavailable"))
            .when(itemStore).deleteByLinkName("link-pending");

        assertThatThrownBy(() -> reconciler.reconcile(
            new Reconciler.Request("link-pending")))
            .isInstanceOf(LinkFeedStorageUnavailableException.class);
        assertThat(link.getMetadata().getFinalizers()).containsExactly(LinkReconciler.FINALIZER);
        verify(client).fetch(Link.class, "link-pending");
        verifyNoMoreInteractions(client);
    }

    @Test
    void shouldFinalizeDeletedLinkAfterStorageRecoversOnRestart() {
        Link link = deletedLink("link-recovered");
        when(client.fetch(Link.class, "link-recovered")).thenReturn(Optional.of(link));
        doThrow(new LinkFeedStorageUnavailableException("unavailable"))
            .doNothing()
            .when(itemStore).deleteByLinkName("link-recovered");

        LinkReconciler beforeRestart = new LinkReconciler(client, itemStore);
        assertThatThrownBy(() -> beforeRestart.reconcile(
            new Reconciler.Request("link-recovered")))
            .isInstanceOf(LinkFeedStorageUnavailableException.class);
        assertThat(link.getMetadata().getFinalizers()).containsExactly(LinkReconciler.FINALIZER);

        LinkReconciler afterRestart = new LinkReconciler(client, itemStore);
        assertThat(afterRestart.reconcile(new Reconciler.Request("link-recovered")))
            .isEqualTo(Reconciler.Result.doNotRetry());
        assertThat(link.getMetadata().getFinalizers()).isEmpty();
        verify(itemStore, times(2)).deleteByLinkName("link-recovered");
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
        Link.LinkSpec spec = new Link.LinkSpec();
        Link.RssSpec rss = new Link.RssSpec();
        rss.setEnabled(true);
        rss.setFeedUrls(List.of("https://example.com/feed.xml"));
        spec.setRss(rss);
        link.setSpec(spec);
        return link;
    }

    private static Link disabledLink(String name) {
        Link link = link(name);
        link.getSpec().getRss().setEnabled(false);
        return link;
    }

    private static Link deletedLink(String name) {
        Link link = link(name);
        link.getMetadata().setDeletionTimestamp(Instant.now());
        link.getMetadata().setFinalizers(Set.of(LinkReconciler.FINALIZER));
        return link;
    }
}
