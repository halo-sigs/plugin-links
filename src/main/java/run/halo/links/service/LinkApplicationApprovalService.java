package run.halo.links.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.links.extension.Link;
import run.halo.links.extension.LinkApplication;
import run.halo.links.extension.LinkGroup;
import run.halo.links.rss.LinkFeedService;
import run.halo.links.verification.LinkVerificationRequest;
import run.halo.links.verification.LinkVerificationService;

/**
 * Resumable LinkApplication approval orchestration.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LinkApplicationApprovalService {

    static final String APPLICATION_NAME_ANNO = "links.halo.run/application-name";

    private final ReactiveExtensionClient client;
    private final LinkVerificationService verificationService;
    private final LinkFeedService feedService;
    private final LinkApplicationCreationCoordinator creationCoordinator;

    public Mono<Link> approve(String applicationName, ApprovalCommand command) {
        return client.fetch(LinkApplication.class, applicationName)
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Link application not found.")))
            .flatMap(application -> approve(application,
                command == null ? new ApprovalCommand(null, null, null, null, null) : command));
    }

    private Mono<Link> approve(LinkApplication application, ApprovalCommand command) {
        var spec = application.getSpec();
        if (spec == null || spec.getStatus() == null) {
            return Mono.error(conflict("Link application has no reviewable status."));
        }
        return switch (spec.getStatus()) {
            case PENDING -> reserve(application, command);
            case APPROVING -> resume(application);
            case APPROVED -> returnApprovedLink(application);
            case REJECTED -> Mono.error(conflict("Rejected link applications cannot be approved."));
        };
    }

    private Mono<Link> reserve(LinkApplication application, ApprovalCommand command) {
        var frozen = normalize(application, command);
        String canonicalUrl = LinkUrlCanonicalizer.canonicalKey(frozen.getUrl()).orElseThrow();
        return creationCoordinator.coordinate(canonicalUrl,
                () -> validateBeforeReservation(application, frozen)
                    .then(Mono.defer(() -> {
                        var approval = new LinkApplication.Approval();
                        approval.setLinkName(application.getMetadata().getName());
                        approval.setRequest(frozen);
                        application.getSpec().setApproval(approval);
                        application.getSpec().setStatus(LinkApplication.Status.APPROVING);
                        return client.update(application)
                            .onErrorResume(this::isConflict, error ->
                                client.fetch(LinkApplication.class,
                                        application.getMetadata().getName())
                                    .switchIfEmpty(Mono.error(error)));
                    })))
            .flatMap(current -> current.getSpec().getStatus() == LinkApplication.Status.PENDING
                ? Mono.error(conflict("Approval reservation did not change application state."))
                : approve(current, command));
    }

    private Mono<Link> resume(LinkApplication application) {
        var approval = application.getSpec().getApproval();
        if (approval == null || StringUtils.isBlank(approval.getLinkName())
            || approval.getRequest() == null) {
            return Mono.error(conflict("Approving application has incomplete approval state."));
        }
        return createOrRecoverOwnedLink(application)
            .flatMap(link -> completeApproval(application)
                .doOnNext(completion -> {
                    if (completion.completedNow()) {
                        triggerAutomation(link);
                    }
                })
                .thenReturn(link));
    }

    private Mono<Link> returnApprovedLink(LinkApplication application) {
        var approval = application.getSpec().getApproval();
        if (approval == null || StringUtils.isBlank(approval.getLinkName())) {
            return Mono.error(conflict("Approved application has no recorded Link."));
        }
        return client.fetch(Link.class, approval.getLinkName())
            .switchIfEmpty(Mono.error(conflict("Approved application Link is missing.")))
            .flatMap(link -> isOwnedBy(link, application.getMetadata().getName())
                ? Mono.just(link)
                : Mono.error(conflict("Recorded Link belongs to another application.")));
    }

    private Mono<Void> validateBeforeReservation(LinkApplication application,
        LinkApplication.ApprovalRequest frozen) {
        Mono<Void> groupValidation = StringUtils.isBlank(frozen.getGroupName())
            ? Mono.empty()
            : client.fetch(LinkGroup.class, frozen.getGroupName())
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Selected LinkGroup does not exist.")))
                .then();
        String canonicalUrl = LinkUrlCanonicalizer.canonicalKey(frozen.getUrl()).orElseThrow();
        var options = ListOptions.builder().build();
        return groupValidation.then(Mono.zip(
                client.listAll(Link.class, options, Sort.unsorted()).collectList(),
                client.listAll(LinkApplication.class, options, Sort.unsorted()).collectList()))
            .flatMap(existing -> {
                boolean formalDuplicate = existing.getT1().stream()
                    .map(Link::getSpec)
                    .filter(Objects::nonNull)
                    .map(Link.LinkSpec::getUrl)
                    .map(LinkUrlCanonicalizer::canonicalKey)
                    .anyMatch(key -> key.filter(canonicalUrl::equals).isPresent());
                boolean applicationDuplicate = existing.getT2().stream()
                    .filter(candidate -> candidate.getMetadata() != null)
                    .filter(candidate -> !Objects.equals(candidate.getMetadata().getName(),
                        application.getMetadata().getName()))
                    .filter(candidate -> candidate.getSpec() != null)
                    .filter(candidate -> LinkApplicationUrlOccupancy
                        .usesCanonicalUrl(candidate, canonicalUrl))
                    .anyMatch(LinkApplicationApprovalService::blocksApproval);
                if (formalDuplicate || applicationDuplicate) {
                    return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT,
                        "A Link or active application already uses this URL."));
                }
                return Mono.empty();
            });
    }

    private static boolean blocksApproval(LinkApplication application) {
        var spec = application.getSpec();
        var status = spec.getStatus();
        if (status == null || status == LinkApplication.Status.PENDING
            || status == LinkApplication.Status.APPROVING
            || status == LinkApplication.Status.APPROVED) {
            return true;
        }
        var origin = spec.getOrigin();
        return origin == null || origin.getType() == null
            || origin.getType() == LinkApplication.OriginType.FORM;
    }

    private Mono<Link> createOrRecoverOwnedLink(LinkApplication application) {
        String applicationName = application.getMetadata().getName();
        String linkName = application.getSpec().getApproval().getLinkName();
        return client.fetch(Link.class, linkName)
            .flatMap(link -> isOwnedBy(link, applicationName)
                ? Mono.just(link)
                : Mono.error(conflict("Reserved Link name belongs to another application.")))
            .switchIfEmpty(Mono.defer(() -> client.create(toLink(application))
                .onErrorResume(this::isCreateConflict,
                    error -> client.fetch(Link.class, linkName)
                        .switchIfEmpty(Mono.error(error))
                        .flatMap(link -> isOwnedBy(link, applicationName)
                            ? Mono.just(link)
                            : Mono.error(conflict(
                                "Reserved Link name belongs to another application."))))));
    }

    private Mono<ApprovalCompletion> completeApproval(LinkApplication application) {
        application.getSpec().setStatus(LinkApplication.Status.APPROVED);
        return client.update(application)
            .map(updated -> new ApprovalCompletion(updated, true))
            .onErrorResume(this::isConflict, error ->
                client.fetch(LinkApplication.class, application.getMetadata().getName())
                    .switchIfEmpty(Mono.error(error))
                    .flatMap(current -> {
                        if (current.getSpec().getStatus() == LinkApplication.Status.APPROVED) {
                            return Mono.just(new ApprovalCompletion(current, false));
                        }
                        if (current.getSpec().getStatus() != LinkApplication.Status.APPROVING) {
                            return Mono.error(conflict(
                                "Application lifecycle changed while approval was completing."));
                        }
                        current.getSpec().setStatus(LinkApplication.Status.APPROVED);
                        return client.update(current)
                            .map(updated -> new ApprovalCompletion(updated, true));
                    }));
    }

    private Link toLink(LinkApplication application) {
        var appSpec = application.getSpec();
        var frozen = appSpec.getApproval().getRequest();
        var link = new Link();
        var metadata = new Metadata();
        metadata.setName(appSpec.getApproval().getLinkName());
        metadata.setAnnotations(Map.of(APPLICATION_NAME_ANNO,
            application.getMetadata().getName()));
        link.setMetadata(metadata);

        var spec = new Link.LinkSpec();
        spec.setUrl(frozen.getUrl());
        spec.setDisplayName(frozen.getDisplayName());
        spec.setLogo(frozen.getLogo());
        spec.setDescription(frozen.getDescription());
        spec.setGroupName(frozen.getGroupName());
        spec.setPriority(0);
        if (appSpec.getFeedUrls() != null && !appSpec.getFeedUrls().isEmpty()) {
            var rss = new Link.RssSpec();
            rss.setEnabled(true);
            rss.setFeedUrls(List.copyOf(appSpec.getFeedUrls()));
            spec.setRss(rss);
        }
        if (StringUtils.isNotBlank(appSpec.getBacklink())) {
            var verification = new Link.VerificationSpec();
            verification.setBacklinkScanUrl(appSpec.getBacklink().trim());
            spec.setVerification(verification);
        }
        link.setSpec(spec);
        return link;
    }

    private void triggerAutomation(Link link) {
        String linkName = link.getMetadata().getName();
        var request = new LinkVerificationRequest();
        request.setNames(List.of(linkName));
        verificationService.verify(request)
            .doOnError(error -> log.warn("Failed to trigger verification for approved Link {}",
                linkName, error))
            .onErrorResume(error -> Mono.empty())
            .subscribe();
        var rss = link.getSpec().getRss();
        if (rss != null && Boolean.TRUE.equals(rss.getEnabled())
            && rss.getFeedUrls() != null && !rss.getFeedUrls().isEmpty()) {
            feedService.refresh(linkName)
                .doOnError(error -> log.warn("Failed to trigger RSS refresh for approved Link {}",
                    linkName, error))
                .onErrorResume(error -> Mono.empty())
                .subscribe();
        }
    }

    private static LinkApplication.ApprovalRequest normalize(LinkApplication application,
        ApprovalCommand command) {
        var spec = application.getSpec();
        var frozen = new LinkApplication.ApprovalRequest();
        frozen.setUrl(required(command.url(), spec.getUrl(), "url", "URL is required."));
        if (LinkUrlCanonicalizer.canonicalKey(frozen.getUrl()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URL format is invalid.");
        }
        frozen.setDisplayName(required(command.displayName(), spec.getDisplayName(),
            "displayName", "Display name is required."));
        frozen.setLogo(optional(command.logo(), spec.getLogo()));
        frozen.setDescription(optional(command.description(), spec.getDescription()));
        frozen.setGroupName(StringUtils.isBlank(command.groupName())
            ? null : command.groupName().trim());
        return frozen;
    }

    private static String required(String override, String original, String field, String message) {
        String value = StringUtils.isNotBlank(override) ? override : original;
        if (StringUtils.isBlank(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                field + ": " + message);
        }
        return value.trim();
    }

    private static String optional(String override, String original) {
        String value = override != null ? override : original;
        return StringUtils.isBlank(value) ? null : value.trim();
    }

    private boolean isConflict(Throwable error) {
        return error instanceof org.springframework.dao.OptimisticLockingFailureException
            || error instanceof ResponseStatusException response
            && response.getStatusCode().value() == HttpStatus.CONFLICT.value();
    }

    private boolean isCreateConflict(Throwable error) {
        return error instanceof DataIntegrityViolationException || isConflict(error);
    }

    private static boolean isOwnedBy(Link link, String applicationName) {
        return link.getMetadata() != null && link.getMetadata().getAnnotations() != null
            && applicationName.equals(
            link.getMetadata().getAnnotations().get(APPLICATION_NAME_ANNO));
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    public record ApprovalCommand(
        String url,
        String displayName,
        String logo,
        String description,
        String groupName
    ) {
    }

    private record ApprovalCompletion(
        LinkApplication application,
        boolean completedNow
    ) {
    }
}
