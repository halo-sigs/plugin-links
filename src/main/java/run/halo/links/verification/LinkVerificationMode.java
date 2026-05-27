package run.halo.links.verification;

public enum LinkVerificationMode {
    FULL(true),
    ACCESS_ONLY(false);

    private final boolean includeBacklink;

    LinkVerificationMode(boolean includeBacklink) {
        this.includeBacklink = includeBacklink;
    }

    boolean includeBacklink() {
        return includeBacklink;
    }
}
