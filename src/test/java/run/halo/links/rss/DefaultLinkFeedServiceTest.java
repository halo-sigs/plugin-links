package run.halo.links.rss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URL;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ServerErrorException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.links.extension.Link;
import run.halo.links.security.SafeUrlFetcher;

class DefaultLinkFeedServiceTest {

    @Test
    void shouldDiscoverHaloDefaultRssBeforeHtmlDiscovery() throws Exception {
        ReactiveExtensionClient client = mock(ReactiveExtensionClient.class);
        LinkFeedItemStore itemStore = mock(LinkFeedItemStore.class);
        LinkFeedRetentionService retentionService = mock(LinkFeedRetentionService.class);
        LinkFeedFetcher feedFetcher = mock(LinkFeedFetcher.class);
        DefaultLinkFeedService service =
            new DefaultLinkFeedService(client, itemStore, retentionService, feedFetcher);

        when(feedFetcher.fetchFeed(eq("https://example.com/rss.xml"), isNull(), isNull()))
            .thenReturn(feedResult("https://example.com/rss.xml", 200, feedXml()));
        when(feedFetcher.fetchFeed(eq("https://example.com/feed/moments/rss.xml"), isNull(),
            isNull())).thenReturn(feedResult("https://example.com/feed/moments/rss.xml",
            404, ""));

        StepVerifier.create(service.discover("https://example.com/about"))
            .assertNext(result -> assertThat(result.getFeedUrls())
                .containsExactly("https://example.com/rss.xml"))
            .verifyComplete();

        verify(feedFetcher).fetchFeed(eq("https://example.com/rss.xml"), isNull(), isNull());
        verify(feedFetcher).fetchFeed(eq("https://example.com/feed/moments/rss.xml"), isNull(),
            isNull());
        verify(feedFetcher, never()).fetchHtml(anyString());
    }

    @Test
    void shouldDiscoverBothHaloDefaultFeeds() throws Exception {
        ReactiveExtensionClient client = mock(ReactiveExtensionClient.class);
        LinkFeedItemStore itemStore = mock(LinkFeedItemStore.class);
        LinkFeedRetentionService retentionService = mock(LinkFeedRetentionService.class);
        LinkFeedFetcher feedFetcher = mock(LinkFeedFetcher.class);
        DefaultLinkFeedService service =
            new DefaultLinkFeedService(client, itemStore, retentionService, feedFetcher);

        when(feedFetcher.fetchFeed(eq("https://example.com/rss.xml"), isNull(), isNull()))
            .thenReturn(feedResult("https://example.com/rss.xml", 200, feedXml()));
        when(feedFetcher.fetchFeed(eq("https://example.com/feed/moments/rss.xml"), isNull(),
            isNull())).thenReturn(feedResult("https://example.com/feed/moments/rss.xml",
            200, feedXml()));

        StepVerifier.create(service.discover("https://example.com"))
            .assertNext(result -> assertThat(result.getFeedUrls())
                .containsExactly("https://example.com/rss.xml",
                    "https://example.com/feed/moments/rss.xml"))
            .verifyComplete();

        verify(feedFetcher, never()).fetchHtml(anyString());
    }

    @Test
    void shouldFallbackToHtmlDiscoveryWhenHaloDefaultFeedsAreUnavailable() throws Exception {
        ReactiveExtensionClient client = mock(ReactiveExtensionClient.class);
        LinkFeedItemStore itemStore = mock(LinkFeedItemStore.class);
        LinkFeedRetentionService retentionService = mock(LinkFeedRetentionService.class);
        LinkFeedFetcher feedFetcher = mock(LinkFeedFetcher.class);
        DefaultLinkFeedService service =
            new DefaultLinkFeedService(client, itemStore, retentionService, feedFetcher);
        String html = """
            <!doctype html>
            <html>
              <head>
                <link rel="alternate" type="application/rss+xml" href="/feed.xml">
              </head>
            </html>
            """;

        when(feedFetcher.fetchFeed(eq("https://example.com/rss.xml"), isNull(), isNull()))
            .thenReturn(feedResult("https://example.com/rss.xml", 404, ""));
        when(feedFetcher.fetchFeed(eq("https://example.com/feed/moments/rss.xml"), isNull(),
            isNull())).thenReturn(feedResult("https://example.com/feed/moments/rss.xml",
            200, "<html></html>"));
        when(feedFetcher.fetchHtml("https://example.com/blog"))
            .thenReturn(htmlResult("https://example.com/blog", 200, html));

        StepVerifier.create(service.discover("https://example.com/blog"))
            .assertNext(result -> assertThat(result.getFeedUrls())
                .containsExactly("https://example.com/feed.xml"))
            .verifyComplete();
    }

    @Test
    void shouldDeriveHaloDefaultFeedCandidatesFromWebsiteOrigin() {
        assertThat(DefaultLinkFeedService.haloDefaultFeedCandidates(
            "https://example.com:8443/posts/hello?preview=true"))
            .containsExactly("https://example.com:8443/rss.xml",
                "https://example.com:8443/feed/moments/rss.xml");
    }

    @Test
    void shouldCacheItemsAndUpdateSuccessStatus() throws Exception {
        ReactiveExtensionClient client = mock(ReactiveExtensionClient.class);
        LinkFeedItemStore itemStore = mock(LinkFeedItemStore.class);
        LinkFeedRetentionService retentionService = mock(LinkFeedRetentionService.class);
        LinkFeedFetcher feedFetcher = mock(LinkFeedFetcher.class);
        DefaultLinkFeedService service =
            new DefaultLinkFeedService(client, itemStore, retentionService, feedFetcher);
        Link link = rssLink("link-a", "https://example.com/feed.xml");

        when(client.fetch(Link.class, "link-a")).thenReturn(Mono.just(link));
        when(client.update(any(Link.class))).thenAnswer(invocation ->
            Mono.just(invocation.getArgument(0)));
        when(itemStore.upsertAll(anyList())).thenReturn(1);
        when(itemStore.countByLinkNameAndFeedUrl("link-a", "https://example.com/feed.xml"))
            .thenReturn(1L);
        when(feedFetcher.fetchFeed(eq("https://example.com/feed.xml"), any(), any()))
            .thenReturn(new SafeUrlFetcher.FetchResult(
                new URL("https://example.com/feed.xml"),
                200,
                feedXml(),
                null,
                "\"feed-v1\"",
                "Wed, 20 May 2026 10:00:00 GMT"
            ));

        StepVerifier.create(service.refresh("link-a"))
            .assertNext(result -> {
                assertThat(result.getLinkName()).isEqualTo("link-a");
                assertThat(result.getFetchedItemCount()).isEqualTo(1);
                assertThat(result.getItemCount()).isEqualTo(1);
            })
            .verifyComplete();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LinkFeedItem>> itemsCaptor = ArgumentCaptor.forClass(List.class);
        verify(itemStore).upsertAll(itemsCaptor.capture());
        assertThat(itemsCaptor.getValue())
            .singleElement()
            .satisfies(item -> {
                assertThat(item.getTitle()).isEqualTo("Hello");
                assertThat(item.getSummary()).isEqualTo("Summary");
            });

        Link.RssStatus status = link.getStatus().getRss();
        assertThat(status.getLastError()).isNull();
        assertThat(status.getFailureCount()).isZero();
        assertThat(status.getItemCount()).isEqualTo(1);
        assertThat(status.getLatestPublishedAt()).isEqualTo(Instant.parse("2026-05-20T10:00:00Z"));
        assertThat(status.getFeeds())
            .singleElement()
            .satisfies(feedStatus -> {
                assertThat(feedStatus.getUrl()).isEqualTo("https://example.com/feed.xml");
                assertThat(feedStatus.getEtag()).isEqualTo("\"feed-v1\"");
                assertThat(feedStatus.getFailureCount()).isZero();
                assertThat(feedStatus.getItemCount()).isEqualTo(1);
            });
        verify(retentionService).enforceForLink(eq("link-a"), any(LinkFeedRetentionPolicy.class));
    }

    @Test
    void shouldUpdatePerFeedFailureStatusWhenRefreshFails() {
        ReactiveExtensionClient client = mock(ReactiveExtensionClient.class);
        LinkFeedItemStore itemStore = mock(LinkFeedItemStore.class);
        LinkFeedRetentionService retentionService = mock(LinkFeedRetentionService.class);
        LinkFeedFetcher feedFetcher = mock(LinkFeedFetcher.class);
        DefaultLinkFeedService service =
            new DefaultLinkFeedService(client, itemStore, retentionService, feedFetcher);
        Link link = rssLink("link-a", "https://example.com/feed.xml");
        Link.RssStatus status = new Link.RssStatus();
        status.setFailureCount(2);
        Link.RssFeedStatus feedStatus = new Link.RssFeedStatus();
        feedStatus.setUrl("https://example.com/feed.xml");
        feedStatus.setFailureCount(2);
        status.setFeeds(List.of(feedStatus));
        link.getStatus().setRss(status);

        when(client.fetch(Link.class, "link-a")).thenReturn(Mono.just(link));
        when(client.update(any(Link.class))).thenAnswer(invocation ->
            Mono.just(invocation.getArgument(0)));
        when(itemStore.countByLinkNameAndFeedUrl("link-a", "https://example.com/feed.xml"))
            .thenReturn(0L);
        when(feedFetcher.fetchFeed(eq("https://example.com/feed.xml"), any(), any()))
            .thenThrow(new ServerErrorException("URL blocked for security reasons",
                new IllegalArgumentException("private")));

        StepVerifier.create(service.refresh("link-a"))
            .assertNext(result -> {
                assertThat(result.isPartialFailure()).isFalse();
                assertThat(result.getFeeds())
                    .singleElement()
                    .satisfies(feed -> assertThat(feed.getError()).contains("URL blocked"));
            })
            .verifyComplete();

        Link.RssStatus updatedStatus = link.getStatus().getRss();
        assertThat(updatedStatus.getFailureCount()).isEqualTo(3);
        assertThat(updatedStatus.getLastError()).contains("URL blocked");
        assertThat(updatedStatus.getLastFetchedAt()).isNotNull();
        assertThat(updatedStatus.getFeeds())
            .singleElement()
            .satisfies(updatedFeed -> {
                assertThat(updatedFeed.getUrl()).isEqualTo("https://example.com/feed.xml");
                assertThat(updatedFeed.getFailureCount()).isEqualTo(3);
                assertThat(updatedFeed.getLastError()).contains("URL blocked");
            });
    }

    @Test
    void shouldPassItemStateFiltersWhenListingItems() {
        ReactiveExtensionClient client = mock(ReactiveExtensionClient.class);
        LinkFeedItemStore itemStore = mock(LinkFeedItemStore.class);
        LinkFeedRetentionService retentionService = mock(LinkFeedRetentionService.class);
        LinkFeedFetcher feedFetcher = mock(LinkFeedFetcher.class);
        DefaultLinkFeedService service =
            new DefaultLinkFeedService(client, itemStore, retentionService, feedFetcher);
        when(itemStore.listRecent(any(LinkFeedItemQuery.class))).thenReturn(List.of());

        LinkFeedItemQuery query = new LinkFeedItemQuery();
        query.setRead(false);
        query.setFavorite(true);
        query.setReadLater(true);
        service.listItems(query);

        ArgumentCaptor<LinkFeedItemQuery> queryCaptor =
            ArgumentCaptor.forClass(LinkFeedItemQuery.class);
        verify(itemStore).listRecent(queryCaptor.capture());
        assertThat(queryCaptor.getValue())
            .satisfies(actual -> {
                assertThat(actual.getRead()).isFalse();
                assertThat(actual.getFavorite()).isTrue();
                assertThat(actual.getReadLater()).isTrue();
            });
    }

    @Test
    void shouldCacheHaloGeneratedRssItems() throws Exception {
        ReactiveExtensionClient client = mock(ReactiveExtensionClient.class);
        LinkFeedItemStore itemStore = mock(LinkFeedItemStore.class);
        LinkFeedRetentionService retentionService = mock(LinkFeedRetentionService.class);
        LinkFeedFetcher feedFetcher = mock(LinkFeedFetcher.class);
        DefaultLinkFeedService service =
            new DefaultLinkFeedService(client, itemStore, retentionService, feedFetcher);
        Link link = rssLink("link-a", "https://ryanc.cc/rss.xml");

        when(client.fetch(Link.class, "link-a")).thenReturn(Mono.just(link));
        when(client.update(any(Link.class))).thenAnswer(invocation ->
            Mono.just(invocation.getArgument(0)));
        when(itemStore.upsertAll(anyList())).thenReturn(2);
        when(itemStore.countByLinkNameAndFeedUrl("link-a", "https://ryanc.cc/rss.xml"))
            .thenReturn(2L);
        when(feedFetcher.fetchFeed(eq("https://ryanc.cc/rss.xml"), any(), any()))
            .thenReturn(new SafeUrlFetcher.FetchResult(
                new URL("https://ryanc.cc/rss.xml"),
                200,
                haloFeedXml(),
                null,
                null,
                null
            ));

        StepVerifier.create(service.refresh("link-a"))
            .assertNext(result -> {
                assertThat(result.getFetchedItemCount()).isEqualTo(2);
                assertThat(result.getItemCount()).isEqualTo(2);
            })
            .verifyComplete();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LinkFeedItem>> itemsCaptor = ArgumentCaptor.forClass(List.class);
        verify(itemStore).upsertAll(itemsCaptor.capture());
        assertThat(itemsCaptor.getValue())
            .extracting(LinkFeedItem::getTitle)
            .containsExactly("为 Docusaurus 添加 Shiki 代码高亮支持", "内容助手插件 - Word 导入演示");
    }

    @Test
    void shouldSkipConditionalHeadersWhenLocalFeedCacheIsEmpty() throws Exception {
        ReactiveExtensionClient client = mock(ReactiveExtensionClient.class);
        LinkFeedItemStore itemStore = mock(LinkFeedItemStore.class);
        LinkFeedRetentionService retentionService = mock(LinkFeedRetentionService.class);
        LinkFeedFetcher feedFetcher = mock(LinkFeedFetcher.class);
        DefaultLinkFeedService service =
            new DefaultLinkFeedService(client, itemStore, retentionService, feedFetcher);
        Link link = rssLink("link-a", "https://example.com/feed.xml");
        Link.RssStatus status = new Link.RssStatus();
        Link.RssFeedStatus feedStatus = new Link.RssFeedStatus();
        feedStatus.setUrl("https://example.com/feed.xml");
        feedStatus.setEtag("\"feed-v1\"");
        feedStatus.setLastModified("Wed, 20 May 2026 10:00:00 GMT");
        status.setFeeds(List.of(feedStatus));
        link.getStatus().setRss(status);

        when(client.fetch(Link.class, "link-a")).thenReturn(Mono.just(link));
        when(client.update(any(Link.class))).thenAnswer(invocation ->
            Mono.just(invocation.getArgument(0)));
        when(itemStore.countByLinkNameAndFeedUrl("link-a", "https://example.com/feed.xml"))
            .thenReturn(0L, 1L);
        when(itemStore.upsertAll(anyList())).thenReturn(1);
        when(feedFetcher.fetchFeed(eq("https://example.com/feed.xml"), any(), any()))
            .thenReturn(new SafeUrlFetcher.FetchResult(
                new URL("https://example.com/feed.xml"),
                200,
                feedXml(),
                null,
                "\"feed-v1\"",
                "Wed, 20 May 2026 10:00:00 GMT"
            ));

        StepVerifier.create(service.refresh("link-a"))
            .assertNext(result -> assertThat(result.getItemCount()).isEqualTo(1))
            .verifyComplete();

        verify(feedFetcher).fetchFeed(eq("https://example.com/feed.xml"), isNull(), isNull());
    }

    @Test
    void shouldRefreshMultipleFeedUrlsWithPartialFailureIsolation() throws Exception {
        ReactiveExtensionClient client = mock(ReactiveExtensionClient.class);
        LinkFeedItemStore itemStore = mock(LinkFeedItemStore.class);
        LinkFeedRetentionService retentionService = mock(LinkFeedRetentionService.class);
        LinkFeedFetcher feedFetcher = mock(LinkFeedFetcher.class);
        DefaultLinkFeedService service =
            new DefaultLinkFeedService(client, itemStore, retentionService, feedFetcher);
        Link link = rssLink("link-a", "https://example.com/feed.xml",
            "https://example.com/comments.xml");

        when(client.fetch(Link.class, "link-a")).thenReturn(Mono.just(link));
        when(client.update(any(Link.class))).thenAnswer(invocation ->
            Mono.just(invocation.getArgument(0)));
        when(itemStore.upsertAll(anyList())).thenReturn(1);
        when(itemStore.countByLinkNameAndFeedUrl(eq("link-a"), anyString())).thenReturn(1L);
        when(feedFetcher.fetchFeed(eq("https://example.com/feed.xml"), any(), any()))
            .thenReturn(new SafeUrlFetcher.FetchResult(
                new URL("https://example.com/feed.xml"),
                200,
                feedXml(),
                null,
                "\"feed-v1\"",
                null
            ));
        when(feedFetcher.fetchFeed(eq("https://example.com/comments.xml"), any(), any()))
            .thenThrow(new ServerErrorException("URL blocked for security reasons",
                new IllegalArgumentException("private")));

        StepVerifier.create(service.refresh("link-a"))
            .assertNext(result -> {
                assertThat(result.isPartialFailure()).isTrue();
                assertThat(result.getFetchedItemCount()).isEqualTo(1);
                assertThat(result.getItemCount()).isEqualTo(2);
                assertThat(result.getFeeds())
                    .extracting(LinkFeedRefreshResult.FeedResult::getUrl)
                    .containsExactly("https://example.com/feed.xml",
                        "https://example.com/comments.xml");
                assertThat(result.getFeeds().get(1).getError()).contains("URL blocked");
            })
            .verifyComplete();

        Link.RssStatus status = link.getStatus().getRss();
        assertThat(status.getLastError()).contains("Failed to refresh 1 RSS feed URL");
        assertThat(status.getFailureCount()).isZero();
        assertThat(status.getItemCount()).isEqualTo(2);
        assertThat(status.getFeeds())
            .hasSize(2)
            .extracting(Link.RssFeedStatus::getUrl)
            .containsExactly("https://example.com/feed.xml", "https://example.com/comments.xml");
    }

    @Test
    void shouldRejectEnabledLinkWithoutFeedUrls() {
        ReactiveExtensionClient client = mock(ReactiveExtensionClient.class);
        LinkFeedItemStore itemStore = mock(LinkFeedItemStore.class);
        LinkFeedRetentionService retentionService = mock(LinkFeedRetentionService.class);
        LinkFeedFetcher feedFetcher = mock(LinkFeedFetcher.class);
        DefaultLinkFeedService service =
            new DefaultLinkFeedService(client, itemStore, retentionService, feedFetcher);
        Link link = rssLink("link-a", " ");

        when(client.fetch(Link.class, "link-a")).thenReturn(Mono.just(link));

        StepVerifier.create(service.refresh("link-a"))
            .expectErrorSatisfies(error -> assertThat(error)
                .hasMessageContaining("RSS is not enabled"))
            .verify();
    }

    @Test
    void shouldScopeStableItemIdByFeedUrl() {
        assertThat(DefaultLinkFeedService.stableItemId("link-a",
            "https://example.com/feed.xml", "post-1"))
            .isNotEqualTo(DefaultLinkFeedService.stableItemId("link-a",
                "https://example.com/comments.xml", "post-1"));
    }

    private static Link rssLink(String name, String... feedUrls) {
        Link link = new Link();
        Metadata metadata = new Metadata();
        metadata.setName(name);
        link.setMetadata(metadata);
        Link.LinkSpec spec = new Link.LinkSpec();
        Link.RssSpec rss = new Link.RssSpec();
        rss.setEnabled(true);
        rss.setFeedUrls(Arrays.asList(feedUrls));
        spec.setRss(rss);
        link.setSpec(spec);
        link.setStatus(new Link.LinkStatus());
        return link;
    }

    private static String feedXml() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
                <title>Example</title>
                <link>https://example.com</link>
                <item>
                  <guid>post-1</guid>
                  <title><![CDATA[<b>Hello</b>]]></title>
                  <link>https://example.com/post-1</link>
                  <description><![CDATA[<p>Summary</p>]]></description>
                  <pubDate>Wed, 20 May 2026 10:00:00 GMT</pubDate>
                </item>
              </channel>
            </rss>
            """;
    }

    private static SafeUrlFetcher.FetchResult feedResult(String url, int statusCode, String body)
        throws Exception {
        return new SafeUrlFetcher.FetchResult(new URL(url), statusCode, body, null, null, null);
    }

    private static SafeUrlFetcher.FetchResult htmlResult(String url, int statusCode, String body)
        throws Exception {
        return new SafeUrlFetcher.FetchResult(new URL(url), statusCode, body,
            Jsoup.parse(body, url), null, null);
    }

    private static String haloFeedXml() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss xmlns:dc="http://purl.org/dc/elements/1.1/"
                 xmlns:atom="http://www.w3.org/2005/Atom"
                 xmlns:media="http://search.yahoo.com/mrss/"
                 version="2.0">
              <channel>
                <title>Ryan Wang's Blog</title>
                <link>https://ryanc.cc</link>
                <atom:link href="https://ryanc.cc/rss.xml" rel="self" type="application/rss+xml"/>
                <description>Ryan Wang's Blog</description>
                <generator>Halo v2.24.0</generator>
                <follow_challenge>
                  <feedId>69290139450912768</feedId>
                  <userId>41706424548048896</userId>
                </follow_challenge>
                <item>
                  <title><![CDATA[为 Docusaurus 添加 Shiki 代码高亮支持]]></title>
                  <link>https://ryanc.cc/archives/docusaurus-shiki</link>
                  <description><![CDATA[
                    <blockquote>
                      <p>本文记录 <a href="https://docs.halo.run/">https://docs.halo.run</a> 集成 Shiki 代码高亮的过程。</p>
                    </blockquote>
                    <pre><code class="language-shellscript">pnpm add @shikijs/rehype shiki -D</code></pre>
                  ]]></description>
                  <guid isPermaLink="false">/archives/docusaurus-shiki</guid>
                  <dc:creator>Ryan Wang</dc:creator>
                  <enclosure url="https://ryanc.cc/apis/api.storage.halo.run/v1alpha1/thumbnails/-/via-uri?uri=%2Fupload%2Fexample.png&amp;size=m" type="image/jpeg" length="58627"/>
                  <pubDate>Wed, 29 Oct 2025 15:24:03 GMT</pubDate>
                </item>
                <item>
                  <title><![CDATA[内容助手插件 - Word 导入演示]]></title>
                  <link>https://ryanc.cc/archives/plugin-content-tools-word-import</link>
                  <description><![CDATA[
                    <p>选择 Word 文档：</p>
                    <figure data-content-type="image" data-position="left">
                      <img src="https://ryanc.cc/apis/api.storage.halo.run/v1alpha1/thumbnails/-/via-uri?uri=%2Fupload%2Fword.png&amp;size=m" width="991px" height="Infinitypx" data-position="left">
                    </figure>
                  ]]></description>
                  <guid isPermaLink="false">/archives/plugin-content-tools-word-import</guid>
                  <dc:creator>Ryan Wang</dc:creator>
                  <pubDate>Fri, 22 May 2026 08:20:14 GMT</pubDate>
                </item>
              </channel>
            </rss>
            """;
    }
}
