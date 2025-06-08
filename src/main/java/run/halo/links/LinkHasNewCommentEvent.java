package run.halo.links;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;
import run.halo.app.core.extension.content.Comment;

/**
 * @author LIlGG
 */
@Getter
public class LinkHasNewCommentEvent extends ApplicationEvent {

    private final Comment comment;

    public LinkHasNewCommentEvent(Object source, Comment comment) {
        super(source);
        this.comment = comment;
    }
}
