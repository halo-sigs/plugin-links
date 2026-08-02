package run.halo.links.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.test.StepVerifier;
import run.halo.links.rss.LinkFeedItemQuery;
import run.halo.links.rss.LinkFeedItemStore;
import run.halo.links.rss.LinkFeedStorageUnavailableException;
import run.halo.links.vo.LinkFeedItemVo;

class LinkFeedPublicQueryServiceImplTest {

    @Test
    void shouldReturnEmptyPageWhenRssStorageIsUnavailable() {
        LinkFeedItemStore itemStore = mock(LinkFeedItemStore.class);
        LinkFeedItemQuery query = new LinkFeedItemQuery();
        when(itemStore.listRecent(any()))
            .thenThrow(new LinkFeedStorageUnavailableException("unavailable"));
        LinkFeedPublicQueryServiceImpl service =
            new LinkFeedPublicQueryServiceImpl(null, itemStore, null);

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
        LinkFeedPublicQueryServiceImpl service =
            new LinkFeedPublicQueryServiceImpl(null, itemStore, null);

        StepVerifier.create(service.listFeeds(null, new LinkFeedItemQuery()))
            .assertNext(page -> assertThat(page.getItems()).isEmpty())
            .verifyComplete();

        assertThat(threadName.get()).contains("boundedElastic");
    }

    @Test
    void shouldForceVisibleOnlyStoreQuery() {
        LinkFeedItemStore itemStore = mock(LinkFeedItemStore.class);
        when(itemStore.listRecent(any())).thenReturn(List.of());
        LinkFeedPublicQueryServiceImpl service =
            new LinkFeedPublicQueryServiceImpl(null, itemStore, null);
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
}
