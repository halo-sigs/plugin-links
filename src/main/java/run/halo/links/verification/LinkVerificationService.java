package run.halo.links.verification;

import reactor.core.publisher.Mono;

public interface LinkVerificationService {

    Mono<LinkVerificationTriggerResult> verify(LinkVerificationRequest request);

    Mono<LinkVerificationTriggerResult> verify(LinkVerificationRequest request,
        LinkVerificationMode mode);
}
