package run.halo.links.endpoint;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import run.halo.links.recognition.CommentApplicationRecognitionProcessor;
import run.halo.links.recognition.CommentApplicationRecognitionReconciler;
import run.halo.links.service.ai.AiFoundationLinkAiService;

class AiFoundationAvailabilityTest {

    @Test
    void shouldStartContextWithoutRegisteringAiComponentsWhenApiIsAbsent() {
        new ApplicationContextRunner()
            .withClassLoader(new FilteredClassLoader("run.halo.aifoundation"))
            .withUserConfiguration(ConditionalAiConfiguration.class)
            .run(context -> {
                assertThat(context.getStartupFailure()).isNull();
                assertThat(context).doesNotHaveBean(AiFoundationLinkAiService.class);
                assertThat(context).doesNotHaveBean(LinkAiExtractEndpoint.class);
                assertThat(context).doesNotHaveBean(
                    CommentApplicationRecognitionProcessor.class);
                assertThat(context).doesNotHaveBean(
                    CommentApplicationRecognitionReconciler.class);
            });
    }

    @Test
    void shouldDetectAiFoundationApiOnCurrentTestClasspath() {
        assertThat(AiFoundationAvailability.isAvailable()).isTrue();
    }

    @Configuration(proxyBeanMethods = false)
    @Import({
        AiFoundationLinkAiService.class,
        LinkAiExtractEndpoint.class,
        CommentApplicationRecognitionProcessor.class,
        CommentApplicationRecognitionReconciler.class
    })
    static class ConditionalAiConfiguration {
    }
}
