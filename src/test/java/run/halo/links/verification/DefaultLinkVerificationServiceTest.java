package run.halo.links.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ServerErrorException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.infra.ExternalUrlSupplier;
import run.halo.links.extension.Link;
import run.halo.links.security.SafeUrlFetcher;

@ExtendWith(MockitoExtension.class)
class DefaultLinkVerificationServiceTest {

    @Mock
    ReactiveExtensionClient client;

    @Mock
    ExternalUrlSupplier externalUrlSupplier;

    @Mock
    LinkVerificationFetcher fetcher;

    DefaultLinkVerificationService service;

    @AfterEach
    void tearDown() {
        if (service != null) {
            service.disposeScheduler();
        }
    }

    @Test
    void shouldRecordAccessibleAndFoundBacklink() throws Exception {
        service = service();
        Link link = link("link-a", "https://friend.example.com", "https://friend.example.com/links");
        when(client.fetch(Link.class, "link-a")).thenReturn(Mono.just(link));
        when(client.update(any(Link.class))).thenAnswer(invocation ->
            Mono.just(invocation.getArgument(0)));
        when(externalUrlSupplier.getRaw()).thenReturn(new URL("https://ryanc.cc/blog"));
        when(fetcher.fetchReachability("https://friend.example.com"))
            .thenReturn(fetchResult("https://friend.example.com", 200, ""));
        when(fetcher.fetchBacklinkPage("https://friend.example.com/links"))
            .thenReturn(htmlResult("https://friend.example.com/links", 200, """
                <a href="https://ryanc.cc/blog/links">Ryan</a>
                """));

        StepVerifier.create(service.verifyLink("link-a"))
            .assertNext(updated -> {
                Link.VerificationStatus status = updated.getStatus().getVerification();
                assertThat(status.getAccess().getState()).isEqualTo(Link.AccessState.ACCESSIBLE);
                assertThat(status.getAccess().getFinalUrl()).isEqualTo("https://friend.example.com");
                assertThat(status.getBacklink().getState()).isEqualTo(Link.BacklinkState.FOUND);
                assertThat(status.getBacklink().getTargetUrl()).isEqualTo("https://ryanc.cc/blog");
                assertThat(status.getBacklink().getMatchedUrl())
                    .isEqualTo("https://ryanc.cc/blog/links");
            })
            .verifyComplete();
    }

    @Test
    void shouldRecordInaccessibleLink() throws Exception {
        service = service();
        Link link = link("link-a", "https://friend.example.com", null);
        when(client.fetch(Link.class, "link-a")).thenReturn(Mono.just(link));
        when(client.update(any(Link.class))).thenAnswer(invocation ->
            Mono.just(invocation.getArgument(0)));
        when(fetcher.fetchReachability("https://friend.example.com"))
            .thenReturn(fetchResult("https://friend.example.com", 500, ""));

        StepVerifier.create(service.verifyLink("link-a"))
            .assertNext(updated -> {
                Link.VerificationStatus status = updated.getStatus().getVerification();
                assertThat(status.getAccess().getState()).isEqualTo(Link.AccessState.INACCESSIBLE);
                assertThat(status.getAccess().getStatusCode()).isEqualTo(500);
                assertThat(status.getBacklink().getState()).isEqualTo(Link.BacklinkState.NOT_CONFIGURED);
            })
            .verifyComplete();
    }

    @Test
    void shouldRecordMissingBacklink() throws Exception {
        service = service();
        Link link = link("link-a", "https://friend.example.com", "https://friend.example.com/links");
        when(client.fetch(Link.class, "link-a")).thenReturn(Mono.just(link));
        when(client.update(any(Link.class))).thenAnswer(invocation ->
            Mono.just(invocation.getArgument(0)));
        when(externalUrlSupplier.getRaw()).thenReturn(new URL("https://ryanc.cc"));
        when(fetcher.fetchReachability("https://friend.example.com"))
            .thenReturn(fetchResult("https://friend.example.com", 200, ""));
        when(fetcher.fetchBacklinkPage("https://friend.example.com/links"))
            .thenReturn(htmlResult("https://friend.example.com/links", 200, """
                <a href="https://other.example.com">Other</a>
                """));

        StepVerifier.create(service.verifyLink("link-a"))
            .assertNext(updated -> assertThat(updated.getStatus().getVerification()
                .getBacklink().getState()).isEqualTo(Link.BacklinkState.MISSING))
            .verifyComplete();
    }

    @Test
    void shouldSkipBacklinkFetchWhenNotConfigured() throws Exception {
        service = service();
        Link link = link("link-a", "https://friend.example.com", null);
        when(client.fetch(Link.class, "link-a")).thenReturn(Mono.just(link));
        when(client.update(any(Link.class))).thenAnswer(invocation ->
            Mono.just(invocation.getArgument(0)));
        when(fetcher.fetchReachability("https://friend.example.com"))
            .thenReturn(fetchResult("https://friend.example.com", 200, ""));

        StepVerifier.create(service.verifyLink("link-a"))
            .assertNext(updated -> assertThat(updated.getStatus().getVerification()
                .getBacklink().getState()).isEqualTo(Link.BacklinkState.NOT_CONFIGURED))
            .verifyComplete();

        verify(fetcher, never()).fetchBacklinkPage(any());
    }

    @Test
    void shouldRecordBacklinkFailureWhenExternalUrlIsMissing() throws Exception {
        service = service();
        Link link = link("link-a", "https://friend.example.com", "https://friend.example.com/links");
        when(client.fetch(Link.class, "link-a")).thenReturn(Mono.just(link));
        when(client.update(any(Link.class))).thenAnswer(invocation ->
            Mono.just(invocation.getArgument(0)));
        when(externalUrlSupplier.getRaw()).thenReturn(null);
        when(fetcher.fetchReachability("https://friend.example.com"))
            .thenReturn(fetchResult("https://friend.example.com", 200, ""));

        StepVerifier.create(service.verifyLink("link-a"))
            .assertNext(updated -> {
                Link.BacklinkStatus backlink = updated.getStatus().getVerification().getBacklink();
                assertThat(backlink.getState()).isEqualTo(Link.BacklinkState.FAILED);
                assertThat(backlink.getError()).contains("external URL");
            })
            .verifyComplete();

        verify(fetcher, never()).fetchBacklinkPage(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "URL blocked for security reasons",
        "reserved address access is not allowed",
        "Only HTTP and HTTPS protocols are allowed",
        "Response exceeds maximum size",
        "Too many redirects"
    })
    void shouldRecordVerificationFetchFailures(String message) {
        service = service();
        Link link = link("link-a", "https://friend.example.com", null);
        when(client.fetch(Link.class, "link-a")).thenReturn(Mono.just(link));
        when(client.update(any(Link.class))).thenAnswer(invocation ->
            Mono.just(invocation.getArgument(0)));
        when(fetcher.fetchReachability("https://friend.example.com"))
            .thenThrow(new ServerErrorException(message, new IllegalArgumentException(message)));

        StepVerifier.create(service.verifyLink("link-a"))
            .assertNext(updated -> {
                Link.AccessStatus access = updated.getStatus().getVerification().getAccess();
                assertThat(access.getState()).isEqualTo(Link.AccessState.INACCESSIBLE);
                assertThat(access.getError()).contains(message);
            })
            .verifyComplete();
    }

    @Test
    void shouldDeduplicateRunningLinks() throws Exception {
        service = service();
        Link link = link("link-a", "https://friend.example.com", null);
        CountDownLatch fetchStarted = new CountDownLatch(1);
        CountDownLatch releaseFetch = new CountDownLatch(1);
        when(client.fetch(Link.class, "link-a")).thenReturn(Mono.just(link));
        when(client.update(any(Link.class))).thenAnswer(invocation ->
            Mono.just(invocation.getArgument(0)));
        when(fetcher.fetchReachability("https://friend.example.com")).thenAnswer(invocation -> {
            fetchStarted.countDown();
            assertThat(releaseFetch.await(2, TimeUnit.SECONDS)).isTrue();
            return fetchResult("https://friend.example.com", 200, "");
        });

        LinkVerificationRequest request = new LinkVerificationRequest();
        request.setNames(List.of("link-a"));
        LinkVerificationTriggerResult first = service.verify(request).block(Duration.ofSeconds(2));
        assertThat(first.getAcceptedNames()).containsExactly("link-a");
        assertThat(fetchStarted.await(2, TimeUnit.SECONDS)).isTrue();

        LinkVerificationTriggerResult second = service.verify(request).block(Duration.ofSeconds(2));
        assertThat(second.getAcceptedNames()).isEmpty();
        assertThat(second.getAlreadyRunningNames()).containsExactly("link-a");

        releaseFetch.countDown();
    }

    @Test
    void shouldQueueBlockingChecksOnVerificationSchedulerWhenStatusUpdatesAreAsync()
        throws Exception {
        service = new DefaultLinkVerificationService(client, externalUrlSupplier, fetcher,
            Schedulers.newSingle("verification-test"));
        Link first = link("first", "https://first.example.com", null);
        Link second = link("second", "https://second.example.com", null);
        List<String> fetchThreads = new CopyOnWriteArrayList<>();
        List<Link> finalUpdates = new CopyOnWriteArrayList<>();
        CountDownLatch firstFetchStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstFetch = new CountDownLatch(1);
        CountDownLatch secondFetchStarted = new CountDownLatch(1);
        when(client.fetch(Link.class, "first")).thenReturn(Mono.just(first));
        when(client.fetch(Link.class, "second")).thenReturn(Mono.just(second));
        when(client.update(any(Link.class))).thenAnswer(invocation -> {
            Link updated = invocation.getArgument(0);
            Link.VerificationStatus status = updated.getStatus().getVerification();
            if (status != null && status.getAccess() != null
                && status.getAccess().getState() != Link.AccessState.CHECKING) {
                finalUpdates.add(updated);
            }
            return Mono.delay(Duration.ofMillis(10), Schedulers.parallel())
                .thenReturn(updated);
        });
        when(fetcher.fetchReachability("https://first.example.com")).thenAnswer(invocation -> {
            fetchThreads.add(Thread.currentThread().getName());
            firstFetchStarted.countDown();
            assertThat(releaseFirstFetch.await(2, TimeUnit.SECONDS)).isTrue();
            return fetchResult("https://first.example.com", 200, "");
        });
        when(fetcher.fetchReachability("https://second.example.com")).thenAnswer(invocation -> {
            fetchThreads.add(Thread.currentThread().getName());
            secondFetchStarted.countDown();
            return fetchResult("https://second.example.com", 200, "");
        });

        LinkVerificationRequest request = new LinkVerificationRequest();
        request.setNames(List.of("first", "second"));
        LinkVerificationTriggerResult result = service.verify(request).block(Duration.ofSeconds(2));

        assertThat(result.getAcceptedNames()).containsExactly("first", "second");
        assertThat(firstFetchStarted.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(secondFetchStarted.await(200, TimeUnit.MILLISECONDS)).isFalse();

        releaseFirstFetch.countDown();
        assertThat(secondFetchStarted.await(2, TimeUnit.SECONDS)).isTrue();
        awaitFinalUpdates(finalUpdates, 2);
        assertThat(fetchThreads)
            .allSatisfy(threadName -> assertThat(threadName).startsWith("verification-test-"));
    }

    @Test
    void shouldContinueBatchWhenOneLinkFails() throws Exception {
        service = service();
        Link good = link("good", "https://good.example.com", null);
        Link bad = link("bad", "https://bad.example.com", null);
        List<Link> finalUpdates = new ArrayList<>();
        when(client.fetch(Link.class, "good")).thenReturn(Mono.just(good));
        when(client.fetch(Link.class, "bad")).thenReturn(Mono.just(bad));
        when(client.update(any(Link.class))).thenAnswer(invocation -> {
            Link updated = invocation.getArgument(0);
            Link.VerificationStatus status = updated.getStatus().getVerification();
            if (status != null && status.getAccess() != null
                && status.getAccess().getState() != Link.AccessState.CHECKING) {
                synchronized (finalUpdates) {
                    finalUpdates.add(updated);
                }
            }
            return Mono.just(updated);
        });
        when(fetcher.fetchReachability("https://good.example.com"))
            .thenReturn(fetchResult("https://good.example.com", 200, ""));
        when(fetcher.fetchReachability("https://bad.example.com"))
            .thenThrow(new ServerErrorException("timeout", new IllegalStateException("timeout")));

        LinkVerificationRequest request = new LinkVerificationRequest();
        request.setNames(List.of("good", "bad"));
        LinkVerificationTriggerResult result = service.verify(request).block(Duration.ofSeconds(2));

        assertThat(result.getAcceptedNames()).containsExactly("good", "bad");
        awaitFinalUpdates(finalUpdates, 2);
        assertThat(finalUpdates)
            .extracting(link -> link.getStatus().getVerification().getAccess().getState())
            .contains(Link.AccessState.ACCESSIBLE, Link.AccessState.INACCESSIBLE);
    }

    @Test
    void shouldResolveSelectedScopeWithUnknownLinkSkipped() {
        service = immediateService();
        Link linkA = link("link-a", "https://a.example.com", null);
        when(client.fetch(Link.class, "link-a")).thenReturn(Mono.just(linkA));
        when(client.fetch(Link.class, "missing")).thenReturn(Mono.empty());
        when(client.update(any(Link.class))).thenAnswer(invocation ->
            Mono.just(invocation.getArgument(0)));
        when(fetcher.fetchReachability(any())).thenReturn(fetchResultUnchecked("https://example.com", 200));

        LinkVerificationRequest selected = new LinkVerificationRequest();
        selected.setNames(List.of("link-a", "missing"));
        LinkVerificationTriggerResult selectedResult = service.verify(selected)
            .block(Duration.ofSeconds(2));
        assertThat(selectedResult.getAcceptedNames()).containsExactly("link-a");
        assertThat(selectedResult.getSkippedNames()).containsExactly("missing");
    }

    @Test
    void shouldResolveGroupScope() {
        service = immediateService();
        Link linkA = link("link-a", "https://a.example.com", null);
        Link linkB = link("link-b", "https://b.example.com", null);
        when(client.listAll(any(), any(ListOptions.class), any()))
            .thenReturn(Flux.just(linkA, linkB));
        when(client.fetch(Link.class, "link-a")).thenReturn(Mono.just(linkA));
        when(client.fetch(Link.class, "link-b")).thenReturn(Mono.just(linkB));
        when(client.update(any(Link.class))).thenAnswer(invocation ->
            Mono.just(invocation.getArgument(0)));
        when(fetcher.fetchReachability(any())).thenReturn(fetchResultUnchecked("https://example.com", 200));

        LinkVerificationRequest group = new LinkVerificationRequest();
        group.setGroupName("friends");
        assertThat(service.verify(group).block(Duration.ofSeconds(2)).getAcceptedNames())
            .containsExactly("link-a", "link-b");
    }

    @Test
    void shouldResolveAllScope() {
        service = immediateService();
        Link linkA = link("link-a", "https://a.example.com", null);
        Link linkB = link("link-b", "https://b.example.com", null);
        when(client.listAll(any(), any(ListOptions.class), any()))
            .thenReturn(Flux.just(linkA, linkB));
        when(client.fetch(Link.class, "link-a")).thenReturn(Mono.just(linkA));
        when(client.fetch(Link.class, "link-b")).thenReturn(Mono.just(linkB));
        when(client.update(any(Link.class))).thenAnswer(invocation ->
            Mono.just(invocation.getArgument(0)));
        when(fetcher.fetchReachability(any())).thenReturn(fetchResultUnchecked("https://example.com", 200));

        assertThat(service.verify(new LinkVerificationRequest()).block(Duration.ofSeconds(2))
            .getAcceptedNames()).containsExactly("link-a", "link-b");
    }

    private DefaultLinkVerificationService service() {
        return new DefaultLinkVerificationService(client, externalUrlSupplier, fetcher);
    }

    private DefaultLinkVerificationService immediateService() {
        return new DefaultLinkVerificationService(client, externalUrlSupplier, fetcher,
            Schedulers.immediate());
    }

    private static void awaitFinalUpdates(List<Link> finalUpdates, int expectedCount)
        throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            synchronized (finalUpdates) {
                if (finalUpdates.size() >= expectedCount) {
                    return;
                }
            }
            Thread.sleep(10);
        }
        assertThat(finalUpdates).hasSizeGreaterThanOrEqualTo(expectedCount);
    }

    private static Link link(String name, String url, String backlinkScanUrl) {
        Link link = new Link();
        Metadata metadata = new Metadata();
        metadata.setName(name);
        link.setMetadata(metadata);
        Link.LinkSpec spec = new Link.LinkSpec();
        spec.setUrl(url);
        if (backlinkScanUrl != null) {
            Link.VerificationSpec verification = new Link.VerificationSpec();
            verification.setBacklinkScanUrl(backlinkScanUrl);
            spec.setVerification(verification);
        }
        link.setSpec(spec);
        link.setStatus(new Link.LinkStatus());
        return link;
    }

    private static SafeUrlFetcher.FetchResult fetchResult(String url, int statusCode, String body)
        throws Exception {
        return new SafeUrlFetcher.FetchResult(new URL(url), statusCode, body, null, null, null);
    }

    private static SafeUrlFetcher.FetchResult fetchResultUnchecked(String url, int statusCode) {
        try {
            return fetchResult(url, statusCode, "");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static SafeUrlFetcher.FetchResult htmlResult(String url, int statusCode, String body)
        throws Exception {
        return new SafeUrlFetcher.FetchResult(new URL(url), statusCode, body,
            Jsoup.parse(body, url), null, null);
    }
}
