package run.halo.links.rss;

public interface LinkFeedStorageMaintenance {

    boolean isAvailable();

    void compactIfNeeded();

    void snapshotIfDue();
}
