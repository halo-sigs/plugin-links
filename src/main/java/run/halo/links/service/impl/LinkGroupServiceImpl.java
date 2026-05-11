package run.halo.links.service.impl;

import static run.halo.app.extension.index.query.Queries.equal;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.links.Link;
import run.halo.links.LinkGroup;
import run.halo.links.service.LinkGroupService;

@Component
public class LinkGroupServiceImpl implements LinkGroupService {

    private final ReactiveExtensionClient client;

    public LinkGroupServiceImpl(ReactiveExtensionClient client) {
        this.client = client;
    }

    @Override
    public Mono<LinkGroup> deleteLinkGroup(String name, boolean deleteLinks) {
        return this.client.fetch(LinkGroup.class, name)
            .flatMap(group -> {
                var listOptions = ListOptions.builder()
                    .andQuery(equal("spec.groupName", name))
                    .build();
                var links = this.client.listAll(Link.class, listOptions, Sort.unsorted());
                if (!deleteLinks) {
                    return links
                        .flatMap(link -> {
                            link.getSpec().setGroupName(null);
                            return client.update(link);
                        })
                        .then(this.client.delete(group))
                        .thenReturn(group);
                }
                return links
                    .flatMap(this.client::delete)
                    .then(this.client.delete(group))
                    .thenReturn(group);
            });
    }
}
