package run.halo.links.finders.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Sort;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.router.selector.FieldSelector;
import tools.jackson.databind.json.JsonMapper;
import run.halo.app.theme.finders.Finder;
import run.halo.links.extension.Link;
import run.halo.links.extension.LinkGroup;
import run.halo.links.finders.LinkFeedFinder;
import run.halo.links.rss.LinkFeedItem;
import run.halo.links.rss.LinkFeedItemQuery;
import run.halo.links.rss.LinkFeedItemStore;
import run.halo.links.service.LinkFeedPublicQueryService;
import run.halo.links.vo.LinkFeedGroupVo;
import run.halo.links.vo.LinkFeedItemPageVo;
import run.halo.links.vo.LinkFeedItemVo;
import run.halo.links.vo.LinkFeedVo;

import static org.springframework.data.domain.Sort.Order.asc;
import static run.halo.app.extension.index.query.Queries.isNull;


@Finder("linkFeedFinder")
public class LinkFeedFinderImpl implements LinkFeedFinder {

    static final String UNGROUPED_NAME = "ungrouped";

    private final ReactiveExtensionClient client;

    private final LinkFeedPublicQueryService linkFeedPublicQueryService;

    private final LinkFeedItemStore itemStore;

    public LinkFeedFinderImpl(ReactiveExtensionClient client, LinkFeedPublicQueryService linkFeedPublicQueryService,
        LinkFeedItemStore itemStore) {
        this.client = client;
        this.linkFeedPublicQueryService = linkFeedPublicQueryService;
        this.itemStore = itemStore;
    }

    @Override
    public Mono<LinkFeedItemPageVo> list(Map<String, Object> params) {
        return linkFeedPublicQueryService.isPublicEnabled()
            .flatMap(enabled -> enabled
                ? listWhenPublicEnabled(params)
                : Mono.just(new LinkFeedItemPageVo(List.of(), null, null, false)));
    }

    @Override
    public Flux<LinkFeedGroupVo> groupBy(Integer limit) {
        return linkFeedPublicQueryService.isPublicEnabled()
            .flatMapMany(enabled -> enabled ? groupByWhenPublicEnabled(limit) : Flux.empty());
    }

    private Mono<LinkFeedItemPageVo> listWhenPublicEnabled(Map<String, Object> params) {
        var query = Optional.ofNullable(params)
            .map(map -> JsonMapper.shared().convertValue(map, LinkFeedItemQuery.class))
            .orElseGet(LinkFeedItemQuery::new);
        String groupName = Optional.ofNullable(params)
            .map(map -> (String) map.get("groupName"))
            .orElse(null);
        return linkFeedPublicQueryService.listFeeds(groupName, query);
    }

    private Flux<LinkFeedGroupVo> groupByWhenPublicEnabled(Integer limit) {
        var linkOptions = new ListOptions();
        linkOptions.setFieldSelector(
            FieldSelector.of(isNull("metadata.deletionTimestamp")));

        return Mono.zip(
                listAllGroups(ListOptions.builder().build()),
                listAllLinkFeeds(linkOptions,limit)
            )
            .flatMapMany(tuple -> {
                var groups = tuple.getT1();
                var allLinks = tuple.getT2();

                Map<String, List<LinkFeedVo>> linksByGroup = allLinks.stream()
                    .collect(Collectors.groupingBy(link ->
                        link.getSpec() != null
                            && link.getSpec().getGroupName() != null
                            ? link.getSpec().getGroupName()
                            : UNGROUPED_NAME));

                var result = groups.stream()
                    .map(group -> {
                        var links = linksByGroup.getOrDefault(
                            group.getMetadata().getName(), List.of());

                        links = links.stream()
                            .sorted(Comparator.comparing((LinkFeedVo link) -> {
                                    List<LinkFeedItemVo> linkFeedItems = link.getFeeds();
                                    if (linkFeedItems != null && !linkFeedItems.isEmpty()) {
                                        return linkFeedItems.get(0).getPublishedAt();
                                    }
                                    return null;
                                }, Comparator.nullsLast(Comparator.reverseOrder())
                            )).toList();
                        return group.withLinks(links);
                    })
                    .toList();

                var ungroupedLinks = linksByGroup.get(UNGROUPED_NAME);
                if (ungroupedLinks != null && !ungroupedLinks.isEmpty()) {
                    return Flux.fromIterable(result)
                        .concatWith(ungrouped()
                            .map(LinkFeedGroupVo::from)
                            .map(group -> group.withLinks(ungroupedLinks)));
                }
                return Flux.fromIterable(result);
            });
    }

    private Mono<List<LinkFeedGroupVo>> listAllGroups(ListOptions options) {
        return client.listAll(LinkGroup.class, options, Sort.unsorted())
            .sort(groupComparator())
            .map(this::toGroupVo)
            .collectList();
    }

    private LinkFeedGroupVo toGroupVo(LinkGroup group) {
        return LinkFeedGroupVo.from(group);
    }

    private Mono<List<LinkFeedVo>> listAllLinkFeeds(ListOptions options, Integer limit) {
        return client.listAll(Link.class, options, defaultLinkSort())
            .concatMap(link -> Mono.fromCallable(() -> {
                LinkFeedVo linkFeed = LinkFeedVo.from(link);
                LinkFeedItemQuery storeQuery = new LinkFeedItemQuery();
                storeQuery.setLinkName(link.getMetadata().getName());
                storeQuery.setLimit(limit);

                List<LinkFeedItemVo> linkFeedItemVos = new ArrayList<>();

                List<LinkFeedItem> linkFeedItems = itemStore.listRecent(storeQuery);
                for (LinkFeedItem linkFeedItem : linkFeedItems) {
                    LinkFeedItemVo linkFeedItemVo = LinkFeedItemVo.from(linkFeedItem);
                    if (StringUtils.isEmpty(linkFeedItemVo.getAuthor())) {
                        linkFeedItemVo.setAuthor(link.getSpec().getDisplayName());
                    }
                    linkFeedItemVo.setAuthorLogo(link.getSpec().getLogo());
                    linkFeedItemVo.setAuthorUrl(link.getSpec().getUrl());
                    linkFeedItemVos.add(linkFeedItemVo);
                }
                linkFeed.setFeeds(linkFeedItemVos);
                return linkFeed;
            }).subscribeOn(Schedulers.boundedElastic()))
            .collectList();
    }

    static Comparator<LinkGroup> groupComparator() {
        return (g1, g2) -> {
            var p1 = g1.getSpec() != null && g1.getSpec().getPriority() != null
                ? g1.getSpec().getPriority() : 0;
            var p2 = g2.getSpec() != null && g2.getSpec().getPriority() != null
                ? g2.getSpec().getPriority() : 0;
            int priorityCompare = Integer.compare(p1, p2);
            if (priorityCompare != 0) {
                return priorityCompare;
            }
            var t1 = g1.getMetadata() != null ? g1.getMetadata().getCreationTimestamp() : null;
            var t2 = g2.getMetadata() != null ? g2.getMetadata().getCreationTimestamp() : null;
            if (t1 == null && t2 == null) {
                return 0;
            }
            if (t1 == null) {
                return 1;
            }
            if (t2 == null) {
                return -1;
            }
            int timeCompare = t1.compareTo(t2);
            if (timeCompare != 0) {
                return timeCompare;
            }
            var n1 = g1.getMetadata() != null ? g1.getMetadata().getName() : "";
            var n2 = g2.getMetadata() != null ? g2.getMetadata().getName() : "";
            return n1.compareTo(n2);
        };
    }


    Mono<LinkGroup> ungrouped() {
        LinkGroup linkGroup = new LinkGroup();
        linkGroup.setMetadata(new Metadata());
        linkGroup.getMetadata().setName(UNGROUPED_NAME);
        linkGroup.setSpec(new LinkGroup.LinkGroupSpec());
        linkGroup.getSpec().setDisplayName("");
        linkGroup.getSpec().setPriority(0);
        return Mono.just(linkGroup);
    }

    static Sort defaultLinkSort() {
        return Sort.by(asc("spec.priority"),
            asc("metadata.creationTimestamp"),
            asc("metadata.name")
        );
    }
}
