package run.halo.links.finders.impl;

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
import run.halo.app.extension.router.selector.FieldSelector;
import run.halo.links.Link;
import run.halo.links.LinkGroup;
import run.halo.links.finders.LinkPublicQueryService;
import run.halo.links.vo.LinkGroupVo;
import run.halo.links.vo.LinkVo;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.springframework.data.domain.Sort.Order.asc;
import static org.springframework.data.domain.Sort.Order.desc;
import static run.halo.app.extension.index.query.Queries.isNull;

@Component
public class LinkPublicQueryServiceImpl implements LinkPublicQueryService {

    private final ReactiveExtensionClient client;

    public LinkPublicQueryServiceImpl(ReactiveExtensionClient client) {
        this.client = client;
    }


    @Override
    public Mono<ListResult<LinkVo>> listLinks(ListOptions options, PageRequest page) {
        return client.listBy(Link.class, options, page)
            .flatMap(result -> Flux.fromIterable(result.getItems())
                .flatMap(this::toLinkVo)
                .collectList()
                .map(items -> new ListResult<>(
                    result.getPage(), result.getSize(), result.getTotal(), items)));
    }

    @Override
    public Mono<List<LinkGroupVo>> listAllGroups(ListOptions options) {
        return client.listAll(LinkGroup.class, options, Sort.unsorted())
            .sort(groupComparator())
            .concatMap(this::toGroupVo)
            .collectList();
    }

    @Override
    public Mono<List<LinkVo>> random(Integer maxSize) {
        Assert.isTrue(maxSize > 0 && maxSize <= 100, "Size must be between 1 and 100");
        return client.countBy(Link.class, new ListOptions())
            .filter(total -> total > 0)
            .flatMap(total -> {
                var totalInt = total.intValue();
                var effectiveSize = Math.min(maxSize, totalInt);
                var totalPages = (int) Math.ceil((double) totalInt / effectiveSize);
                var page = RandomUtils.insecure().randomInt(1, totalPages + 1);
                var sort = defaultSort();
                var firstRequest = PageRequestImpl.of(page, effectiveSize, sort);
                return client.listBy(Link.class, new ListOptions(), firstRequest)
                    .map(ListResult::getItems)
                    .flatMap(items -> {
                        if (items.size() >= effectiveSize || total <= effectiveSize) {
                            return Mono.just(items);
                        }
                        // wrap around to the beginning to fill up to effectiveSize
                        var remaining = effectiveSize - items.size();
                        var wrapRequest = PageRequestImpl.of(1, remaining, sort);
                        return client.listBy(Link.class, new ListOptions(), wrapRequest)
                            .map(ListResult::getItems)
                            .flatMap(wrapItems -> {
                                var combined = new ArrayList<>(items);
                                combined.addAll(wrapItems);
                                return Mono.just(combined);
                            });
                    });
            })
            .flatMap(items -> {
                var randomItems = new ArrayList<>(items);
                Collections.shuffle(randomItems, ThreadLocalRandom.current());
                return Flux.fromIterable(randomItems)
                    .concatMap(this::toLinkVo)
                    .collectList();
            })
            .switchIfEmpty(Mono.fromSupplier(List::of));
    }

    @Override
    public Mono<Integer> count() {
        var listOptions = new ListOptions();
        listOptions.setFieldSelector(FieldSelector.of(
            isNull("metadata.deletionTimestamp")
        ));
        return client.listBy(Link.class, listOptions, PageRequestImpl.ofSize(1))
            .flatMap(links -> Mono.just((int)links.getTotal()));
    }


    private Mono<LinkGroupVo> toGroupVo(LinkGroup group) {
        return Mono.fromSupplier(() -> LinkGroupVo.from(group));
    }

    public Mono<LinkVo> toLinkVo(Link link) {
        return Mono.fromSupplier(() -> LinkVo.from(link));
    }

    static Comparator<LinkGroup> groupComparator() {
        return (g1, g2) -> {
            var p1 = g1.getSpec() != null && g1.getSpec().getPriority() != null
                ? g1.getSpec().getPriority() : 0;
            var p2 = g2.getSpec() != null && g2.getSpec().getPriority() != null
                ? g2.getSpec().getPriority() : 0;
            int priorityCompare = Integer.compare(p2, p1);
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
            int timeCompare = t2.compareTo(t1);
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
