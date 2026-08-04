package run.halo.links;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import run.halo.links.endpoint.AiFoundationAvailableCondition;
import run.halo.links.endpoint.LinkAiExtractEndpoint;
import run.halo.links.recognition.CommentApplicationRecognitionProcessor;
import run.halo.links.recognition.CommentApplicationRecognitionReconciler;
import run.halo.links.service.ai.AiFoundationLinkAiService;

/**
 * Registers the complete AI Foundation integration as one conditional bean graph.
 */
@Configuration(proxyBeanMethods = false)
@Conditional(AiFoundationAvailableCondition.class)
@Import({
    AiFoundationLinkAiService.class,
    LinkAiExtractEndpoint.class,
    CommentApplicationRecognitionProcessor.class,
    CommentApplicationRecognitionReconciler.class
})
public class AiFoundationComponentsConfiguration {
}
