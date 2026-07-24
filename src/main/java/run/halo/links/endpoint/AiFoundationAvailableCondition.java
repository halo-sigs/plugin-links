package run.halo.links.endpoint;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Condition that evaluates to true only when the ai-foundation plugin is available.
 */
public class AiFoundationAvailableCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return AiFoundationAvailability.isAvailable();
    }
}
