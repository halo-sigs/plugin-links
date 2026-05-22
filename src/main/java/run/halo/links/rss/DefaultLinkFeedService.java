package run.halo.links.rss;

import com.rometools.rome.feed.synd.SyndContent;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
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
                    .flatMap(result -> updateSuccessStatus(link, result).thenReturn(result))
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
        storeQuery.setLimit(Math.min(limit + 1, LinkFeedItemQuery.MAX_LIMIT));

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
        var result = feedFetcher.fetchHtml(websiteUrl);
        if (!isSuccess(result.statusCode()) || result.document() == null) {
            return new LinkFeedDiscoveryResult();
        }
        Optional<String> feedUrl = result.document()
            .select("link[rel~=(?i)alternate]")
            .stream()
            .filter(DefaultLinkFeedService::isFeedLink)
            .map(element -> element.absUrl("href"))
            .filter(StringUtils::hasText)
            .findFirst();
        return new LinkFeedDiscoveryResult(feedUrl.orElse(null));
    }

    private LinkFeedRefreshResult refreshBlocking(Link link) throws Exception {
        String linkName = link.getMetadata().getName();
        String feedUrl = link.getSpec().getRss().getFeedUrl();
        Link.RssStatus previousStatus = Optional.ofNullable(link.getStatus().getRss())
            .orElse(new Link.RssStatus());
        Instant fetchedAt = Instant.now();

        var fetchResult = feedFetcher.fetchFeed(feedUrl, previousStatus.getEtag(),
            previousStatus.getLastModified());

        LinkFeedRefreshResult result = new LinkFeedRefreshResult();
        result.setLinkName(linkName);
        result.setFeedUrl(feedUrl);
        result.setFetchedAt(fetchedAt);
        result.setNotModified(fetchResult.statusCode() == 304);
        result.setEtag(fetchResult.etag());
        result.setLastModified(fetchResult.lastModified());

        if (result.isNotModified()) {
            result.setItemCount(itemStore.countByLinkName(linkName));
            result.setLatestPublishedAt(previousStatus.getLatestPublishedAt());
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
        retentionService.enforceForLink(linkName, LinkFeedRetentionPolicy.defaults());

        result.setFetchedItemCount(items.size());
        result.setItemCount(itemStore.countByLinkName(linkName));
        result.setLatestPublishedAt(items.stream()
            .map(LinkFeedItem::getPublishedAt)
            .filter(Objects::nonNull)
            .max(Comparator.naturalOrder())
            .orElse(previousStatus.getLatestPublishedAt()));

        return result;
    }

    private Mono<Link> updateSuccessStatus(Link link, LinkFeedRefreshResult result) {
        Link.RssStatus status = Optional.ofNullable(link.getStatus().getRss())
            .orElseGet(Link.RssStatus::new);
        status.setEffectiveFeedUrl(result.getFeedUrl());
        status.setLastFetchedAt(result.getFetchedAt());
        status.setLastSuccessAt(result.getFetchedAt());
        status.setLastError(null);
        status.setFailureCount(0);
        if (StringUtils.hasText(result.getEtag())) {
            status.setEtag(result.getEtag());
        }
        if (StringUtils.hasText(result.getLastModified())) {
            status.setLastModified(result.getLastModified());
        }
        status.setLatestPublishedAt(result.getLatestPublishedAt());
        status.setItemCount(result.getItemCount());
        link.getStatus().setRss(status);
        return client.update(link);
    }

    private Mono<Link> updateFailureStatus(Link link, Throwable error) {
        Link.RssStatus status = Optional.ofNullable(link.getStatus().getRss())
            .orElseGet(Link.RssStatus::new);
        status.setEffectiveFeedUrl(link.getSpec().getRss().getFeedUrl());
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
        item.setId(sha256(linkName + "|" + identity));
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
        item.setFetchedAt(fetchedAt);
        item.setContentHash(sha256(firstText(title, "") + "|" + firstText(summary, "")));
        item.setRead(false);
        return item;
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
            && StringUtils.hasText(link.getSpec().getRss().getFeedUrl());
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
