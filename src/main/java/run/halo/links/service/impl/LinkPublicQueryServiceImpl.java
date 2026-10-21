package run.halo.links.service.impl;

import org.apache.commons.lang3.RandomUtils;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ListResult;
import run.halo.app.extension.PageRequest;
import run.halo.app.extension.PageRequestImpl;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.links.extension.Link;
import run.halo.links.extension.LinkGroup;
import run.halo.links.service.LinkPrivateAccessChecker;
import run.halo.links.service.LinkPublicQueryService;
import run.halo.links.vo.LinkGroupVo;
import run.halo.links.vo.LinkVo;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static org.springframework.data.domain.Sort.Order.asc;
import static org.springframework.data.domain.Sort.Order.desc;
import static run.halo.app.extension.index.query.Queries.equal;
import static run.halo.app.extension.index.query.Queries.in;
import static run.halo.app.extension.index.query.Queries.isNull;
import static run.halo.app.extension.index.query.Queries.not;
import static run.halo.app.extension.index.query.Queries.or;

@Component
public class LinkPublicQueryServiceImpl implements LinkPublicQueryService {

    private final ReactiveExtensionClient client;

    private final LinkPrivateAccessChecker accessChecker;

    public LinkPublicQueryServiceImpl(ReactiveExtensionClient client,
        LinkPrivateAccessChecker accessChecker) {
        this.client = client;
        this.accessChecker = accessChecker;
    }


    @Override
    public Mono<ListResult<LinkVo>> listLinks(ListOptions options, PageRequest page) {
        return accessibleOptions(options)
            .flatMap(listOptions -> client.listBy(Link.class, listOptions, page))
            .flatMap(result -> Flux.fromIterable(result.getItems())
                .map(this::toLinkVo)
                .collectList()
                .map(items -> new ListResult<>(
                    result.getPage(), result.getSize(), result.getTotal(), items)));
    }

    @Override
    public Mono<List<LinkVo>> listAll(ListOptions options, Sort sort) {
        return accessibleOptions(options)
            .flatMapMany(listOptions -> client.listAll(Link.class, listOptions, sort))
            .map(this::toLinkVo)
            .collectList();
    }

    @Override
    public Mono<List<LinkGroupVo>> listAllGroups(ListOptions options) {
        return accessChecker.canViewPrivateLinks()
            .flatMapMany(canViewPrivate -> {
                var listOptions = canViewPrivate ? options : publicGroupsOnly(options);
                return client.listAll(LinkGroup.class, listOptions, Sort.unsorted());
            })
            .sort(groupComparator())
            .map(this::toGroupVo)
            .collectList();
    }

    @Override
    public Mono<List<LinkVo>> random(Integer maxSize) {
        Assert.isTrue(maxSize > 0 && maxSize <= 100, "Size must be between 1 and 100");
        return accessibleOptions(new ListOptions())
            .flatMap(options -> client.countBy(Link.class, options)
                .filter(total -> total > 0)
                .flatMap(total -> {
                    var totalInt = total.intValue();
                    var effectiveSize = Math.min(maxSize, totalInt);
                    var totalPages = (int) Math.ceil((double) totalInt / effectiveSize);
                    var page = RandomUtils.insecure().randomInt(1, totalPages + 1);
                    var sort = defaultSort();
                    var firstRequest = PageRequestImpl.of(page, effectiveSize, sort);
                    return client.listBy(Link.class, options, firstRequest)
                        .map(ListResult::getItems)
                        .flatMap(items -> {
                            if (items.size() >= effectiveSize || total <= effectiveSize) {
                                return Mono.just(items);
                            }
                            // wrap around to the beginning to fill up to effectiveSize
                            var remaining = effectiveSize - items.size();
                            var wrapRequest = PageRequestImpl.of(1, remaining, sort);
                            return client.listBy(Link.class, options, wrapRequest)
                                .map(ListResult::getItems)
                                .flatMap(wrapItems -> {
                                    var combined = new ArrayList<>(items);
                                    combined.addAll(wrapItems);
                                    return Mono.just(combined);
                                });
                        });
                }))
            .flatMap(items -> {
                var randomItems = new ArrayList<>(items);
                Collections.shuffle(randomItems, ThreadLocalRandom.current());
                return Flux.fromIterable(randomItems)
                    .map(this::toLinkVo)
                    .collectList();
            })
            .switchIfEmpty(Mono.fromSupplier(List::of));
    }

    @Override
    public Mono<Integer> count() {
        var listOptions = ListOptions.builder()
            .andQuery(isNull("metadata.deletionTimestamp"))
            .build();
        return accessibleOptions(listOptions)
            .flatMap(options -> client.countBy(Link.class, options))
            .map(Long::intValue);
    }


    @Override
    public Mono<Set<String>> privateGroupNames() {
        var options = ListOptions.builder()
            .andQuery(equal("spec.visible", LinkGroup.LinkGroupVisible.PRIVATE.name()))
            .build();
        return client.listAll(LinkGroup.class, options, Sort.unsorted())
            .map(group -> group.getMetadata().getName())
            .collect(Collectors.toSet());
    }

    private Mono<ListOptions> accessibleOptions(ListOptions options) {
        return accessChecker.canViewPrivateLinks()
            .flatMap(canViewPrivate -> canViewPrivate
                ? Mono.just(options)
                : publicOnly(options));
    }

    private Mono<ListOptions> publicOnly(ListOptions options) {
        return privateGroupNames()
            .map(privateGroups -> {
                var builder = ListOptions.builder(options)
                    .andQuery(equal("spec.visible", Link.LinkVisible.PUBLIC.name()));
                if (!privateGroups.isEmpty()) {
                    builder.andQuery(or(
                        isNull("spec.groupName"),
                        not(in("spec.groupName", new ArrayList<>(privateGroups)))
                    ));
                }
                return builder.build();
            });
    }

    static ListOptions publicGroupsOnly(ListOptions options) {
        return ListOptions.builder(options)
            .andQuery(equal("spec.visible", LinkGroup.LinkGroupVisible.PUBLIC.name()))
            .build();
    }


    private LinkGroupVo toGroupVo(LinkGroup group) {
        return LinkGroupVo.from(group);
    }

    public LinkVo toLinkVo(Link link) {
        return LinkVo.from(link);
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

    static Sort defaultSort() {
        return Sort.by(
            desc("metadata.creationTimestamp"),
            asc("metadata.name")
        );
    }
}
