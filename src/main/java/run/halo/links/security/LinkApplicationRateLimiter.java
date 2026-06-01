package run.halo.links.security;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.web.reactive.function.server.ServerRequest;

/**
 * Simple in-memory rate limiter for link application submissions.
 * Limits to 1 request per minute per IP address.
 */
public class LinkApplicationRateLimiter {

    private static final Duration RATE_LIMIT_DURATION = Duration.ofMinutes(1);

    private final ConcurrentHashMap<String, Instant> lastRequestByIp = new ConcurrentHashMap<>();

    /**
     * Checks if the request from the given IP is allowed.
     *
     * @param request the server request
     * @return true if the request is within rate limits
     */
    public boolean isAllowed(ServerRequest request) {
        String ip = getClientIp(request);
        Instant now = Instant.now();
        Instant lastRequest = lastRequestByIp.get(ip);
        if (lastRequest != null && Duration.between(lastRequest, now).compareTo(RATE_LIMIT_DURATION) < 0) {
            return false;
        }
        lastRequestByIp.put(ip, now);
        return true;
    }

    private String getClientIp(ServerRequest request) {
        return request.remoteAddress()
            .map(InetSocketAddress::getAddress)
            .map(addr -> addr.getHostAddress())
            .orElse("unknown");
    }
}
