package run.halo.links.rss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URL;
import java.time.Instant;
import java.util.List;
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
        when(itemStore.countByLinkName("link-a")).thenReturn(1L);
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
        assertThat(status.getEffectiveFeedUrl()).isEqualTo("https://example.com/feed.xml");
        assertThat(status.getLastError()).isNull();
        assertThat(status.getFailureCount()).isZero();
        assertThat(status.getEtag()).isEqualTo("\"feed-v1\"");
        assertThat(status.getItemCount()).isEqualTo(1);
        assertThat(status.getLatestPublishedAt()).isEqualTo(Instant.parse("2026-05-20T10:00:00Z"));
        verify(retentionService).enforceForLink(eq("link-a"), any(LinkFeedRetentionPolicy.class));
    }

    @Test
    void shouldUpdateFailureStatusWhenRefreshFails() {
        ReactiveExtensionClient client = mock(ReactiveExtensionClient.class);
        LinkFeedItemStore itemStore = mock(LinkFeedItemStore.class);
        LinkFeedRetentionService retentionService = mock(LinkFeedRetentionService.class);
        LinkFeedFetcher feedFetcher = mock(LinkFeedFetcher.class);
        DefaultLinkFeedService service =
            new DefaultLinkFeedService(client, itemStore, retentionService, feedFetcher);
        Link link = rssLink("link-a", "https://example.com/feed.xml");
        Link.RssStatus status = new Link.RssStatus();
        status.setFailureCount(2);
        link.getStatus().setRss(status);

        when(client.fetch(Link.class, "link-a")).thenReturn(Mono.just(link));
        when(client.update(any(Link.class))).thenAnswer(invocation ->
            Mono.just(invocation.getArgument(0)));
        when(feedFetcher.fetchFeed(eq("https://example.com/feed.xml"), any(), any()))
            .thenThrow(new ServerErrorException("URL blocked for security reasons",
                new IllegalArgumentException("private")));

        StepVerifier.create(service.refresh("link-a"))
            .expectError(ServerErrorException.class)
            .verify();

        Link.RssStatus updatedStatus = link.getStatus().getRss();
        assertThat(updatedStatus.getEffectiveFeedUrl()).isEqualTo("https://example.com/feed.xml");
        assertThat(updatedStatus.getFailureCount()).isEqualTo(3);
        assertThat(updatedStatus.getLastError()).contains("URL blocked");
        assertThat(updatedStatus.getLastFetchedAt()).isNotNull();
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
        when(itemStore.countByLinkName("link-a")).thenReturn(2L);
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
        status.setEtag("\"feed-v1\"");
        status.setLastModified("Wed, 20 May 2026 10:00:00 GMT");
        link.getStatus().setRss(status);

        when(client.fetch(Link.class, "link-a")).thenReturn(Mono.just(link));
        when(client.update(any(Link.class))).thenAnswer(invocation ->
            Mono.just(invocation.getArgument(0)));
        when(itemStore.countByLinkName("link-a")).thenReturn(0L, 1L);
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

    private static Link rssLink(String name, String feedUrl) {
        Link link = new Link();
        Metadata metadata = new Metadata();
        metadata.setName(name);
        link.setMetadata(metadata);
        Link.LinkSpec spec = new Link.LinkSpec();
        Link.RssSpec rss = new Link.RssSpec();
        rss.setEnabled(true);
        rss.setFeedUrl(feedUrl);
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
