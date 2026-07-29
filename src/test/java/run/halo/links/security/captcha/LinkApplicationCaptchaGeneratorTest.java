package run.halo.links.security.captcha;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Font;
import java.io.ByteArrayInputStream;
import java.util.random.RandomGenerator;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class LinkApplicationCaptchaGeneratorTest {

    @Test
    void shouldGenerateFixedAnswerAndPngDimensions() throws Exception {
        var generator = new LinkApplicationCaptchaGenerator(new ZeroRandom(),
            new Font(Font.MONOSPACED, Font.BOLD, 30));

        var captcha = generator.generate();
        var image = ImageIO.read(new ByteArrayInputStream(captcha.png()));

        assertThat(captcha.answer()).isEqualTo("AAAAA");
        assertThat(captcha.answer()).hasSize(5);
        assertThat(captcha.answer().chars())
            .allMatch(character -> LinkApplicationCaptchaGenerator.ALPHABET
                .indexOf(character) >= 0);
        assertThat(LinkApplicationCaptchaGenerator.ALPHABET)
            .doesNotContain("0", "O", "1", "I", "l");
        assertThat(image.getWidth()).isEqualTo(160);
        assertThat(image.getHeight()).isEqualTo(48);
    }

    @Test
    void shouldLoadPackagedFontInHeadlessMode() {
        var generator = new LinkApplicationCaptchaGenerator();

        assertThat(generator.loadFont().getFamily()).isEqualTo("JetBrains Mono");
    }

    private static final class ZeroRandom implements RandomGenerator {

        @Override
        public long nextLong() {
            return 0;
        }
    }
}
