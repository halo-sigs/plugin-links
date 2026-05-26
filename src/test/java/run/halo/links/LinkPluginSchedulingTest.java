package run.halo.links;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import run.halo.app.extension.SchemeManager;
import run.halo.app.plugin.PluginContext;

class LinkPluginSchedulingTest {

    @Test
    void shouldEnableScheduledAnnotationProcessingFromPluginEntry() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(PluginContext.class, () -> mock(PluginContext.class));
            context.registerBean(SchemeManager.class, () -> mock(SchemeManager.class));
            context.register(LinkPlugin.class, TestScheduledBean.class);
            context.refresh();

            int scheduledTaskCount = context.getBeansOfType(ScheduledTaskHolder.class)
                .values()
                .stream()
                .mapToInt(holder -> holder.getScheduledTasks().size())
                .sum();

            assertThat(scheduledTaskCount).isEqualTo(1);
        }
    }

    static class TestScheduledBean {

        @Scheduled(fixedDelay = 3_600_000L, initialDelay = 3_600_000L)
        void refresh() {
        }
    }
}
