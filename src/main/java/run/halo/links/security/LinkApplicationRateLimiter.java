package run.halo.links.security;

import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;

/**
 * Simple in-memory rate limiter for link application submissions.
 * Limits to 1 request per minute per IP address.
 */
@Component
public class LinkApplicationRateLimiter {

    private static final Duration RATE_LIMIT_DURATION = Duration.ofMinutes(1);
    private static final int MAX_TRACKED_IPS = 10_000;

    private final ConcurrentHashMap<String, Instant> lastRequestByIp = new ConcurrentHashMap<>();
    private final Clock clock;

    public LinkApplicationRateLimiter() {
        this(Clock.systemUTC());
    }

    LinkApplicationRateLimiter(Clock clock) {
        this.clock = clock;
    }

    /**
     * Checks if the request from the given IP is allowed.
     *
     * @param request the server request
     * @return the admission decision and retry timing when rejected
     */
    public Admission admit(ServerRequest request) {
        String ip = getClientIp(request);
        Instant now = clock.instant();
        if (!lastRequestByIp.containsKey(ip) && lastRequestByIp.size() >= MAX_TRACKED_IPS) {
            removeExpired(now);
            if (lastRequestByIp.size() >= MAX_TRACKED_IPS) {
                removeOldest();
            }
        }
        var admission = new AtomicReference<Admission>();
        lastRequestByIp.compute(ip, (key, lastRequest) -> {
            if (lastRequest != null && now.isBefore(lastRequest.plus(RATE_LIMIT_DURATION))) {
                var remaining = Duration.between(now, lastRequest.plus(RATE_LIMIT_DURATION));
                long retryAfterSeconds = remaining.getSeconds()
                    + (remaining.getNano() == 0 ? 0 : 1);
                admission.set(new Admission(false, retryAfterSeconds));
                return lastRequest;
            }
            admission.set(new Admission(true, 0));
            return now;
        });
        return admission.get();
    }

    int trackedIpCount() {
        return lastRequestByIp.size();
    }

    private void removeExpired(Instant now) {
        lastRequestByIp.entrySet()
            .removeIf(entry -> !now.isBefore(entry.getValue().plus(RATE_LIMIT_DURATION)));
    }

    private void removeOldest() {
        lastRequestByIp.entrySet().stream()
            .min(Comparator.comparing(java.util.Map.Entry::getValue))
            .ifPresent(entry -> lastRequestByIp.remove(entry.getKey(), entry.getValue()));
    }

    private static String getClientIp(ServerRequest request) {
        return request.remoteAddress()
            .map(InetSocketAddress::getAddress)
            .map(addr -> addr.getHostAddress())
            .orElse("unknown");
    }

    public record Admission(boolean allowed, long retryAfterSeconds) {
    }
}
