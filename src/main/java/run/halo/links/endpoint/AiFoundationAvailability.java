package run.halo.links.endpoint;

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
        try {
            Class.forName(AI_MODEL_SERVICE_CLASS);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
