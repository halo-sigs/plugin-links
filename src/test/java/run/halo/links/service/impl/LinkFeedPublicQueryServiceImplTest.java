package run.halo.links.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.links.rss.LinkFeedItemQuery;
import run.halo.links.rss.LinkFeedItemStore;
import run.halo.links.rss.LinkFeedStorageUnavailableException;
import run.halo.links.service.LinkPrivateAccessChecker;
import run.halo.links.service.LinkPublicQueryService;
import run.halo.links.vo.LinkFeedItemVo;

class LinkFeedPublicQueryServiceImplTest {

    private static LinkPrivateAccessChecker publicOnlyChecker() {
        var checker = mock(LinkPrivateAccessChecker.class);
        when(checker.canViewPrivateLinks()).thenReturn(Mono.just(false));
        return checker;
    }

    private static LinkPublicQueryService publicQueryServiceWithoutPrivateGroups() {
        var service = mock(LinkPublicQueryService.class);
        when(service.privateGroupNames()).thenReturn(Mono.just(Set.of()));
        return service;
    }

    private static LinkFeedPublicQueryServiceImpl serviceWith(LinkFeedItemStore itemStore) {
        return new LinkFeedPublicQueryServiceImpl(null, itemStore, null, publicOnlyChecker(),
            publicQueryServiceWithoutPrivateGroups());
    }

    @Test
    void shouldReturnEmptyPageWhenRssStorageIsUnavailable() {
        LinkFeedItemStore itemStore = mock(LinkFeedItemStore.class);
        LinkFeedItemQuery query = new LinkFeedItemQuery();
        when(itemStore.listRecent(any()))
            .thenThrow(new LinkFeedStorageUnavailableException("unavailable"));
        LinkFeedPublicQueryServiceImpl service = serviceWith(itemStore);

        StepVerifier.create(service.listFeeds(null, query))
            .assertNext(page -> {
                assertThat(page.getItems()).isEmpty();
                assertThat(page.isHasNext()).isFalse();
            })
            .verifyComplete();
    }

    @Test
    void shouldRunFeedItemLookupOnBoundedElasticThread() {
        LinkFeedItemStore itemStore = mock(LinkFeedItemStore.class);
        AtomicReference<String> threadName = new AtomicReference<>();
        when(itemStore.listRecent(any())).thenAnswer(invocation -> {
            threadName.set(Thread.currentThread().getName());
            return List.of();
        });
        LinkFeedPublicQueryServiceImpl service = serviceWith(itemStore);

        StepVerifier.create(service.listFeeds(null, new LinkFeedItemQuery()))
            .assertNext(page -> assertThat(page.getItems()).isEmpty())
            .verifyComplete();

        assertThat(threadName.get()).contains("boundedElastic");
    }

    @Test
    void shouldForceVisibleOnlyStoreQuery() {
        LinkFeedItemStore itemStore = mock(LinkFeedItemStore.class);
        when(itemStore.listRecent(any())).thenReturn(List.of());
        LinkFeedPublicQueryServiceImpl service = serviceWith(itemStore);
        LinkFeedItemQuery requested = new LinkFeedItemQuery();
        requested.setHidden(true);

        StepVerifier.create(service.listFeeds(null, requested))
            .assertNext(page -> assertThat(page.getItems()).isEmpty())
            .verifyComplete();

        ArgumentCaptor<LinkFeedItemQuery> queryCaptor =
            ArgumentCaptor.forClass(LinkFeedItemQuery.class);
        verify(itemStore).listRecent(queryCaptor.capture());
        assertThat(queryCaptor.getValue().getHidden()).isFalse();
    }

    @Test
    void shouldNotExposeHiddenStateOnPublicItemModel() {
        assertThat(LinkFeedItemVo.class.getDeclaredFields())
            .extracting(field -> field.getName())
            .doesNotContain("hidden");
    }

    @Test
    void shouldReturnEmptyPageWhenRequestedGroupIsPrivate() {
        LinkFeedItemStore itemStore = mock(LinkFeedItemStore.class);
        var publicQueryService = mock(LinkPublicQueryService.class);
        when(publicQueryService.privateGroupNames()).thenReturn(Mono.just(Set.of("private-group")));
        LinkFeedPublicQueryServiceImpl service =
            new LinkFeedPublicQueryServiceImpl(null, itemStore, null, publicOnlyChecker(),
                publicQueryService);

        StepVerifier.create(service.listFeeds("private-group", new LinkFeedItemQuery()))
            .assertNext(page -> {
                assertThat(page.getItems()).isEmpty();
                assertThat(page.isHasNext()).isFalse();
            })
            .verifyComplete();
    }

    @Test
    void shouldHideFeedItemsWhoseLinkBelongsToPrivateGroup() {
        LinkFeedItemStore itemStore = mock(LinkFeedItemStore.class);
        var feedItem = mock(run.halo.links.rss.LinkFeedItem.class);
        when(feedItem.getLinkName()).thenReturn("link-in-private-group");
        when(itemStore.listRecent(any())).thenReturn(List.of(feedItem));

        var client = mock(run.halo.app.extension.ReactiveExtensionClient.class);
        var link = new run.halo.links.extension.Link();
        var metadata = new run.halo.app.extension.Metadata();
        metadata.setName("link-in-private-group");
        link.setMetadata(metadata);
        var spec = new run.halo.links.extension.Link.LinkSpec();
        spec.setGroupName("private-group");
        link.setSpec(spec);
        when(client.listAll(org.mockito.ArgumentMatchers.eq(run.halo.links.extension.Link.class),
            any(), any())).thenReturn(reactor.core.publisher.Flux.just(link));

        var publicQueryService = mock(LinkPublicQueryService.class);
        when(publicQueryService.privateGroupNames()).thenReturn(Mono.just(Set.of("private-group")));
        LinkFeedPublicQueryServiceImpl service =
            new LinkFeedPublicQueryServiceImpl(client, itemStore, null, publicOnlyChecker(),
                publicQueryService);

        StepVerifier.create(service.listFeeds(null, new LinkFeedItemQuery()))
            .assertNext(page -> assertThat(page.getItems()).isEmpty())
            .verifyComplete();
    }
}
