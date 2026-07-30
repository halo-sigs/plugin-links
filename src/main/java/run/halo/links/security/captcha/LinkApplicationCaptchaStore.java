package run.halo.links.security.captcha;

import jakarta.annotation.PreDestroy;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class LinkApplicationCaptchaStore {

    static final Duration CHALLENGE_TTL = Duration.ofMinutes(5);
    static final int MAX_CHALLENGES = 10_000;
    private static final int IDENTIFIER_BYTES = 24;

    private final Map<String, Challenge> challenges = new HashMap<>();
    private final Clock clock;
    private final int capacity;
    private final Supplier<String> identifierSupplier;

    public LinkApplicationCaptchaStore() {
        var secureRandom = new SecureRandom();
        this.clock = Clock.systemUTC();
        this.capacity = MAX_CHALLENGES;
        this.identifierSupplier = () -> {
            byte[] bytes = new byte[IDENTIFIER_BYTES];
            secureRandom.nextBytes(bytes);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        };
    }

    LinkApplicationCaptchaStore(Clock clock, int capacity, Supplier<String> identifierSupplier) {
        this.clock = clock;
        this.capacity = capacity;
        this.identifierSupplier = identifierSupplier;
    }

    public synchronized String issue(String answer, String previousIdentifier) {
        Instant now = clock.instant();
        removeExpired(now);
        if (previousIdentifier != null) {
            challenges.remove(previousIdentifier);
        }
        if (challenges.size() >= capacity) {
            throw new CapacityExceededException();
        }

        String identifier;
        do {
            identifier = identifierSupplier.get();
        } while (challenges.containsKey(identifier));
        challenges.put(identifier,
            new Challenge(answer.toUpperCase(java.util.Locale.ROOT), now.plus(CHALLENGE_TTL)));
        return identifier;
    }

    public synchronized boolean verifyAndConsume(String identifier, String submittedAnswer) {
        if (identifier == null) {
            return false;
        }
        Challenge challenge = challenges.remove(identifier);
        if (challenge == null || !clock.instant().isBefore(challenge.expiresAt())) {
            return false;
        }
        String normalized = submittedAnswer == null ? "" : submittedAnswer.trim();
        if (normalized.length() != 5 || !normalized.chars().allMatch(character -> character < 128)) {
            return false;
        }
        return challenge.normalizedAnswer().equalsIgnoreCase(normalized);
    }

    @PreDestroy
    public synchronized void clear() {
        challenges.clear();
    }

    synchronized int size() {
        return challenges.size();
    }

    private void removeExpired(Instant now) {
        challenges.values().removeIf(challenge -> !now.isBefore(challenge.expiresAt()));
    }

    private record Challenge(String normalizedAnswer, Instant expiresAt) {
    }

    public static final class CapacityExceededException extends RuntimeException {
    }
}
