package run.halo.links.finders.impl;

import static org.springframework.data.domain.Sort.Order.asc;
import static run.halo.app.extension.index.query.Queries.*;

import org.springframework.data.domain.Sort;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.router.selector.FieldSelector;
import run.halo.app.theme.finders.Finder;
import run.halo.links.extension.Link;
import run.halo.links.extension.LinkGroup;
import run.halo.links.finders.LinkFinder;
import run.halo.links.service.LinkPublicQueryService;
import run.halo.links.vo.LinkGroupVo;
import run.halo.links.vo.LinkVo;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A default implementation for {@link LinkFinder}.
 *
 * @author guqing
 * @author ryanwang
 */
@Finder("linkFinder")
public class LinkFinderImpl implements LinkFinder {
    static final String UNGROUPED_NAME = "ungrouped";
    private final LinkPublicQueryService linkPublicQueryService;

    public LinkFinderImpl(LinkPublicQueryService linkPublicQueryService) {
        this.linkPublicQueryService = linkPublicQueryService;
    }

    @Override
    public Flux<LinkVo> listBy(String groupName) {
        var listOptions = new ListOptions();
        var query = isNull("metadata.deletionTimestamp");
        if (UNGROUPED_NAME.equals(groupName)) {
            query = and(query, isNull("spec.groupName"));
        } else {
            query = and(query, equal("spec.groupName", groupName));
        }
        listOptions.setFieldSelector(FieldSelector.of(query));
        return linkPublicQueryService.listAll(listOptions, defaultLinkSort())
            .flatMapIterable(list -> list);
    }

    @Override
    public Flux<LinkGroupVo> groupBy() {
        var linkOptions = new ListOptions();
        linkOptions.setFieldSelector(
            FieldSelector.of(isNull("metadata.deletionTimestamp")));

        return Mono.zip(
                linkPublicQueryService.listAllGroups(ListOptions.builder().build()),
                linkPublicQueryService.listAll(linkOptions, defaultLinkSort())
            )
            .flatMapMany(tuple -> {
                var groups = tuple.getT1();
                var allLinks = tuple.getT2();

                Map<String, List<LinkVo>> linksByGroup = allLinks.stream()
                    .collect(Collectors.groupingBy(link ->
                        link.getSpec() != null
                            && link.getSpec().getGroupName() != null
                            ? link.getSpec().getGroupName()
                            : UNGROUPED_NAME));

                var result = groups.stream()
                    .map(group -> {
                        var links = linksByGroup.getOrDefault(
                            group.getMetadata().getName(), List.of());
                        return group.withLinks(links);
                    })
                    .toList();

                var ungroupedLinks = linksByGroup.get(UNGROUPED_NAME);
                if (ungroupedLinks != null && !ungroupedLinks.isEmpty()) {
                    return Flux.fromIterable(result)
                        .concatWith(ungrouped()
                            .map(LinkGroupVo::from)
                            .map(group -> group.withLinks(ungroupedLinks)));
                }
                return Flux.fromIterable(result);
            });
    }

    @Override
    public Mono<List<LinkVo>> random(Integer maxSize) {
        return linkPublicQueryService.random(maxSize);
    }

    @Override
    public Mono<Integer> count() {
        return linkPublicQueryService.count();
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
