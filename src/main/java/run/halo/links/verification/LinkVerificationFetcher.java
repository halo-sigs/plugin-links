package run.halo.links.verification;

import org.springframework.stereotype.Component;
import run.halo.links.security.SafeUrlFetcher;

@Component
public class LinkVerificationFetcher {

    static final int MAX_REACHABILITY_BODY_SIZE = 1024 * 1024;
    static final int MAX_BACKLINK_BODY_SIZE = 1024 * 1024;

    public SafeUrlFetcher.FetchResult fetchReachability(String url) {
        return SafeUrlFetcher.fetch(url,
            SafeUrlFetcher.FetchOptions.verification(url, MAX_REACHABILITY_BODY_SIZE));
    }

    public SafeUrlFetcher.FetchResult fetchBacklinkPage(String url) {
        return SafeUrlFetcher.fetch(url,
            SafeUrlFetcher.FetchOptions.verificationHtml(url, MAX_BACKLINK_BODY_SIZE));
    }
}
