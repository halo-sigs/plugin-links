package run.halo.links.verification;

import static run.halo.app.extension.index.query.Queries.equal;

import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.net.URL;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.infra.ExternalUrlSupplier;
import run.halo.links.extension.Link;
import run.halo.links.security.SafeUrlFetcher;

@Slf4j
@Component
public class DefaultLinkVerificationService implements LinkVerificationService {

    private static final int VERIFICATION_CONCURRENCY = 3;
    private static final int VERIFICATION_QUEUE_CAPACITY = 1024;

    private final ReactiveExtensionClient client;
    private final ExternalUrlSupplier externalUrlSupplier;
    private final LinkVerificationFetcher fetcher;
    private final Scheduler scheduler;
    private final Set<String> runningNames = ConcurrentHashMap.newKeySet();

    @Autowired
    public DefaultLinkVerificationService(ReactiveExtensionClient client,
        ExternalUrlSupplier externalUrlSupplier, LinkVerificationFetcher fetcher) {
        this(client, externalUrlSupplier, fetcher,
            Schedulers.newBoundedElastic(VERIFICATION_CONCURRENCY, VERIFICATION_QUEUE_CAPACITY,
                "link-verification"));
    }

    DefaultLinkVerificationService(ReactiveExtensionClient client,
        ExternalUrlSupplier externalUrlSupplier, LinkVerificationFetcher fetcher,
        Scheduler scheduler) {
        this.client = client;
        this.externalUrlSupplier = externalUrlSupplier;
        this.fetcher = fetcher;
        this.scheduler = scheduler;
    }

    @Override
    public Mono<LinkVerificationTriggerResult> verify(LinkVerificationRequest request) {
        return resolveLinks(Optional.ofNullable(request).orElse(new LinkVerificationRequest()))
            .collectList()
            .map(this::enqueue);
    }

    @PreDestroy
    void disposeScheduler() {
        scheduler.dispose();
    }

    Mono<Link> verifyLink(String linkName) {
        return client.fetch(Link.class, linkName)
            .flatMap(link -> {
                link.getStatus().setVerification(checkingStatus(link));
                return client.update(link);
            })
            .flatMap(link -> Mono.fromCallable(() -> verifyBlocking(link))
                .map(status -> {
                    link.getStatus().setVerification(status);
                    return link;
                })
                .flatMap(client::update)
                .onErrorResume(error -> {
                    link.getStatus().setVerification(unexpectedFailureStatus(link, error));
                    return client.update(link);
                }));
    }

    private Flux<ResolvedLink> resolveLinks(LinkVerificationRequest request) {
        List<String> names = normalizedNames(request.getNames());
        if (!names.isEmpty()) {
            return Flux.fromIterable(names)
                .concatMap(name -> client.fetch(Link.class, name)
                    .map(link -> ResolvedLink.found(name, link))
                    .defaultIfEmpty(ResolvedLink.skipped(name)));
        }
        String groupName = request.getGroupName();
        if (StringUtils.hasText(groupName)) {
            var options = ListOptions.builder()
                .andQuery(equal("spec.groupName", groupName.trim()))
                .build();
            return client.listAll(Link.class, options, Sort.unsorted())
                .map(link -> ResolvedLink.found(link.getMetadata().getName(), link));
        }
        return client.listAll(Link.class, ListOptions.builder().build(), Sort.unsorted())
            .map(link -> ResolvedLink.found(link.getMetadata().getName(), link));
    }

    private LinkVerificationTriggerResult enqueue(List<ResolvedLink> resolvedLinks) {
        List<String> acceptedNames = new ArrayList<>();
        List<String> skippedNames = new ArrayList<>();
        List<String> alreadyRunningNames = new ArrayList<>();

        for (ResolvedLink resolvedLink : resolvedLinks) {
            if (resolvedLink.link() == null) {
                skippedNames.add(resolvedLink.name());
                continue;
            }
            String name = resolvedLink.name();
            if (!runningNames.add(name)) {
                alreadyRunningNames.add(name);
                continue;
            }
            acceptedNames.add(name);
            verifyLink(name)
                .subscribeOn(scheduler)
                .doOnError(error -> log.warn("Failed to verify link {}", name, error))
                .onErrorResume(error -> Mono.empty())
                .doFinally(signalType -> runningNames.remove(name))
                .subscribe();
        }

        LinkVerificationTriggerResult result = new LinkVerificationTriggerResult();
        result.setAcceptedNames(List.copyOf(acceptedNames));
        result.setSkippedNames(List.copyOf(skippedNames));
        result.setAlreadyRunningNames(List.copyOf(alreadyRunningNames));
        return result;
    }

    private Link.VerificationStatus verifyBlocking(Link link) {
        Link.VerificationStatus status = new Link.VerificationStatus();
        Link.AccessStatus access = verifyAccess(link);
        Link.BacklinkStatus backlink = verifyBacklink(link);
        status.setAccess(access);
        status.setBacklink(backlink);
        status.setLastCheckedAt(maxCheckedAt(access.getCheckedAt(), backlink.getCheckedAt()));
        return status;
    }

    private Link.AccessStatus verifyAccess(Link link) {
        Instant checkedAt = Instant.now();
        Link.AccessStatus status = new Link.AccessStatus();
        status.setCheckedAt(checkedAt);
        String url = link.getSpec() == null ? null : link.getSpec().getUrl();
        if (!StringUtils.hasText(url)) {
            status.setState(Link.AccessState.INACCESSIBLE);
            status.setError("Link URL is required.");
            return status;
        }
        try {
            SafeUrlFetcher.FetchResult result = fetcher.fetchReachability(url.trim());
            status.setStatusCode(result.statusCode());
            status.setFinalUrl(result.url().toExternalForm());
            if (isSuccess(result.statusCode())) {
                status.setState(Link.AccessState.ACCESSIBLE);
            } else {
                status.setState(Link.AccessState.INACCESSIBLE);
                status.setError("Link responded with HTTP " + result.statusCode());
            }
        } catch (Throwable e) {
            status.setState(Link.AccessState.INACCESSIBLE);
            status.setError(errorMessage(e));
        }
        return status;
    }

    private Link.BacklinkStatus verifyBacklink(Link link) {
        Instant checkedAt = Instant.now();
        Link.BacklinkStatus status = new Link.BacklinkStatus();
        status.setCheckedAt(checkedAt);

        String scanUrl = backlinkScanUrl(link);
        if (!StringUtils.hasText(scanUrl)) {
            status.setState(Link.BacklinkState.NOT_CONFIGURED);
            return status;
        }
        status.setScanUrl(scanUrl);

        Optional<URI> targetUri = externalUrl();
        if (targetUri.isEmpty()) {
            status.setState(Link.BacklinkState.FAILED);
            status.setError("Halo external URL is not configured.");
            return status;
        }
        status.setTargetUrl(targetUri.get().toString());

        try {
            SafeUrlFetcher.FetchResult result = fetcher.fetchBacklinkPage(scanUrl);
            if (!isSuccess(result.statusCode())) {
                status.setState(Link.BacklinkState.FAILED);
                status.setError("Backlink scan page responded with HTTP " + result.statusCode());
                return status;
            }
            Document document = result.document() == null
                ? Jsoup.parse(result.body(), result.url().toExternalForm())
                : result.document();
            Optional<String> matchedUrl = findBacklink(document, targetUri.get());
            if (matchedUrl.isPresent()) {
                status.setState(Link.BacklinkState.FOUND);
                status.setMatchedUrl(matchedUrl.get());
            } else {
                status.setState(Link.BacklinkState.MISSING);
            }
        } catch (Throwable e) {
            status.setState(Link.BacklinkState.FAILED);
            status.setError(errorMessage(e));
        }
        return status;
    }

    private Optional<URI> externalUrl() {
        try {
            URL externalUrl = externalUrlSupplier.getRaw();
            if (externalUrl == null) {
                return Optional.empty();
            }
            URI uri = externalUrl.toURI().normalize();
            if (!StringUtils.hasText(uri.getScheme()) || !StringUtils.hasText(uri.getHost())) {
                return Optional.empty();
            }
            return Optional.of(uri);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static Optional<String> findBacklink(Document document, URI targetUri) {
        if (document == null) {
            return Optional.empty();
        }
        return document.select("a[href]")
            .stream()
            .map(element -> element.absUrl("href"))
            .filter(StringUtils::hasText)
            .map(String::trim)
            .filter(href -> pointsToTarget(href, targetUri))
            .findFirst();
    }

    private static boolean pointsToTarget(String href, URI targetUri) {
        try {
            URI hrefUri = URI.create(href).normalize();
            if (!sameOrigin(hrefUri, targetUri)) {
                return false;
            }
            String targetPath = normalizePath(targetUri.getPath());
            if ("/".equals(targetPath)) {
                return true;
            }
            String hrefPath = normalizePath(hrefUri.getPath());
            return hrefPath.equals(targetPath) || hrefPath.startsWith(targetPath + "/");
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean sameOrigin(URI left, URI right) {
        return Objects.equals(lower(left.getScheme()), lower(right.getScheme()))
            && Objects.equals(lower(left.getHost()), lower(right.getHost()))
            && effectivePort(left) == effectivePort(right);
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() != -1) {
            return uri.getPort();
        }
        String scheme = lower(uri.getScheme());
        if ("http".equals(scheme)) {
            return 80;
        }
        if ("https".equals(scheme)) {
            return 443;
        }
        return -1;
    }

    private static String normalizePath(String path) {
        if (!StringUtils.hasText(path)) {
            return "/";
        }
        String normalized = path.startsWith("/") ? path : "/" + path;
        while (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static String lower(String value) {
        return value == null ? null : value.toLowerCase();
    }

    private static List<String> normalizedNames(List<String> names) {
        if (names == null) {
            return List.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        names.stream()
            .filter(StringUtils::hasText)
            .map(String::trim)
            .forEach(normalized::add);
        return List.copyOf(normalized);
    }

    private static Link.VerificationStatus checkingStatus(Link link) {
        Instant checkedAt = Instant.now();
        Link.VerificationStatus status = new Link.VerificationStatus();
        status.setLastCheckedAt(checkedAt);

        Link.AccessStatus access = new Link.AccessStatus();
        access.setState(Link.AccessState.CHECKING);
        access.setCheckedAt(checkedAt);
        status.setAccess(access);

        Link.BacklinkStatus backlink = new Link.BacklinkStatus();
        backlink.setCheckedAt(checkedAt);
        String scanUrl = backlinkScanUrl(link);
        if (StringUtils.hasText(scanUrl)) {
            backlink.setState(Link.BacklinkState.CHECKING);
            backlink.setScanUrl(scanUrl);
        } else {
            backlink.setState(Link.BacklinkState.NOT_CONFIGURED);
        }
        status.setBacklink(backlink);

        return status;
    }

    private static Link.VerificationStatus unexpectedFailureStatus(Link link, Throwable error) {
        Instant checkedAt = Instant.now();
        Link.VerificationStatus status = new Link.VerificationStatus();
        status.setLastCheckedAt(checkedAt);

        Link.AccessStatus access = new Link.AccessStatus();
        access.setState(Link.AccessState.INACCESSIBLE);
        access.setCheckedAt(checkedAt);
        access.setError(errorMessage(error));
        status.setAccess(access);

        Link.BacklinkStatus backlink = new Link.BacklinkStatus();
        backlink.setCheckedAt(checkedAt);
        String scanUrl = backlinkScanUrl(link);
        backlink.setScanUrl(scanUrl);
        backlink.setState(StringUtils.hasText(scanUrl)
            ? Link.BacklinkState.FAILED
            : Link.BacklinkState.NOT_CONFIGURED);
        if (StringUtils.hasText(scanUrl)) {
            backlink.setError(errorMessage(error));
        }
        status.setBacklink(backlink);
        return status;
    }

    private static String backlinkScanUrl(Link link) {
        if (link.getSpec() == null || link.getSpec().getVerification() == null) {
            return null;
        }
        String scanUrl = link.getSpec().getVerification().getBacklinkScanUrl();
        return StringUtils.hasText(scanUrl) ? scanUrl.trim() : null;
    }

    private static Instant maxCheckedAt(Instant left, Instant right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.isAfter(right) ? left : right;
    }

    private static boolean isSuccess(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    private static String errorMessage(Throwable error) {
        String message = error.getMessage();
        return StringUtils.hasText(message) ? message : error.getClass().getSimpleName();
    }

    private record ResolvedLink(String name, Link link) {
        static ResolvedLink found(String name, Link link) {
            return new ResolvedLink(name, link);
        }

        static ResolvedLink skipped(String name) {
            return new ResolvedLink(name, null);
        }
    }
}
