package run.halo.links.finders.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.links.rss.LinkFeedItemQuery;
import run.halo.links.service.LinkFeedPublicQueryService;
import run.halo.links.vo.LinkFeedItemPageVo;

class LinkFeedFinderImplTest {

    @Test
    void shouldReturnEmptyPageWhenPublicFeedQueriesAreDisabled() {
        LinkFeedPublicQueryService service = mock(LinkFeedPublicQueryService.class);
        when(service.isPublicEnabled()).thenReturn(Mono.just(false));
        LinkFeedFinderImpl finder = new LinkFeedFinderImpl(null, service, null);

        StepVerifier.create(finder.list(Map.of("limit", 10)))
            .assertNext(page -> {
                assertThat(page.getItems()).isEmpty();
                assertThat(page.isHasNext()).isFalse();
            })
            .verifyComplete();

        verify(service, never()).listFeeds(nullable(String.class), any(LinkFeedItemQuery.class));
    }

    @Test
    void shouldDelegateListWhenPublicFeedQueriesAreEnabled() {
        LinkFeedPublicQueryService service = mock(LinkFeedPublicQueryService.class);
        LinkFeedItemPageVo page = new LinkFeedItemPageVo(List.of(), null, null, false);
        when(service.isPublicEnabled()).thenReturn(Mono.just(true));
        when(service.listFeeds(isNull(), any(LinkFeedItemQuery.class))).thenReturn(Mono.just(page));
        LinkFeedFinderImpl finder = new LinkFeedFinderImpl(null, service, null);

        StepVerifier.create(finder.list(Map.of("limit", 10)))
            .expectNext(page)
            .verifyComplete();
    }

    @Test
    void shouldReturnEmptyGroupsWhenPublicFeedQueriesAreDisabled() {
        LinkFeedPublicQueryService service = mock(LinkFeedPublicQueryService.class);
        when(service.isPublicEnabled()).thenReturn(Mono.just(false));
        LinkFeedFinderImpl finder = new LinkFeedFinderImpl(null, service, null);

        StepVerifier.create(finder.groupBy(1))
            .verifyComplete();
    }
}
