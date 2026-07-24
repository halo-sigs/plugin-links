package run.halo.links.service.ai;

/**
 * Plugin-owned error for AI structured-output validation failures.
 */
public class LinkAiOutputException extends RuntimeException {

    public LinkAiOutputException(Throwable cause) {
        super(cause.getMessage(), cause);
    }
}
