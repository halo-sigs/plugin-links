package run.halo.links;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import reactor.core.publisher.Mono;
import run.halo.app.content.comment.CommentSubject;
import run.halo.app.core.extension.Plugin;
import run.halo.app.extension.GroupVersionKind;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.Ref;
import run.halo.app.plugin.PluginContext;

/**
 * @author LIlGG
 */
@Component
@RequiredArgsConstructor
public class LinkCommentSubject implements CommentSubject<Plugin> {
    
    private final ReactiveExtensionClient client;
    
    private final PluginContext pluginContext;
    
    @Override
    public Mono<Plugin> get(String name) {
        return client.get(Plugin.class, name);
    }
    
    @Override
    public Mono<SubjectDisplay> getSubjectDisplay(String name) {
        return Mono.just(new SubjectDisplay("链接", "/links", "链接"));
    }
    
    @Override
    public boolean supports(Ref ref) {
        Assert.notNull(ref, "Subject ref must not be null.");
        GroupVersionKind groupVersionKind = new GroupVersionKind(ref.getGroup(),
            ref.getVersion(), ref.getKind()
        );
        return GroupVersionKind.fromExtension(Plugin.class).equals(
            groupVersionKind) && pluginContext.getName().equals(ref.getName());
    }
}
