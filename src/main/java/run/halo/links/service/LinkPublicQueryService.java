package run.halo.links.service;

import org.springframework.data.domain.Sort;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ListResult;
import run.halo.app.extension.PageRequest;
import run.halo.links.vo.LinkGroupVo;
import run.halo.links.vo.LinkVo;
import java.util.List;

public interface LinkPublicQueryService {

    /**
     * List links with filters and pagination.
     *
     * @param options list options
     * @param page    page request
     * @return a mono of list result
     */
    Mono<ListResult<LinkVo>> listLinks(ListOptions options, PageRequest page);

    /**
     * List all links without pagination.
     *
     * @param options list options
     * @param sort    sort order
     * @return a mono of list of link vos
     */
    Mono<List<LinkVo>> listAll(ListOptions options, Sort sort);

    /**
     * List all link groups without pagination.
     *
     * @param options list options
     * @return a mono of list of link group vos
     */
    Mono<List<LinkGroupVo>> listAllGroups(ListOptions options);

    Mono<List<LinkVo>> random(Integer maxSize);

    Mono<Integer> count();
}
