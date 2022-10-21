package run.halo.links.finders.impl;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.springframework.lang.Nullable;
import org.springframework.util.comparator.Comparators;
import reactor.core.publisher.Flux;
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
    public List<LinkVo> listBy(String groupName) {
        return client.fetch(LinkGroup.class, groupName)
            .mapNotNull(group -> group.getSpec().getLinks())
            .flatMapMany(linkNames -> Flux.fromIterable(linkNames)
                .flatMap(linkName -> client.fetch(Link.class, linkName)))
            .map(LinkVo::from)
            .collectList()
            .block();
    }

    @Override
    public List<LinkGroupVo> groupBy() {
        Map<String, Link> nameLink = listAll(null)
            .stream()
            .collect(Collectors.toMap(link -> link.getMetadata().getName(), link -> link));
        return listAllGroups()
            .stream()
            .map(group -> {
                LinkedHashSet<String> linkNames = group.getSpec().getLinks();
                if (linkNames == null) {
                    return group;
                }
                List<LinkVo> links = linkNames.stream()
                    .filter(nameLink::containsKey)
                    .map(nameLink::get)
                    .sorted(defaultLinkComparator())
                    .map(LinkVo::from)
                    .toList();
                return group.withLinks(links);
            })
            .toList();
    }

    List<Link> listAll(@Nullable Predicate<Link> predicate) {
        return client.list(Link.class, predicate, defaultLinkComparator())
            .collectList()
            .block();
    }

    List<LinkGroupVo> listAllGroups() {
        return client.list(LinkGroup.class, null, defaultGroupComparator())
            .map(LinkGroupVo::from)
            .collectList()
            .block();
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
