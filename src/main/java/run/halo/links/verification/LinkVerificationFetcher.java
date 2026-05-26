package run.halo.links.verification;

import org.springframework.stereotype.Component;
import run.halo.links.security.SafeUrlFetcher;

@Component
public class LinkVerificationFetcher {

    static final int MAX_REACHABILITY_BODY_SIZE = 64 * 1024;
    static final int MAX_BACKLINK_BODY_SIZE = 1024 * 1024;
    static final int VERIFICATION_TIMEOUT_MS = 5_000;

    public SafeUrlFetcher.FetchResult fetchReachability(String url) {
        return SafeUrlFetcher.fetch(url,
            SafeUrlFetcher.FetchOptions.verification(url, MAX_REACHABILITY_BODY_SIZE,
                VERIFICATION_TIMEOUT_MS));
    }

    public SafeUrlFetcher.FetchResult fetchBacklinkPage(String url) {
        return SafeUrlFetcher.fetch(url,
            SafeUrlFetcher.FetchOptions.verificationHtml(url, MAX_BACKLINK_BODY_SIZE,
                VERIFICATION_TIMEOUT_MS));
    }
}
