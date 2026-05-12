package run.halo.links.service;

import reactor.core.publisher.Mono;
import run.halo.links.extension.LinkGroup;

/**
 * A service for {@link LinkGroup}.
 */
public interface LinkGroupService {

    /**
     * Delete a link group by name.
     *
     * @param name        group name
     * @param deleteLinks when {@code true}, all links in the group are deleted;
     *                    when {@code false}, links are ungrouped (their
     *                    {@code spec.groupName} is cleared)
     * @return the deleted group, or empty if not found
     */
    Mono<LinkGroup> deleteLinkGroup(String name, boolean deleteLinks);
}
