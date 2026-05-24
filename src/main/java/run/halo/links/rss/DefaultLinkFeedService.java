package run.halo.links.rss;

import com.rometools.rome.feed.synd.SyndContent;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.links.extension.Link;

@Component
@RequiredArgsConstructor
public class DefaultLinkFeedService implements LinkFeedService {

    private static final int MAX_ITEMS_PER_FETCH = 100;
    private static final int MAX_SUMMARY_LENGTH = 500;
    private static final List<String> HALO_DEFAULT_FEED_PATHS =
        List.of("/rss.xml", "/feed/moments/rss.xml");

    private final ReactiveExtensionClient client;
    private final LinkFeedItemStore itemStore;
    private final LinkFeedRetentionService retentionService;
    private final LinkFeedFetcher feedFetcher;

    @Override
    public Mono<LinkFeedDiscoveryResult> discover(String websiteUrl) {
        return Mono.fromCallable(() -> discoverBlocking(websiteUrl))
            .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<LinkFeedRefreshResult> refresh(String linkName) {
        return client.fetch(Link.class, linkName)
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Link not found: " + linkName)))
            .flatMap(link -> {
                if (!isRssEnabled(link)) {
                    return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "RSS is not enabled for this link."));
                }
                return Mono.fromCallable(() -> refreshBlocking(link))
                    .subscribeOn(Schedulers.boundedElastic())
                    .flatMap(result -> updateStatus(link, result).thenReturn(result))
                    .onErrorResume(error -> updateFailureStatus(link, error)
                        .then(Mono.error(error)));
            });
    }

    @Override
    public LinkFeedItemPage listItems(LinkFeedItemQuery query) {
        LinkFeedItemQuery requested = Optional.ofNullable(query).orElse(new LinkFeedItemQuery());
        int limit = requested.normalizedLimit();
        LinkFeedItemQuery storeQuery = new LinkFeedItemQuery();
        storeQuery.setLinkName(requested.getLinkName());
        storeQuery.setBeforePublishedAt(requested.getBeforePublishedAt());
        storeQuery.setBeforeId(requested.getBeforeId());
        storeQuery.setRead(requested.getRead());
        storeQuery.setFavorite(requested.getFavorite());
        storeQuery.setReadLater(requested.getReadLater());
        storeQuery.setLimit(limit + 1);

        List<LinkFeedItem> items = itemStore.listRecent(storeQuery);
        boolean hasNext = items.size() > limit;
        List<LinkFeedItem> pageItems = hasNext ? items.subList(0, limit) : items;
        LinkFeedItem last = pageItems.isEmpty() ? null : pageItems.get(pageItems.size() - 1);
        String nextBeforePublishedAt = last == null || last.getPublishedAt() == null
            ? null
            : last.getPublishedAt().toString();
        String nextBeforeId = last == null ? null : last.getId();
        return new LinkFeedItemPage(List.copyOf(pageItems), nextBeforePublishedAt, nextBeforeId,
            hasNext);
    }

    private LinkFeedDiscoveryResult discoverBlocking(String websiteUrl) {
        List<String> haloDefaultFeedUrls = discoverHaloDefaultFeedUrls(websiteUrl);
        if (!haloDefaultFeedUrls.isEmpty()) {
            return new LinkFeedDiscoveryResult(haloDefaultFeedUrls);
        }
        var result = feedFetcher.fetchHtml(websiteUrl);
        if (!isSuccess(result.statusCode()) || result.document() == null) {
            return new LinkFeedDiscoveryResult();
        }
        List<String> feedUrls = result.document()
            .select("link[rel~=(?i)alternate]")
            .stream()
            .filter(DefaultLinkFeedService::isFeedLink)
            .map(element -> element.absUrl("href"))
            .filter(StringUtils::hasText)
            .map(String::trim)
            .distinct()
            .toList();
        return new LinkFeedDiscoveryResult(feedUrls);
    }

    private List<String> discoverHaloDefaultFeedUrls(String websiteUrl) {
        return haloDefaultFeedCandidates(websiteUrl)
            .stream()
            .filter(this::isParseableFeed)
            .distinct()
            .toList();
    }

    static List<String> haloDefaultFeedCandidates(String websiteUrl) {
        if (!StringUtils.hasText(websiteUrl)) {
            return List.of();
        }
        try {
            URI websiteUri = new URI(websiteUrl.trim());
            if (!StringUtils.hasText(websiteUri.getScheme())
                || !StringUtils.hasText(websiteUri.getHost())) {
                return List.of();
            }
            URI origin = new URI(websiteUri.getScheme(), null, websiteUri.getHost(),
                websiteUri.getPort(), null, null, null);
            return HALO_DEFAULT_FEED_PATHS.stream()
                .map(path -> origin.resolve(path).toString())
                .toList();
        } catch (URISyntaxException e) {
            return List.of();
        }
    }

    private boolean isParseableFeed(String feedUrl) {
        try {
            var result = feedFetcher.fetchFeed(feedUrl, null, null);
            if (!isSuccess(result.statusCode()) || !StringUtils.hasText(result.body())) {
                return false;
            }
            new SyndFeedInput().build(new StringReader(result.body()));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private LinkFeedRefreshResult refreshBlocking(Link link) throws Exception {
        String linkName = link.getMetadata().getName();
        List<String> feedUrls = rssFeedUrls(link);
        if (feedUrls.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "RSS feed URLs are required for this link.");
        }
        Link.RssStatus previousStatus = Optional.ofNullable(link.getStatus().getRss())
            .orElse(new Link.RssStatus());
        Instant fetchedAt = Instant.now();
        Map<String, Link.RssFeedStatus> previousFeedStatuses = feedStatusByUrl(previousStatus);

        LinkFeedRefreshResult result = new LinkFeedRefreshResult();
        result.setLinkName(linkName);
        result.setFetchedAt(fetchedAt);

        List<LinkFeedRefreshResult.FeedResult> feedResults = new ArrayList<>();
        for (String feedUrl : feedUrls) {
            Link.RssFeedStatus previousFeedStatus = previousFeedStatuses.get(feedUrl);
            try {
                feedResults.add(refreshFeedBlocking(linkName, feedUrl, previousFeedStatus,
                    fetchedAt));
            } catch (Exception e) {
                feedResults.add(failedFeedResult(linkName, feedUrl, previousFeedStatus,
                    fetchedAt, e));
            }
        }
        retentionService.enforceForLink(linkName, LinkFeedRetentionPolicy.defaults());

        feedResults.forEach(feedResult -> feedResult.setItemCount(
            itemStore.countByLinkNameAndFeedUrl(linkName, feedResult.getUrl())));
        result.setFeeds(List.copyOf(feedResults));
        result.setFetchedItemCount(feedResults.stream()
            .mapToInt(LinkFeedRefreshResult.FeedResult::getFetchedItemCount)
            .sum());
        result.setItemCount(feedResults.stream()
            .mapToLong(LinkFeedRefreshResult.FeedResult::getItemCount)
            .sum());
        result.setPartialFailure(hasAnyFailure(feedResults) && hasAnySuccess(feedResults));
        result.setLatestPublishedAt(feedResults.stream()
            .map(LinkFeedRefreshResult.FeedResult::getLatestPublishedAt)
            .filter(Objects::nonNull)
            .max(Comparator.naturalOrder())
            .orElse(previousStatus.getLatestPublishedAt()));

        return result;
    }

    private LinkFeedRefreshResult.FeedResult refreshFeedBlocking(String linkName, String feedUrl,
        Link.RssFeedStatus previousStatus, Instant fetchedAt) throws Exception {
        long cachedItemCount = itemStore.countByLinkNameAndFeedUrl(linkName, feedUrl);
        var fetchResult = feedFetcher.fetchFeed(feedUrl,
            cachedItemCount > 0 && previousStatus != null ? previousStatus.getEtag() : null,
            cachedItemCount > 0 && previousStatus != null ? previousStatus.getLastModified()
                : null);

        LinkFeedRefreshResult.FeedResult result = new LinkFeedRefreshResult.FeedResult();
        result.setUrl(feedUrl);
        result.setFetchedAt(fetchedAt);
        result.setNotModified(fetchResult.statusCode() == 304);
        result.setEtag(fetchResult.etag());
        result.setLastModified(fetchResult.lastModified());

        if (result.isNotModified()) {
            result.setItemCount(cachedItemCount);
            result.setLatestPublishedAt(previousStatus == null ? null
                : previousStatus.getLatestPublishedAt());
            return result;
        }
        if (!isSuccess(fetchResult.statusCode())) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "Feed responded with HTTP " + fetchResult.statusCode());
        }

        SyndFeed feed = new SyndFeedInput().build(new StringReader(fetchResult.body()));
        List<LinkFeedItem> items = feed.getEntries()
            .stream()
            .limit(MAX_ITEMS_PER_FETCH)
            .map(entry -> toItem(linkName, feedUrl, entry, fetchedAt))
            .filter(Objects::nonNull)
            .toList();

        itemStore.upsertAll(items);
        result.setFetchedItemCount(items.size());
        result.setItemCount(itemStore.countByLinkNameAndFeedUrl(linkName, feedUrl));
        result.setLatestPublishedAt(items.stream()
            .map(LinkFeedItem::getPublishedAt)
            .filter(Objects::nonNull)
            .max(Comparator.naturalOrder())
            .orElse(previousStatus == null ? null : previousStatus.getLatestPublishedAt()));
        return result;
    }

    private LinkFeedRefreshResult.FeedResult failedFeedResult(String linkName, String feedUrl,
        Link.RssFeedStatus previousStatus, Instant fetchedAt, Throwable error) {
        LinkFeedRefreshResult.FeedResult result = new LinkFeedRefreshResult.FeedResult();
        result.setUrl(feedUrl);
        result.setFetchedAt(fetchedAt);
        result.setItemCount(itemStore.countByLinkNameAndFeedUrl(linkName, feedUrl));
        result.setLatestPublishedAt(previousStatus == null ? null
            : previousStatus.getLatestPublishedAt());
        result.setError(error.getMessage());
        return result;
    }

    private Mono<Link> updateStatus(Link link, LinkFeedRefreshResult result) {
        Link.RssStatus previousStatus = Optional.ofNullable(link.getStatus().getRss())
            .orElseGet(Link.RssStatus::new);
        Map<String, Link.RssFeedStatus> previousFeedStatuses = feedStatusByUrl(previousStatus);
        Link.RssStatus status = Optional.ofNullable(link.getStatus().getRss())
            .orElseGet(Link.RssStatus::new);
        status.setLastFetchedAt(result.getFetchedAt());
        result.getFeeds()
            .stream()
            .filter(DefaultLinkFeedService::isSuccessful)
            .map(LinkFeedRefreshResult.FeedResult::getFetchedAt)
            .filter(Objects::nonNull)
            .max(Comparator.naturalOrder())
            .ifPresent(status::setLastSuccessAt);
        status.setLastError(aggregateError(result.getFeeds()));
        status.setFailureCount(hasAnySuccess(result.getFeeds()) ? 0
            : Optional.ofNullable(status.getFailureCount()).orElse(0) + 1);
        status.setLatestPublishedAt(result.getLatestPublishedAt());
        status.setItemCount(result.getItemCount());
        status.setFeeds(result.getFeeds()
            .stream()
            .map(feedResult -> toFeedStatus(feedResult,
                previousFeedStatuses.get(feedResult.getUrl())))
            .toList());
        link.getStatus().setRss(status);
        return client.update(link);
    }

    private Mono<Link> updateFailureStatus(Link link, Throwable error) {
        Link.RssStatus status = Optional.ofNullable(link.getStatus().getRss())
            .orElseGet(Link.RssStatus::new);
        status.setLastFetchedAt(Instant.now());
        status.setLastError(error.getMessage());
        status.setFailureCount(Optional.ofNullable(status.getFailureCount()).orElse(0) + 1);
        link.getStatus().setRss(status);
        return client.update(link);
    }

    private static LinkFeedItem toItem(String linkName, String feedUrl, SyndEntry entry,
        Instant fetchedAt) {
        String guid = firstText(entry.getUri(), entry.getLink(), entry.getTitle());
        String url = firstText(entry.getLink(), guid);
        if (!StringUtils.hasText(guid) && !StringUtils.hasText(url)) {
            return null;
        }
        Instant publishedAt = toInstant(entry.getPublishedDate());
        Instant updatedAt = toInstant(entry.getUpdatedDate());
        String title = plainText(entry.getTitle(), 200);
        String summary = plainText(summaryValue(entry), MAX_SUMMARY_LENGTH);
        String identity = StringUtils.hasText(guid) ? guid : url;

        LinkFeedItem item = new LinkFeedItem();
        item.setId(stableItemId(linkName, feedUrl, identity));
        item.setLinkName(linkName);
        item.setFeedUrl(feedUrl);
        item.setGuid(guid);
        item.setUrl(url);
        item.setTitle(title);
        item.setSummary(summary);
        item.setAuthor(plainText(entry.getAuthor(), 120));
        item.setPublishedAt(publishedAt != null ? publishedAt
            : Optional.ofNullable(updatedAt).orElse(fetchedAt));
        item.setUpdatedAt(updatedAt);
        item.setFirstSeenAt(fetchedAt);
        item.setFetchedAt(fetchedAt);
        item.setContentHash(sha256(firstText(title, "") + "|" + firstText(summary, "")));
        item.setRead(false);
        return item;
    }

    static String stableItemId(String linkName, String feedUrl, String identity) {
        return sha256(linkName + "|" + feedUrl + "|" + identity);
    }

    private static List<String> rssFeedUrls(Link link) {
        if (link.getSpec() == null || link.getSpec().getRss() == null
            || link.getSpec().getRss().getFeedUrls() == null) {
            return List.of();
        }
        Map<String, String> normalized = new LinkedHashMap<>();
        link.getSpec().getRss().getFeedUrls()
            .stream()
            .filter(StringUtils::hasText)
            .map(String::trim)
            .forEach(feedUrl -> normalized.putIfAbsent(feedUrl, feedUrl));
        return List.copyOf(normalized.values());
    }

    private static Map<String, Link.RssFeedStatus> feedStatusByUrl(Link.RssStatus status) {
        if (status == null || status.getFeeds() == null) {
            return Map.of();
        }
        Map<String, Link.RssFeedStatus> statuses = new LinkedHashMap<>();
        status.getFeeds()
            .stream()
            .filter(feedStatus -> StringUtils.hasText(feedStatus.getUrl()))
            .forEach(feedStatus -> statuses.putIfAbsent(feedStatus.getUrl(), feedStatus));
        return statuses;
    }

    private static Link.RssFeedStatus toFeedStatus(LinkFeedRefreshResult.FeedResult result,
        Link.RssFeedStatus previousStatus) {
        Link.RssFeedStatus status = new Link.RssFeedStatus();
        status.setUrl(result.getUrl());
        status.setLastFetchedAt(result.getFetchedAt());
        status.setItemCount(result.getItemCount());
        if (!isSuccessful(result)) {
            status.setLastSuccessAt(previousStatus == null ? null : previousStatus.getLastSuccessAt());
            status.setLatestPublishedAt(previousStatus == null ? null
                : previousStatus.getLatestPublishedAt());
            status.setEtag(previousStatus == null ? null : previousStatus.getEtag());
            status.setLastModified(previousStatus == null ? null : previousStatus.getLastModified());
            status.setLastError(result.getError());
            status.setFailureCount(Optional.ofNullable(previousStatus)
                .map(Link.RssFeedStatus::getFailureCount)
                .orElse(0) + 1);
            return status;
        }
        status.setLastSuccessAt(result.getFetchedAt());
        status.setLatestPublishedAt(result.getLatestPublishedAt());
        status.setLastError(null);
        status.setFailureCount(0);
        status.setEtag(nextValidator(result.getEtag(), previousStatus == null ? null
            : previousStatus.getEtag(), result.isNotModified()));
        status.setLastModified(nextValidator(result.getLastModified(), previousStatus == null ? null
            : previousStatus.getLastModified(), result.isNotModified()));
        return status;
    }

    private static String nextValidator(String current, String previous, boolean notModified) {
        if (StringUtils.hasText(current)) {
            return current;
        }
        return notModified ? previous : null;
    }

    private static String aggregateError(List<LinkFeedRefreshResult.FeedResult> feedResults) {
        if (!hasAnyFailure(feedResults)) {
            return null;
        }
        if (hasAnySuccess(feedResults)) {
            long failedCount = feedResults.stream()
                .filter(feedResult -> !isSuccessful(feedResult))
                .count();
            return "Failed to refresh " + failedCount + " RSS feed URL"
                + (failedCount > 1 ? "s" : "") + ".";
        }
        return feedResults.stream()
            .map(LinkFeedRefreshResult.FeedResult::getError)
            .filter(StringUtils::hasText)
            .findFirst()
            .orElse("Failed to refresh RSS feeds.");
    }

    private static boolean hasAnySuccess(List<LinkFeedRefreshResult.FeedResult> feedResults) {
        return feedResults != null && feedResults.stream()
            .anyMatch(DefaultLinkFeedService::isSuccessful);
    }

    private static boolean hasAnyFailure(List<LinkFeedRefreshResult.FeedResult> feedResults) {
        return feedResults != null && feedResults.stream()
            .anyMatch(feedResult -> !isSuccessful(feedResult));
    }

    private static boolean isSuccessful(LinkFeedRefreshResult.FeedResult feedResult) {
        return feedResult != null && !StringUtils.hasText(feedResult.getError());
    }

    private static boolean isFeedLink(Element element) {
        String type = element.attr("type").toLowerCase();
        String href = element.attr("href");
        return StringUtils.hasText(href)
            && (type.contains("rss") || type.contains("atom") || type.contains("xml"));
    }

    private static boolean isRssEnabled(Link link) {
        return link.getSpec() != null
            && link.getSpec().getRss() != null
            && Boolean.TRUE.equals(link.getSpec().getRss().getEnabled())
            && !rssFeedUrls(link).isEmpty();
    }

    private static String summaryValue(SyndEntry entry) {
        SyndContent description = entry.getDescription();
        if (description != null && StringUtils.hasText(description.getValue())) {
            return description.getValue();
        }
        List<SyndContent> contents = entry.getContents();
        if (contents == null) {
            return null;
        }
        return contents
            .stream()
            .map(SyndContent::getValue)
            .filter(StringUtils::hasText)
            .findFirst()
            .orElse(null);
    }

    private static Instant toInstant(Date date) {
        return date == null ? null : date.toInstant();
    }

    private static String plainText(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String text = Jsoup.parse(value).text().trim();
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }

    private static String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static boolean isSuccess(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }
}
