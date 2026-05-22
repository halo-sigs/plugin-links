package run.halo.links.rss;

import org.springframework.stereotype.Component;
import run.halo.links.security.SafeUrlFetcher;

@Component
public class LinkFeedFetcher {

    public SafeUrlFetcher.FetchResult fetchHtml(String websiteUrl) {
        return SafeUrlFetcher.fetch(websiteUrl, SafeUrlFetcher.FetchOptions.html(websiteUrl));
    }

    public SafeUrlFetcher.FetchResult fetchFeed(String feedUrl, String etag, String lastModified) {
        return SafeUrlFetcher.fetch(feedUrl,
            SafeUrlFetcher.FetchOptions.feed(feedUrl, etag, lastModified));
    }
}
