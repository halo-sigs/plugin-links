package run.halo.links.finders.impl;

import java.time.Instant;
import java.util.Comparator;
import java.util.function.Function;
import java.util.function.Predicate;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;
import org.springframework.util.comparator.Comparators;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
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
    private final ReactiveExtensionClient client;

    public LinkFinderImpl(ReactiveExtensionClient client) {
        this.client = client;
    }

    @Override
    public Flux<LinkVo> listBy(String groupName) {
        return listAll(link -> StringUtils.equals(link.getSpec().getGroupName(), groupName)
            && link.getMetadata().getDeletionTimestamp() != null)
            .map(LinkVo::from);
    }

    @Override
    public Flux<LinkGroupVo> groupBy() {
        Flux<Link> linkFlux = listAll(null);
        return listAllGroups()
            .concatMap(group -> linkFlux
                .filter(link -> StringUtils.equals(link.getSpec().getGroupName(),
                    group.getMetadata().getName())
                )
                .map(LinkVo::from)
                .collectList()
                .map(group::withLinks)
                .defaultIfEmpty(group)
            )
            .mergeWith(Mono.defer(() -> ungrouped()
                .map(LinkGroupVo::from)
                .flatMap(linkGroup -> linkFlux.filter(
                        link -> StringUtils.isBlank(link.getSpec().getGroupName()))
                    .map(LinkVo::from)
                    .collectList()
                    .map(linkGroup::withLinks)
                    .defaultIfEmpty(linkGroup)
                ))
            );
    }

    Mono<LinkGroup> ungrouped() {
        LinkGroup linkGroup = new LinkGroup();
        linkGroup.setMetadata(new Metadata());
        linkGroup.getMetadata().setName("ungrouped");
        linkGroup.setSpec(new LinkGroup.LinkGroupSpec());
        linkGroup.getSpec().setDisplayName("");
        linkGroup.getSpec().setPriority(0);
        return Mono.just(linkGroup);
    }

    Flux<Link> listAll(@Nullable Predicate<Link> predicate) {
        return client.list(Link.class, predicate, defaultLinkComparator());
    }

    Flux<LinkGroupVo> listAllGroups() {
        return client.list(LinkGroup.class, null, defaultGroupComparator())
            .map(LinkGroupVo::from);
    }

    static Comparator<LinkGroup> defaultGroupComparator() {
        Function<LinkGroup, Integer> priority = group -> group.getSpec().getPriority();
        Function<LinkGroup, Instant> createTime =
            group -> group.getMetadata().getCreationTimestamp();
        Function<LinkGroup, String> name = group -> group.getMetadata().getName();
        return Comparator.comparing(priority, Comparators.nullsLow())
            .thenComparing(createTime)
            .thenComparing(name);
    }

    static Comparator<Link> defaultLinkComparator() {
        Function<Link, Integer> priority = link -> link.getSpec().getPriority();
        Function<Link, Instant> createTime = link -> link.getMetadata().getCreationTimestamp();
        Function<Link, String> name = link -> link.getMetadata().getName();
        return Comparator.comparing(priority, Comparators.nullsLow())
            .thenComparing(createTime)
            .thenComparing(name);
    }
}
