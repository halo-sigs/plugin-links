package run.halo.links.verification;

import reactor.core.publisher.Mono;
import run.halo.links.extension.Link;

public interface LinkVerificationService {

    Mono<LinkVerificationTriggerResult> verify(LinkVerificationRequest request);

    Mono<LinkVerificationTriggerResult> verify(LinkVerificationRequest request,
        LinkVerificationMode mode);

    /**
     * Checks one backlink page without persisting Link verification status.
     */
    Mono<Link.BacklinkStatus> verifyBacklink(String scanUrl);
}
