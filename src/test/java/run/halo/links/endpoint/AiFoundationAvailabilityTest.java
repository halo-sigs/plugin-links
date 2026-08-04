package run.halo.links.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import run.halo.app.extension.ExtensionClient;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.plugin.PluginContext;
import run.halo.app.plugin.ReactiveSettingFetcher;
import run.halo.app.plugin.extensionpoint.ExtensionGetter;
import run.halo.links.AiFoundationComponentsConfiguration;
import run.halo.links.recognition.CommentApplicationRecognitionProcessor;
import run.halo.links.recognition.CommentApplicationRecognitionReconciler;
import run.halo.links.service.LinkApplicationCapacityService;
import run.halo.links.service.LinkApplicationService;
import run.halo.links.service.ai.AiFoundationLinkAiService;

class AiFoundationAvailabilityTest {

    private static final String AI_MODEL_SERVICE_CLASS =
        "run.halo.aifoundation.AiModelService";
    private static final List<Class<?>> AI_COMPONENT_TYPES = List.of(
        AiFoundationLinkAiService.class,
        LinkAiExtractEndpoint.class,
        AiFoundationComponentsConfiguration.class
    );

    @Test
    void shouldStartContextWithoutRegisteringAiComponentsWhenApiIsAbsent() {
        new ApplicationContextRunner()
            .withClassLoader(new FilteredClassLoader("run.halo.aifoundation"))
            .withUserConfiguration(AiFoundationComponentsConfiguration.class)
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

    @Test
    void shouldRegisterCompleteAiComponentGraphWhenApiIsAvailable() {
        new ApplicationContextRunner()
            .withUserConfiguration(AiFoundationComponentsConfiguration.class)
            .withBean(ExtensionGetter.class, () -> mock(ExtensionGetter.class))
            .withBean(LinkAiSettingsFetcher.class, () -> mock(LinkAiSettingsFetcher.class))
            .withBean(LinkApplicationSettingsFetcher.class,
                () -> mock(LinkApplicationSettingsFetcher.class))
            .withBean(LinkApplicationService.class, () -> mock(LinkApplicationService.class))
            .withBean(LinkApplicationCapacityService.class,
                () -> mock(LinkApplicationCapacityService.class))
            .withBean(ReactiveExtensionClient.class, () -> mock(ReactiveExtensionClient.class))
            .withBean(ReactiveSettingFetcher.class, () -> mock(ReactiveSettingFetcher.class))
            .withBean(PluginContext.class, () -> mock(PluginContext.class))
            .withBean(ExtensionClient.class, () -> mock(ExtensionClient.class))
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(AiFoundationLinkAiService.class);
                assertThat(context).hasSingleBean(LinkAiExtractEndpoint.class);
                assertThat(context).hasSingleBean(
                    CommentApplicationRecognitionProcessor.class);
                assertThat(context).hasSingleBean(
                    CommentApplicationRecognitionReconciler.class);
            });
    }

    @Test
    void shouldNotPartiallyRegisterAiComponentsWhenApiAppearsDuringRegistration() {
        var lookups = new AtomicInteger();
        var changingClassLoader = new ClassLoader(getClass().getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve)
                throws ClassNotFoundException {
                if (AI_MODEL_SERVICE_CLASS.equals(name) && lookups.getAndIncrement() == 0) {
                    throw new ClassNotFoundException(name);
                }
                return super.loadClass(name, resolve);
            }
        };

        new ApplicationContextRunner()
            .withClassLoader(changingClassLoader)
            .withInitializer(context -> registerIndexedAiComponents(
                (GenericApplicationContext) context))
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).doesNotHaveBean(AiFoundationLinkAiService.class);
                assertThat(context).doesNotHaveBean(LinkAiExtractEndpoint.class);
                assertThat(context).doesNotHaveBean(
                    CommentApplicationRecognitionProcessor.class);
                assertThat(context).doesNotHaveBean(
                    CommentApplicationRecognitionReconciler.class);
            });
    }

    private static void registerIndexedAiComponents(GenericApplicationContext context) {
        AI_COMPONENT_TYPES.stream()
            .filter(type -> AnnotatedElementUtils.hasAnnotation(type, Component.class))
            .forEach(context::registerBean);
    }

}
