package run.halo.links.query;

import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static run.halo.app.extension.index.query.Queries.and;
import static run.halo.app.extension.index.query.Queries.contains;
import static run.halo.app.extension.index.query.Queries.equal;
import static run.halo.app.extension.index.query.Queries.or;

import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.springdoc.core.fn.builders.operation.Builder;
import org.springframework.data.domain.Sort;
import org.springframework.web.server.ServerWebExchange;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.router.SortableRequest;
import run.halo.links.extension.Link;

/**
 * A query object for {@link Link} list in console API.
 */
public class LinkQuery extends SortableRequest {

    public LinkQuery(ServerWebExchange exchange) {
        super(exchange);
    }

    @Schema(description = "Keyword used to search link display names, descriptions, and URLs.")
    public String getKeyword() {
        return queryParams.getFirst("keyword");
    }

    @Schema(description = "Metadata name of the link group to filter by.")
    public String getGroupName() {
        return queryParams.getFirst("groupName");
    }

    @Override
    public ListOptions toListOptions() {
        var builder = ListOptions.builder(super.toListOptions());
        if (StringUtils.isNotBlank(getKeyword())) {
            builder.andQuery(or(
                contains("spec.displayName", getKeyword()),
                contains("spec.description", getKeyword()),
                contains("spec.url", getKeyword())
            ));
        }
        if (StringUtils.isNotBlank(getGroupName())) {
            builder.andQuery(equal("spec.groupName", getGroupName()));
        }
        return builder.build();
    }

    @Override
    public Sort getSort() {
        return super.getSort()
            .and(Sort.by(
                Sort.Order.desc("metadata.creationTimestamp"),
                Sort.Order.asc("metadata.name")
            ));
    }

    public static void buildParameters(Builder builder) {
        builder.parameter(parameterBuilder()
                .name("keyword")
                .description("Keyword used to search link display names, descriptions, and URLs.")
                .in(ParameterIn.QUERY)
                .implementation(String.class)
                .required(false)
            )
            .parameter(parameterBuilder()
                .name("groupName")
                .description("Metadata name of the link group to filter by.")
                .in(ParameterIn.QUERY)
                .implementation(String.class)
                .required(false)
            );
        SortableRequest.buildParameters(builder);
    }
}
