package run.halo.links.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.server.ServerErrorException;
import reactor.core.publisher.Mono;

class SafeUrlFetcherTest {

    @AfterEach
    void tearDown() {
        SafeUrlFetcher.setExchangeFunctionForTesting(null);
    }

    @Test
    void shouldBlockPrivateFeedUrlBeforeConnecting() {
        assertThatThrownBy(() -> SafeUrlFetcher.fetch("http://127.0.0.1/feed.xml",
            SafeUrlFetcher.FetchOptions.verification("http://example.com", 64, 200)))
            .isInstanceOf(ServerErrorException.class);
    }

    @Test
    void shouldRejectNonHttpScheme() {
        assertThatThrownBy(() -> SafeUrlFetcher.fetch("file:///etc/passwd",
            SafeUrlFetcher.FetchOptions.html("file:///etc/passwd")))
            .isInstanceOf(ServerErrorException.class)
            .hasMessageContaining("blocked");
    }

    @Test
    void shouldFollowRedirectAndPreserveHeaders() {
        List<ClientRequest> requests = new ArrayList<>();
        String startUrl = "http://example.com/feed.xml";
        SafeUrlFetcher.setExchangeFunctionForTesting(request -> {
            requests.add(request);
            if (request.url().getPath().equals("/feed.xml")) {
                return Mono.just(response(HttpStatus.FOUND, "", builder ->
                    builder.header(HttpHeaders.LOCATION, "/final.xml")));
            }
            return Mono.just(response(HttpStatus.OK, "<rss></rss>", builder -> builder
                .header(HttpHeaders.ETAG, "\"v2\"")
                .header(HttpHeaders.LAST_MODIFIED, "Wed, 21 Oct 2015 07:28:00 GMT")));
        });

        SafeUrlFetcher.FetchResult result = SafeUrlFetcher.fetch(startUrl,
            SafeUrlFetcher.FetchOptions.feed(startUrl, "\"v1\"",
                "Tue, 20 Oct 2015 07:28:00 GMT"));

        assertThat(result.url().toExternalForm()).isEqualTo("http://example.com/final.xml");
        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(result.body()).isEqualTo("<rss></rss>");
        assertThat(result.etag()).isEqualTo("\"v2\"");
        assertThat(result.lastModified()).isEqualTo("Wed, 21 Oct 2015 07:28:00 GMT");
        assertThat(requests).hasSize(2);
        ClientRequest firstRequest = requests.getFirst();
        assertThat(firstRequest.url()).isEqualTo(URI.create(startUrl));
        assertThat(firstRequest.headers().getFirst(HttpHeaders.HOST)).isEqualTo("example.com");
        assertThat(firstRequest.headers().getFirst(HttpHeaders.USER_AGENT))
            .isEqualTo(SafeUrlFetcher.USER_AGENT);
        assertThat(firstRequest.headers().getFirst(HttpHeaders.ACCEPT))
            .isEqualTo("application/rss+xml,application/atom+xml,application/xml,text/xml");
        assertThat(firstRequest.headers().getFirst(HttpHeaders.REFERER)).isEqualTo(startUrl);
        assertThat(firstRequest.headers().getFirst(HttpHeaders.IF_NONE_MATCH)).isEqualTo("\"v1\"");
        assertThat(firstRequest.headers().getFirst(HttpHeaders.IF_MODIFIED_SINCE))
            .isEqualTo("Tue, 20 Oct 2015 07:28:00 GMT");
    }

    @Test
    void shouldBlockTooManyRedirects() {
        SafeUrlFetcher.setExchangeFunctionForTesting(request -> Mono.just(
            response(HttpStatus.FOUND, "", builder ->
                builder.header(HttpHeaders.LOCATION, "/next"))));

        assertThatThrownBy(() -> SafeUrlFetcher.fetch("http://example.com/start",
            SafeUrlFetcher.FetchOptions.feed("http://example.com/start", null, null)))
            .isInstanceOf(ServerErrorException.class)
            .hasMessageContaining("Too many redirects");
    }

    @Test
    void shouldRejectResponseOverMaximumBodySize() {
        SafeUrlFetcher.setExchangeFunctionForTesting(request -> Mono.just(
            response(HttpStatus.OK, "abcdef", builder -> {
            })));

        assertThatThrownBy(() -> SafeUrlFetcher.fetch("http://example.com",
            SafeUrlFetcher.FetchOptions.verificationHtml("http://example.com", 5, 1000)))
            .isInstanceOf(ServerErrorException.class)
            .hasMessageContaining("maximum size");
    }

    @Test
    void shouldKeepVerificationStatusWhenResponseOverMaximumBodySize() {
        SafeUrlFetcher.setExchangeFunctionForTesting(request -> Mono.just(
            response(HttpStatus.OK, "abcdef", builder -> {
            })));

        SafeUrlFetcher.FetchResult result = SafeUrlFetcher.fetch("http://example.com",
            SafeUrlFetcher.FetchOptions.verification("http://example.com", 5, 1000));

        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(result.body()).isEmpty();
    }

    @Test
    void shouldPreserveNotModifiedStatusAndHeaders() {
        SafeUrlFetcher.setExchangeFunctionForTesting(request -> Mono.just(
            response(HttpStatus.NOT_MODIFIED, "", builder -> builder
                .header(HttpHeaders.ETAG, "\"v1\"")
                .header(HttpHeaders.LAST_MODIFIED, "Wed, 21 Oct 2015 07:28:00 GMT"))));

        SafeUrlFetcher.FetchResult result = SafeUrlFetcher.fetch("http://example.com/feed.xml",
            SafeUrlFetcher.FetchOptions.feed("http://example.com/feed.xml", "\"v1\"",
                "Wed, 21 Oct 2015 07:28:00 GMT"));

        assertThat(result.statusCode()).isEqualTo(304);
        assertThat(result.body()).isEmpty();
        assertThat(result.etag()).isEqualTo("\"v1\"");
        assertThat(result.lastModified()).isEqualTo("Wed, 21 Oct 2015 07:28:00 GMT");
    }

    @Test
    void shouldParseHtmlDocumentWhenRequested() {
        SafeUrlFetcher.setExchangeFunctionForTesting(request -> Mono.just(
            response(HttpStatus.OK, "<html><head><title>Example</title></head></html>",
                builder -> builder.header(HttpHeaders.CONTENT_TYPE, "text/html; charset=UTF-8"))));

        SafeUrlFetcher.FetchResult result = SafeUrlFetcher.fetch("http://example.com",
            SafeUrlFetcher.FetchOptions.html("http://example.com"));

        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(result.document()).isNotNull();
        assertThat(result.document().title()).isEqualTo("Example");
    }

    private static ClientResponse response(HttpStatus status, String body,
        Consumer<ClientResponse.Builder> customizer) {
        ClientResponse.Builder builder = ClientResponse.create(status);
        customizer.accept(builder);
        return builder.body(body).build();
    }
}
