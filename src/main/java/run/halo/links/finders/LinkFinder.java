package run.halo.links.finders;

import java.util.List;
import run.halo.links.vo.LinkGroupVo;
import run.halo.links.vo.LinkVo;

/**
 * A finder for {@link run.halo.links.Link}.
 *
 * @author guqing
 * @author ryanwang
 */
public interface LinkFinder {

    List<LinkVo> listBy(String group);

    List<LinkGroupVo> groupBy();
}
