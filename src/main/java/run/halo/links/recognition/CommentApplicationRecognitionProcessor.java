package run.halo.links.recognition;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.Plugin;
import run.halo.app.core.extension.content.Comment;
import run.halo.app.core.extension.content.Post;
import run.halo.app.core.extension.content.SinglePage;
import run.halo.app.extension.GroupVersionKind;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.Ref;
import run.halo.app.plugin.PluginContext;
import run.halo.app.plugin.ReactiveSettingFetcher;
import run.halo.links.dto.LinkAiSettings;
import run.halo.links.dto.LinkCommentRecognitionRequest;
import run.halo.links.dto.LinkCommentRecognitionResult;
import run.halo.links.endpoint.AiFoundationAvailableCondition;
import run.halo.links.endpoint.LinkAiSettingsFetcher;
import run.halo.links.extension.LinkApplication;
import run.halo.links.route.LinkBaseSettings;
import run.halo.links.service.LinkApplicationService;
import run.halo.links.service.LinkUrlCanonicalizer;
import run.halo.links.service.ai.LinkAiService;

/**
 * Converts one eligible new Comment into a pending LinkApplication when AI recognizes it.
 */
@Component
@RequiredArgsConstructor
@Conditional(AiFoundationAvailableCondition.class)
public class CommentApplicationRecognitionProcessor {

    private static final String BASE_SETTING_GROUP = "base";

    private final LinkAiSettingsFetcher aiSettingsFetcher;
    private final LinkAiService aiService;
    private final LinkApplicationService applicationService;
    private final ReactiveExtensionClient extensionClient;
    private final ReactiveSettingFetcher settingFetcher;
    private final PluginContext pluginContext;

    public Mono<ProcessOutcome> process(Comment comment) {
        if (comment == null || comment.getSpec() == null
            || comment.getSpec().getSubjectRef() == null) {
            return Mono.just(ProcessOutcome.SKIPPED);
        }
        return aiSettingsFetcher.fetch()
            .flatMap(settings -> {
                if (!settings.commentApplicationRecognitionEnabled()) {
                    return Mono.just(ProcessOutcome.SKIPPED);
                }
                var source = findSource(
                    settings.commentApplicationRecognitionSources(),
                    comment.getSpec().getSubjectRef()
                );
                if (source.isEmpty()) {
                    return Mono.just(ProcessOutcome.SKIPPED);
                }
                var modelName = settings.commentApplicationRecognitionModelName();
                return aiService.isOperational(modelName)
                    .onErrorReturn(false)
                    .flatMap(operational -> {
                        if (!operational) {
                            return Mono.just(ProcessOutcome.SKIPPED);
                        }
                        return subjectTitle(source.get())
                            .flatMap(title -> analyze(comment, source.get(), title, modelName));
                    });
            });
    }

    private Mono<ProcessOutcome> analyze(Comment comment,
        LinkAiSettings.RecognitionSource source, String subjectTitle, String modelName) {
        var spec = comment.getSpec();
        var owner = spec.getOwner();
        var ownerDisplayName = owner == null ? null : owner.getDisplayName();
        var ownerWebsite = owner == null
            ? null : owner.getAnnotation(Comment.CommentOwner.WEBSITE_ANNO);
        var request = new LinkCommentRecognitionRequest(
            spec.getRaw(),
            source.getType().name(),
            subjectTitle,
            ownerDisplayName,
            ownerWebsite
        );
        return aiService.recognize(request, modelName)
            .flatMap(result -> createApplication(
                comment, result, modelName, ownerDisplayName, ownerWebsite));
    }

    private Mono<ProcessOutcome> createApplication(Comment comment,
        LinkCommentRecognitionResult result, String modelName, String ownerDisplayName,
        String ownerWebsite) {
        if (!result.isLinkApplication()) {
            return Mono.just(ProcessOutcome.NEGATIVE);
        }
        var url = firstValidUrl(result.url(), ownerWebsite);
        if (url == null) {
            return Mono.just(ProcessOutcome.INVALID);
        }
        var displayName = firstNonBlank(
            result.displayName(),
            ownerDisplayName,
            host(url)
        );
        if (displayName == null) {
            return Mono.just(ProcessOutcome.INVALID);
        }

        var commentSpec = comment.getSpec();
        var owner = commentSpec.getOwner();
        var origin = new LinkApplication.Origin();
        origin.setType(LinkApplication.OriginType.COMMENT);
        origin.setCommentName(comment.getMetadata().getName());
        origin.setSubjectRef(commentSpec.getSubjectRef());
        origin.setModelName(modelName);
        origin.setReason(result.reason());
        origin.setCommentSnapshot(commentSpec.getRaw());

        var submission = new LinkApplicationService.Submission(
            url,
            displayName,
            validOptionalUrl(result.logo()),
            result.description(),
            email(owner),
            validOptionalUrl(result.backlink()),
            validUrls(result.feedUrls()),
            origin
        );
        return applicationService.create(submission)
            .map(created -> switch (created.status()) {
                case CREATED -> ProcessOutcome.CREATED;
                case DUPLICATE -> ProcessOutcome.DUPLICATE;
                case INVALID -> ProcessOutcome.INVALID;
            });
    }

    private Optional<LinkAiSettings.RecognitionSource> findSource(
        List<LinkAiSettings.RecognitionSource> sources, Ref subjectRef) {
        return sources.stream()
            .filter(source -> matches(source, subjectRef))
            .findFirst();
    }

    private boolean matches(LinkAiSettings.RecognitionSource source, Ref subjectRef) {
        return switch (source.getType()) {
            case LINKS -> Ref.groupKindEquals(subjectRef,
                GroupVersionKind.fromExtension(Plugin.class))
                && Objects.equals(pluginContext.getName(), subjectRef.getName());
            case POST -> Ref.groupKindEquals(subjectRef,
                GroupVersionKind.fromExtension(Post.class))
                && Objects.equals(source.getName(), subjectRef.getName());
            case SINGLE_PAGE -> Ref.groupKindEquals(subjectRef,
                GroupVersionKind.fromExtension(SinglePage.class))
                && Objects.equals(source.getName(), subjectRef.getName());
        };
    }

    private Mono<String> subjectTitle(LinkAiSettings.RecognitionSource source) {
        return switch (source.getType()) {
            case LINKS -> settingFetcher.fetch(BASE_SETTING_GROUP, LinkBaseSettings.class)
                .defaultIfEmpty(LinkBaseSettings.defaults())
                .map(LinkBaseSettings::normalizedTitle)
                .onErrorReturn(LinkBaseSettings.DEFAULT_TITLE);
            case POST -> extensionClient.fetch(Post.class, source.getName())
                .map(post -> post.getSpec() == null
                    ? source.getName()
                    : StringUtils.defaultIfBlank(post.getSpec().getTitle(), source.getName()))
                .defaultIfEmpty(source.getName());
            case SINGLE_PAGE -> extensionClient.fetch(SinglePage.class, source.getName())
                .map(page -> page.getSpec() == null
                    ? source.getName()
                    : StringUtils.defaultIfBlank(page.getSpec().getTitle(), source.getName()))
                .defaultIfEmpty(source.getName());
        };
    }

    private static String firstValidUrl(String... candidates) {
        for (var candidate : candidates) {
            if (LinkUrlCanonicalizer.canonicalKey(candidate).isPresent()) {
                return candidate.trim();
            }
        }
        return null;
    }

    private static String validOptionalUrl(String value) {
        return LinkUrlCanonicalizer.canonicalKey(value).isPresent() ? value.trim() : null;
    }

    private static List<String> validUrls(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
            .filter(value -> LinkUrlCanonicalizer.canonicalKey(value).isPresent())
            .map(String::trim)
            .toList();
    }

    private static String firstNonBlank(String... candidates) {
        for (var candidate : candidates) {
            if (StringUtils.isNotBlank(candidate)) {
                return candidate.trim();
            }
        }
        return null;
    }

    private static String host(String url) {
        var canonical = LinkUrlCanonicalizer.canonicalKey(url);
        if (canonical.isEmpty()) {
            return null;
        }
        try {
            return new URI(canonical.get()).getHost();
        } catch (URISyntaxException e) {
            return null;
        }
    }

    private static String email(Comment.CommentOwner owner) {
        if (owner == null || !Comment.CommentOwner.KIND_EMAIL.equals(owner.getKind())) {
            return null;
        }
        return StringUtils.isBlank(owner.getName()) ? null : owner.getName().trim();
    }

    public enum ProcessOutcome {
        SKIPPED,
        NEGATIVE,
        INVALID,
        DUPLICATE,
        CREATED
    }
}
