package run.halo.links;

import static run.halo.links.LinkRouter.LINKS_ROUTE_PATH;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.Map;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import run.halo.app.core.extension.Plugin;
import run.halo.app.core.extension.content.Comment;
import run.halo.app.core.extension.notification.Reason;
import run.halo.app.extension.ExtensionClient;
import run.halo.app.extension.MetadataUtil;
import run.halo.app.extension.Ref;
import run.halo.app.infra.ExternalLinkProcessor;
import run.halo.app.infra.utils.JsonUtils;
import run.halo.app.notification.NotificationReasonEmitter;
import run.halo.app.notification.UserIdentity;
import run.halo.app.plugin.SettingFetcher;

/**
 * @author LIlGG
 */
@Component
@RequiredArgsConstructor
public class CommentNotificationReasonPublisher {

    public static final String NEW_COMMENT_ON_LINK = "new-comment-on-link";
    public static final String NOTIFIED_ANNO = "link.halo.run/notified";

    private final ExtensionClient client;
    private final NotificationReasonEmitter notificationReasonEmitter;
    private final ExternalLinkProcessor externalLinkProcessor;
    private final SettingFetcher settingFetcher;


    /**
     * On new comment.
     */
    @Async
    @EventListener(LinkHasNewCommentEvent.class)
    public void onNewComment(LinkHasNewCommentEvent event) {
        Comment comment = event.getComment();
        var annotations = MetadataUtil.nullSafeAnnotations(comment);
        if (annotations.containsKey(NOTIFIED_ANNO)) {
            return;
        }
        publishReasonBy(comment);
        markAsNotified(comment.getMetadata().getName());
    }

    private void markAsNotified(String commentName) {
        client.fetch(Comment.class, commentName).ifPresent(latestComment -> {
            MetadataUtil.nullSafeAnnotations(latestComment).put(NOTIFIED_ANNO, "true");
            client.update(latestComment);
        });
    }

    public void publishReasonBy(Comment comment) {
        Ref subjectRef = comment.getSpec().getSubjectRef();
        var plugin = client.fetch(Plugin.class, subjectRef.getName()).orElseThrow();

        String linkUrl = externalLinkProcessor.processLink(LINKS_ROUTE_PATH);
        String linkTitle = settingFetcher.get("title").asText();
        if (StringUtils.isBlank(linkTitle)) {
            linkTitle = "链接";
        }
        var reasonSubject = Reason.Subject.builder()
            .apiVersion(plugin.getApiVersion())
            .kind(plugin.getKind())
            .title(linkTitle + ": " + plugin.getMetadata().getName())
            .name(subjectRef.getName())
            .url(linkUrl)
            .build();

        var owner = comment.getSpec().getOwner();
        String finalLinkTitle = linkTitle;
        notificationReasonEmitter.emit(NEW_COMMENT_ON_LINK,
            builder -> {
                var attributes = CommentReasonData.builder()
                    .linkTitle(finalLinkTitle)
                    .commenter(owner.getDisplayName())
                    .content(comment.getSpec().getContent())
                    .commentName(comment.getMetadata().getName())
                    .build();
                builder.attributes(toAttributeMap(attributes))
                    .author(identityFrom(owner))
                    .subject(reasonSubject);
            }).block();
    }

    static <T> Map<String, Object> toAttributeMap(T data) {
        Assert.notNull(data, "Reason attributes must not be null");
        return JsonUtils.mapper().convertValue(data, new TypeReference<>() {
        });
    }

    static UserIdentity identityFrom(Comment.CommentOwner owner) {
        if (Comment.CommentOwner.KIND_EMAIL.equals(owner.getKind())) {
            return UserIdentity.anonymousWithEmail(owner.getName());
        }
        return UserIdentity.of(owner.getName());
    }

    @Builder
    record CommentReasonData(String linkTitle, String commenter, String content,
                             String commentName) {
    }
}
