package run.halo.links.finders;

import java.util.List;
import reactor.core.publisher.Flux;
import run.halo.links.vo.LinkGroupVo;
import run.halo.links.vo.LinkVo;

/**
 * A finder for {@link run.halo.links.Link}.
 *
 * @author guqing
 * @author ryanwang
 */
public interface LinkFinder {

    Flux<LinkVo> listBy(String group);

    /**
     * Lists links from specified groups with a total limit on the number of
     * results.
     *
     * @param groupNames the list of group names to include, empty or null means all
     *                   groups
     * @param limit      the maximum total number of links to return, 0 or negative
     *                   means no limit
     * @return a flux of link VOs
     */
    Flux<LinkVo> listBy(List<String> groupNames, int limit);

    Flux<LinkGroupVo> groupBy();
}
