package run.halo.links.query;

import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static run.halo.app.extension.index.query.Queries.equal;

import io.swagger.v3.oas.annotations.enums.ParameterIn;
import org.apache.commons.lang3.StringUtils;
import org.springdoc.core.fn.builders.operation.Builder;
import org.springframework.data.domain.Sort;
import org.springframework.web.server.ServerWebExchange;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.router.SortableRequest;

/**
 * Shared list and cleanup filters for LinkApplication history.
 */
public class LinkApplicationQuery extends SortableRequest {

    public LinkApplicationQuery(ServerWebExchange exchange) {
        super(exchange);
    }

    public String getStatus() {
        return queryParams.getFirst("status");
    }

    public String getOriginType() {
        return queryParams.getFirst("originType");
    }

    @Override
    public ListOptions toListOptions() {
        var builder = ListOptions.builder(super.toListOptions());
        if (StringUtils.isNotBlank(getStatus())) {
            builder.andQuery(equal("spec.status", getStatus().trim().toUpperCase()));
        }
        if (StringUtils.isNotBlank(getOriginType())) {
            builder.andQuery(equal("spec.origin.type",
                getOriginType().trim().toUpperCase()));
        }
        return builder.build();
    }

    @Override
    public Sort getSort() {
        return super.getSort().and(Sort.by(
            Sort.Order.desc("metadata.creationTimestamp"),
            Sort.Order.asc("metadata.name")
        ));
    }

    public static void buildParameters(Builder builder) {
        builder
            .parameter(parameterBuilder().name("status").in(ParameterIn.QUERY)
                .description("Application status.")
                .implementation(String.class))
            .parameter(parameterBuilder().name("originType").in(ParameterIn.QUERY)
                .description("Application origin type.")
                .implementation(String.class));
        SortableRequest.buildParameters(builder);
    }
}
