package run.halo.links.finders.impl;

import static org.springframework.data.domain.Sort.Order.asc;
import static run.halo.app.extension.index.query.Queries.*;

import org.springframework.data.domain.Sort;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ListResult;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.PageRequestImpl;
import run.halo.app.extension.router.selector.FieldSelector;
import run.halo.app.theme.finders.Finder;
import run.halo.links.LinkGroup;
import run.halo.links.finders.LinkFinder;
import run.halo.links.finders.LinkPublicQueryService;
import run.halo.links.vo.LinkGroupVo;
import run.halo.links.vo.LinkVo;
import java.util.List;

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
        return linkPublicQueryService.listLinks(listOptions,
            PageRequestImpl.of(1, Integer.MAX_VALUE, defaultLinkSort()))
            .flatMapIterable(ListResult::getItems);
    }

    @Override
    public Flux<LinkGroupVo> groupBy() {
        return linkPublicQueryService.listGroups(ListOptions.builder().build(),
                PageRequestImpl.of(1, Integer.MAX_VALUE, defaultGroupSort()))
            .flatMapIterable(ListResult::getItems)
            .concatMap(group -> listBy(group.getMetadata().getName())
                .collectList()
                .map(group::withLinks)
                .defaultIfEmpty(group)
            )
            .mergeWith(Mono.defer(() -> listBy(UNGROUPED_NAME)
                .collectList()
                // do not return ungrouped group if no links
                .filter(links -> !links.isEmpty())
                .flatMap(links -> ungrouped()
                    .map(LinkGroupVo::from)
                    .map(group -> group.withLinks(links))
                )
            ));
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

    static Sort defaultGroupSort() {
        return Sort.by(asc("spec.priority"),
            asc("metadata.creationTimestamp"),
            asc("metadata.name")
        );
    }

    static Sort defaultLinkSort() {
        return Sort.by(asc("spec.priority"),
            asc("metadata.creationTimestamp"),
            asc("metadata.name")
        );
    }
}
