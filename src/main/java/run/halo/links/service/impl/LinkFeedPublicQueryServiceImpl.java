package run.halo.links.service.impl;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.router.selector.FieldSelector;
import run.halo.links.extension.Link;
import run.halo.links.rss.LinkFeedItem;
import run.halo.links.rss.LinkFeedItemQuery;
import run.halo.links.rss.LinkFeedItemStore;
import run.halo.links.service.LinkFeedPublicQueryService;
import run.halo.links.vo.LinkFeedItemPageVo;
import run.halo.links.vo.LinkFeedItemVo;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static run.halo.app.extension.index.query.Queries.and;
import static run.halo.app.extension.index.query.Queries.equal;
import static run.halo.app.extension.index.query.Queries.in;
import static run.halo.app.extension.index.query.Queries.isNull;


@Component
public class LinkFeedPublicQueryServiceImpl implements LinkFeedPublicQueryService {

    private final ReactiveExtensionClient client;

    private final LinkFeedItemStore itemStore;

    public LinkFeedPublicQueryServiceImpl(ReactiveExtensionClient client,
        LinkFeedItemStore itemStore) {
        this.client = client;
        this.itemStore = itemStore;
    }


    @Override
    public Mono<LinkFeedItemPageVo> listFeeds(String groupName, LinkFeedItemQuery query) {
        return StringUtils.hasText(groupName)
            ? listByGroup(groupName, query)
            : listItems(query);
    }

    public Mono<LinkFeedItemPageVo> listItems(LinkFeedItemQuery query) {
        LinkFeedItemQuery requested = Optional.ofNullable(query).orElse(new LinkFeedItemQuery());
        int limit = requested.normalizedLimit();
        LinkFeedItemQuery storeQuery = new LinkFeedItemQuery();
        storeQuery.setLinkName(requested.getLinkName());
        storeQuery.setBeforePublishedAt(requested.getBeforePublishedAt());
        storeQuery.setBeforeId(requested.getBeforeId());
        storeQuery.setRead(requested.getRead());
        storeQuery.setFavorite(requested.getFavorite());
        storeQuery.setReadLater(requested.getReadLater());
        storeQuery.setLimit(limit + 1);

        List<LinkFeedItem> items = itemStore.listRecent(storeQuery);
        boolean hasNext = items.size() > limit;
        List<LinkFeedItem> pageItems = hasNext ? items.subList(0, limit) : items;

        var linkNames = pageItems.stream()
            .map(item -> item.getLinkName())
            .collect(Collectors.toSet());

        return getLinkByLinkNames(linkNames)
            .collectMap(link -> link.getMetadata().getName())
            .map(links -> {

                List<LinkFeedItemVo> list = pageItems.stream()
                    .map(item -> {
                        LinkFeedItemVo vo = LinkFeedItemVo.from(item);
                        String name = item.getLinkName();

                        var link = links.get(name);
                        if (link != null) {
                            if (!StringUtils.hasText(vo.getAuthor())) {
                                vo.setAuthor(link.getSpec().getDisplayName());
                            }
                            vo.setAuthorLogo(link.getSpec().getLogo());
                            vo.setAuthorUrl(link.getSpec().getUrl());
                        }
                        return vo;
                    }).toList();

                LinkFeedItemVo last = list.isEmpty() ? null : list.get(list.size() - 1);
                String nextBeforePublishedAt = last == null || last.getPublishedAt() == null
                    ? null
                    : last.getPublishedAt().toString();
                String nextBeforeId = last == null ? null : last.getId();
                return new LinkFeedItemPageVo(List.copyOf(list), nextBeforePublishedAt, nextBeforeId,
                    hasNext);
            });
    }

    private Mono<LinkFeedItemPageVo> listByGroup(String groupName, LinkFeedItemQuery query) {
        var options = ListOptions.builder()
            .andQuery(equal("spec.groupName", groupName))
            .build();
        return client.listAll(Link.class, options, Sort.unsorted())
            .collectList()
            .flatMap(links -> Mono.fromCallable(() -> queryFeedsByLinks(links, query))
                .subscribeOn(Schedulers.boundedElastic()));
    }

    private LinkFeedItemPageVo queryFeedsByLinks(List<Link> links, LinkFeedItemQuery query) {
        if (links.isEmpty()) {
            return new LinkFeedItemPageVo(List.of(), null, null, false);
        }
        int limit = query.normalizedLimit();
        List<LinkFeedItemVo> items = new ArrayList<>();
        for (Link link : links) {
            LinkFeedItemQuery linkQuery = new LinkFeedItemQuery();
            linkQuery.setLinkName(link.getMetadata().getName());
            linkQuery.setBeforePublishedAt(query.getBeforePublishedAt());
            linkQuery.setBeforeId(query.getBeforeId());
            linkQuery.setRead(query.getRead());
            linkQuery.setFavorite(query.getFavorite());
            linkQuery.setReadLater(query.getReadLater());
            linkQuery.setLimit(limit + 1);
            List<LinkFeedItemVo> linkFeedItemVos = new ArrayList<>();
            List<LinkFeedItem> linkFeedItems = itemStore.listRecent(linkQuery);
            for (LinkFeedItem linkFeedItem : linkFeedItems) {
                LinkFeedItemVo linkFeedItemVo = LinkFeedItemVo.from(linkFeedItem);
                if (!StringUtils.hasText(linkFeedItemVo.getAuthor())) {
                    linkFeedItemVo.setAuthor(link.getSpec().getDisplayName());
                }
                linkFeedItemVo.setAuthorLogo(link.getSpec().getLogo());
                linkFeedItemVo.setAuthorUrl(link.getSpec().getUrl());
                linkFeedItemVos.add(linkFeedItemVo);
            }
            items.addAll(linkFeedItemVos);
        }
        items.sort(recentComparator());
        boolean hasNext = items.size() > limit;
        List<LinkFeedItemVo> pageItems = hasNext
            ? List.copyOf(items.subList(0, limit))
            : List.copyOf(items);
        LinkFeedItemVo last = pageItems.isEmpty() ? null : pageItems.get(pageItems.size() - 1);
        String nextBeforePublishedAt = last == null || last.getPublishedAt() == null
            ? null
            : last.getPublishedAt().toString();
        String nextBeforeId = last == null ? null : last.getId();
        return new LinkFeedItemPageVo(pageItems, nextBeforePublishedAt, nextBeforeId, hasNext);
    }

    private Flux<Link> getLinkByLinkNames(Collection<String> linkNames) {
        if (CollectionUtils.isEmpty(linkNames)) {
            return Flux.empty();
        }
        var listOptions = new ListOptions();
        var query = isNull("metadata.deletionTimestamp");
        query = and(query, in("metadata.name", linkNames));
        listOptions.setFieldSelector(FieldSelector.of(query));
        return client.listAll(Link.class, listOptions, Sort.unsorted());
    }


    private static Comparator<LinkFeedItemVo> recentComparator() {
        return Comparator.comparing(LinkFeedPublicQueryServiceImpl::sortInstant,
                Comparator.nullsLast(Comparator.naturalOrder()))
            .reversed()
            .thenComparing(LinkFeedItemVo::getId, Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private static Instant sortInstant(LinkFeedItemVo item) {
        if (item.getPublishedAt() != null) {
            return item.getPublishedAt();
        }
        if (item.getUpdatedAt() != null) {
            return item.getUpdatedAt();
        }
        return item.getFetchedAt();
    }

}
