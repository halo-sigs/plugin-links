package run.halo.links.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.links.extension.Link;
import run.halo.links.extension.LinkApplication;

/**
 * Shared validation, duplicate detection, and creation for link applications.
 */
@Component
@RequiredArgsConstructor
public class LinkApplicationService {

    private final ReactiveExtensionClient client;
    private final LinkApplicationCreationCoordinator creationCoordinator;

    public Mono<CreateResult> create(Submission submission) {
        var validation = validate(submission);
        if (validation != null) {
            return Mono.just(validation);
        }
        var canonicalUrl = LinkUrlCanonicalizer.canonicalKey(submission.url()).orElseThrow();
        return creationCoordinator.coordinate(canonicalUrl,
            () -> createCoordinated(submission, canonicalUrl));
    }

    private Mono<CreateResult> createCoordinated(Submission submission, String canonicalUrl) {
        var listOptions = ListOptions.builder().build();
        var links = client.listAll(Link.class, listOptions, Sort.unsorted()).collectList();
        var applications = client.listAll(LinkApplication.class, listOptions, Sort.unsorted())
            .collectList();
        return Mono.zip(links, applications)
            .flatMap(existing -> {
                if (hasFormalLink(existing.getT1(), canonicalUrl)
                    || hasDuplicateApplication(existing.getT2(), submission, canonicalUrl)) {
                    return Mono.just(CreateResult.duplicate(submission.url().trim()));
                }
                var application = toApplication(submission);
                return client.create(application)
                    .map(CreateResult::created);
            });
    }

    private static CreateResult validate(Submission submission) {
        if (submission == null || StringUtils.isBlank(submission.url())) {
            return CreateResult.invalid("url", null, "URL不能为空");
        }
        if (LinkUrlCanonicalizer.canonicalKey(submission.url()).isEmpty()) {
            return CreateResult.invalid("url", submission.url(), "URL格式错误");
        }
        if (StringUtils.isBlank(submission.displayName())) {
            return CreateResult.invalid("displayName", submission.displayName(),
                "网站名称不能为空");
        }
        return null;
    }

    private static boolean hasFormalLink(List<Link> links, String canonicalUrl) {
        return links.stream()
            .filter(link -> link.getSpec() != null)
            .map(link -> LinkUrlCanonicalizer.canonicalKey(link.getSpec().getUrl()))
            .anyMatch(key -> key.filter(canonicalUrl::equals).isPresent());
    }

    private static boolean hasDuplicateApplication(List<LinkApplication> applications,
        Submission incoming, String canonicalUrl) {
        var incomingOriginType = originType(incoming.origin());
        var incomingCommentName = commentName(incoming.origin());
        for (var application : applications) {
            var spec = application.getSpec();
            if (spec == null) {
                continue;
            }
            var existingOrigin = spec.getOrigin();
            if (incomingOriginType == LinkApplication.OriginType.COMMENT
                && StringUtils.isNotBlank(incomingCommentName)
                && existingOrigin != null
                && incomingCommentName.equals(commentName(existingOrigin))) {
                return true;
            }
            var existingKey = LinkUrlCanonicalizer.canonicalKey(spec.getUrl());
            if (existingKey.isEmpty() || !canonicalUrl.equals(existingKey.get())) {
                continue;
            }
            var status = spec.getStatus();
            if (status == null || status == LinkApplication.Status.PENDING
                || status == LinkApplication.Status.APPROVING
                || status == LinkApplication.Status.APPROVED) {
                return true;
            }
            var existingOriginType = originType(existingOrigin);
            if (existingOriginType == LinkApplication.OriginType.FORM
                || incomingOriginType == LinkApplication.OriginType.COMMENT) {
                return true;
            }
        }
        return false;
    }

    private static LinkApplication.OriginType originType(LinkApplication.Origin origin) {
        if (origin == null || origin.getType() == null) {
            return LinkApplication.OriginType.FORM;
        }
        return origin.getType();
    }

    private static String commentName(LinkApplication.Origin origin) {
        if (origin == null || origin.getComment() == null) {
            return null;
        }
        return origin.getComment().getName();
    }

    private static LinkApplication toApplication(Submission submission) {
        var application = new LinkApplication();
        var metadata = new Metadata();
        metadata.setGenerateName("link-app-");
        application.setMetadata(metadata);

        var spec = new LinkApplication.LinkApplicationSpec();
        spec.setUrl(submission.url().trim());
        spec.setDisplayName(submission.displayName().trim());
        spec.setLogo(normalizeOptional(submission.logo()));
        spec.setDescription(normalizeOptional(submission.description()));
        spec.setEmail(normalizeOptional(submission.email()));
        spec.setBacklink(normalizeOptional(submission.backlink()));
        spec.setFeedUrls(normalizeList(submission.feedUrls()));
        spec.setStatus(LinkApplication.Status.PENDING);
        spec.setOrigin(copyOrigin(submission.origin()));
        application.setSpec(spec);
        return application;
    }

    private static LinkApplication.Origin copyOrigin(LinkApplication.Origin source) {
        var target = new LinkApplication.Origin();
        target.setType(originType(source));
        if (source == null) {
            return target;
        }
        var commentName = normalizeOptional(commentName(source));
        if (commentName != null) {
            var comment = new LinkApplication.CommentOrigin();
            comment.setName(commentName);
            target.setComment(comment);
        }
        return target;
    }

    private static String normalizeOptional(String value) {
        return StringUtils.isBlank(value) ? null : value.trim();
    }

    private static List<String> normalizeList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
            .filter(StringUtils::isNotBlank)
            .map(String::trim)
            .toList();
    }

    public record Submission(
        String url,
        String displayName,
        String logo,
        String description,
        String email,
        String backlink,
        List<String> feedUrls,
        LinkApplication.Origin origin
    ) {
    }

    public record CreateResult(
        CreateStatus status,
        LinkApplication application,
        String field,
        String value,
        String message
    ) {

        static CreateResult created(LinkApplication application) {
            return new CreateResult(CreateStatus.CREATED, application, null, null, null);
        }

        static CreateResult duplicate(String value) {
            return new CreateResult(CreateStatus.DUPLICATE, null, "url", value,
                "该链接已提交申请");
        }

        static CreateResult invalid(String field, String value, String message) {
            return new CreateResult(CreateStatus.INVALID, null, field, value, message);
        }
    }

    public enum CreateStatus {
        CREATED,
        DUPLICATE,
        INVALID
    }
}
