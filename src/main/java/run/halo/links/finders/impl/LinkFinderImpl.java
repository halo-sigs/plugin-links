package run.halo.links.finders.impl;

import static org.springframework.data.domain.Sort.Order.asc;
import static run.halo.app.extension.index.query.Queries.*;

import java.util.List;
import org.springframework.data.domain.Sort;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.router.selector.FieldSelector;
import run.halo.app.theme.finders.Finder;
import run.halo.links.Link;
import run.halo.links.LinkGroup;
import run.halo.links.finders.LinkFinder;
import run.halo.links.vo.LinkGroupVo;
import run.halo.links.vo.LinkVo;

/**
 * A default implementation for {@link LinkFinder}.
 *
 * @author guqing
 * @author ryanwang
 */
@Finder("linkFinder")
public class LinkFinderImpl implements LinkFinder {
    static final String UNGROUPED_NAME = "ungrouped";
    private final ReactiveExtensionClient client;

    public LinkFinderImpl(ReactiveExtensionClient client) {
        this.client = client;
    }

    @Override
    public Flux<LinkVo> listBy(String groupName) {
        return listByInternal(groupName);
    }

    @Override
    public Flux<LinkVo> listBy(List<String> groupNames, int limit) {
        var listOptions = new ListOptions();
        var query = isNull("metadata.deletionTimestamp");

        if (groupNames != null && !groupNames.isEmpty()) {
            boolean hasUngrouped = groupNames.contains(UNGROUPED_NAME);
            List<String> validGroupNames = groupNames.stream()
                    .filter(name -> !UNGROUPED_NAME.equals(name))
                    .toList();

            var groupQuery = validGroupNames.isEmpty() ? null : in("spec.groupName", validGroupNames);

            if (hasUngrouped) {
                var ungroupedQuery = isNull("spec.groupName");
                if (groupQuery != null) {
                    query = and(query, or(ungroupedQuery, groupQuery));
                } else {
                    query = and(query, ungroupedQuery);
                }
            } else if (groupQuery != null) {
                query = and(query, groupQuery);
            }
        }

        listOptions.setFieldSelector(FieldSelector.of(query));

        if (limit > 0) {
            var pageRequest = new run.halo.app.extension.PageRequestImpl(0, limit, defaultLinkSort());
            return client.listBy(Link.class, listOptions, pageRequest)
                    .flatMapIterable(run.halo.app.extension.ListResult::getItems)
                    .map(LinkVo::from);
        } else {
            return client.listAll(Link.class, listOptions, defaultLinkSort())
                    .map(LinkVo::from);
        }
    }

    @Override
    public Flux<LinkGroupVo> groupBy() {
        return client.listAll(LinkGroup.class, new ListOptions(), defaultGroupSort())
                .map(LinkGroupVo::from)
                .concatMap(group -> listByInternal(group.getMetadata().getName())
                        .collectList()
                        .map(group::withLinks)
                        .defaultIfEmpty(group))
                .mergeWith(Mono.defer(() -> listByInternal(UNGROUPED_NAME)
                        .collectList()
                        // do not return ungrouped group if no links
                        .filter(links -> !links.isEmpty())
                        .flatMap(links -> ungrouped()
                                .map(LinkGroupVo::from)
                                .map(group -> group.withLinks(links)))));
    }

    private Flux<LinkVo> listByInternal(String groupName) {
        var listOptions = new ListOptions();
        var query = isNull("metadata.deletionTimestamp");
        if (UNGROUPED_NAME.equals(groupName)) {
            query = and(query, isNull("spec.groupName"));
        } else {
            query = and(query, equal("spec.groupName", groupName));
        }
        listOptions.setFieldSelector(FieldSelector.of(query));
        return client.listAll(Link.class, listOptions, defaultLinkSort())
                .map(LinkVo::from);
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
                asc("metadata.name"));
    }

    static Sort defaultLinkSort() {
        return Sort.by(asc("spec.priority"),
                asc("metadata.creationTimestamp"),
                asc("metadata.name"));
    }
}
