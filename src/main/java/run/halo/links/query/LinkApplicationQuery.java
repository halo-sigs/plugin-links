package run.halo.links.query;

import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static run.halo.app.extension.index.query.Queries.equal;
import static run.halo.app.extension.index.query.Queries.greaterThan;
import static run.halo.app.extension.index.query.Queries.lessThan;

import io.swagger.v3.oas.annotations.enums.ParameterIn;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import org.apache.commons.lang3.StringUtils;
import org.springdoc.core.fn.builders.operation.Builder;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
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

    public Instant getCreatedAfter() {
        return parseInstant("createdAfter");
    }

    public Instant getCreatedBefore() {
        return parseInstant("createdBefore");
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
        var after = getCreatedAfter();
        if (after != null) {
            builder.andQuery(greaterThan("metadata.creationTimestamp", after, true));
        }
        var before = getCreatedBefore();
        if (before != null) {
            builder.andQuery(lessThan("metadata.creationTimestamp", before, true));
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

    private Instant parseInstant(String name) {
        var value = queryParams.getFirst(name);
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            return Instant.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                name + " must be an ISO-8601 instant.", e);
        }
    }

    public static void buildParameters(Builder builder) {
        builder
            .parameter(parameterBuilder().name("status").in(ParameterIn.QUERY)
                .description("Application status.")
                .implementation(String.class))
            .parameter(parameterBuilder().name("originType").in(ParameterIn.QUERY)
                .description("Application origin type.")
                .implementation(String.class))
            .parameter(parameterBuilder().name("createdAfter").in(ParameterIn.QUERY)
                .description("Inclusive ISO-8601 creation-time lower bound.")
                .implementation(Instant.class))
            .parameter(parameterBuilder().name("createdBefore").in(ParameterIn.QUERY)
                .description("Inclusive ISO-8601 creation-time upper bound.")
                .implementation(Instant.class));
        SortableRequest.buildParameters(builder);
    }
}
