package run.halo.links.endpoint;

import org.springframework.util.ClassUtils;

/**
 * Utility class for checking whether the ai-foundation plugin is available.
 */
public final class AiFoundationAvailability {

    private static final String AI_MODEL_SERVICE_CLASS = "run.halo.aifoundation.AiModelService";

    private AiFoundationAvailability() {
    }

    /**
     * Returns {@code true} if the ai-foundation plugin classes are present on the classpath.
     */
    public static boolean isAvailable() {
        return isAvailable(AiFoundationAvailability.class.getClassLoader());
    }

    static boolean isAvailable(ClassLoader classLoader) {
        return ClassUtils.isPresent(AI_MODEL_SERVICE_CLASS, classLoader);
    }
}
