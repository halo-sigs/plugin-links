package run.halo.links.rss;

public class LinkFeedStorageUnavailableException extends IllegalStateException {

    public LinkFeedStorageUnavailableException(String message) {
        super(message);
    }

    public LinkFeedStorageUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
