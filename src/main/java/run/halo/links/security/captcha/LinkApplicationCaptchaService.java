package run.halo.links.security.captcha;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Mono;

@Component
public class LinkApplicationCaptchaService {

    private final LinkApplicationCaptchaGenerationLimiter limiter;
    private final LinkApplicationCaptchaGenerator generator;
    private final LinkApplicationCaptchaStore store;
    private final LinkApplicationCaptchaCookie cookies;
    private final LinkApplicationCaptchaRenderGate renderGate;

    public LinkApplicationCaptchaService(LinkApplicationCaptchaGenerationLimiter limiter,
        LinkApplicationCaptchaGenerator generator, LinkApplicationCaptchaStore store,
        LinkApplicationCaptchaCookie cookies, LinkApplicationCaptchaRenderGate renderGate) {
        this.limiter = limiter;
        this.generator = generator;
        this.store = store;
        this.cookies = cookies;
        this.renderGate = renderGate;
    }

    public Mono<IssueResult> issue(ServerRequest request) {
        var admission = limiter.admit(request);
        if (!admission.allowed()) {
            return Mono.just(IssueResult.rateLimited(admission.retryAfterSeconds()));
        }
        String previousIdentifier = cookies.resolve(request);
        return renderGate.execute(() -> {
                var generated = generator.generate();
                String identifier = store.issue(generated.answer(), previousIdentifier);
                return IssueResult.issued(generated.png(), cookies.issue(identifier, request));
            })
            .onErrorResume(Exception.class,
                error -> Mono.just(IssueResult.unavailable()));
    }

    public VerificationResult verify(ServerRequest request, String submittedAnswer) {
        boolean valid = store.verifyAndConsume(cookies.resolve(request), submittedAnswer);
        return new VerificationResult(valid, cookies.expire(request));
    }

    public enum IssueStatus {
        ISSUED,
        RATE_LIMITED,
        UNAVAILABLE
    }

    public record IssueResult(
        IssueStatus status,
        byte[] png,
        ResponseCookie cookie,
        long retryAfterSeconds
    ) {

        private static IssueResult issued(byte[] png, ResponseCookie cookie) {
            return new IssueResult(IssueStatus.ISSUED, png, cookie, 0);
        }

        private static IssueResult rateLimited(long retryAfterSeconds) {
            return new IssueResult(IssueStatus.RATE_LIMITED, null, null, retryAfterSeconds);
        }

        private static IssueResult unavailable() {
            return new IssueResult(IssueStatus.UNAVAILABLE, null, null, 0);
        }
    }

    public record VerificationResult(boolean valid, ResponseCookie expiredCookie) {
    }
}
