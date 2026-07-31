package run.halo.links.security.captcha;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Mono;

@Component
public class LinkApplicationCaptchaService {

    private final LinkApplicationCaptchaGenerationLimiter limiter;
    private final LinkApplicationCaptchaGenerator generator;
    private final LinkApplicationCaptchaStore store;
    private final LinkApplicationCaptchaRenderGate renderGate;

    public LinkApplicationCaptchaService(LinkApplicationCaptchaGenerationLimiter limiter,
        LinkApplicationCaptchaGenerator generator, LinkApplicationCaptchaStore store,
        LinkApplicationCaptchaRenderGate renderGate) {
        this.limiter = limiter;
        this.generator = generator;
        this.store = store;
        this.renderGate = renderGate;
    }

    public Mono<IssueResult> issue(ServerRequest request, String previousIdentifier) {
        var admission = limiter.admit(request);
        if (!admission.allowed()) {
            return Mono.just(IssueResult.rateLimited(admission.retryAfterSeconds()));
        }
        return renderGate.execute(() -> {
                var generated = generator.generate();
                String identifier = store.issue(generated.answer(), previousIdentifier);
                return IssueResult.issued(identifier, generated.png());
            })
            .onErrorResume(Exception.class,
                error -> Mono.just(IssueResult.unavailable()));
    }

    public boolean verifyChallenge(String identifier, String submittedAnswer) {
        return store.verifyAndConsume(identifier, submittedAnswer);
    }

    public enum IssueStatus {
        ISSUED,
        RATE_LIMITED,
        UNAVAILABLE
    }

    public record IssueResult(
        IssueStatus status,
        String identifier,
        byte[] png,
        long expiresInSeconds,
        long retryAfterSeconds
    ) {

        private static IssueResult issued(String identifier, byte[] png) {
            return new IssueResult(IssueStatus.ISSUED, identifier, png,
                LinkApplicationCaptchaStore.CHALLENGE_TTL.toSeconds(), 0);
        }

        private static IssueResult rateLimited(long retryAfterSeconds) {
            return new IssueResult(IssueStatus.RATE_LIMITED, null, null, 0,
                retryAfterSeconds);
        }

        private static IssueResult unavailable() {
            return new IssueResult(IssueStatus.UNAVAILABLE, null, null, 0, 0);
        }
    }
}
