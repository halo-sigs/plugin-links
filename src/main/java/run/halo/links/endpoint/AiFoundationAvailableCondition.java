package run.halo.links.endpoint;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Condition that evaluates to true only when the ai-foundation plugin is available.
 */
public class AiFoundationAvailableCondition implements Condition {

    private static final String AI_MODEL_SERVICE_CLASS = "run.halo.aifoundation.AiModelService";

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        try {
            Class.forName(AI_MODEL_SERVICE_CLASS);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
