package run.halo.links.security.captcha;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;

@Component
public class LinkApplicationCaptchaGenerator {

    static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    static final int WIDTH = 160;
    static final int HEIGHT = 48;
    private static final int ANSWER_LENGTH = 5;
    private static final String FONT_RESOURCE = "/fonts/JetBrainsMono-Bold.ttf";

    private final RandomGenerator random;
    private final Supplier<Font> fontLoader;
    private volatile Font font;

    public LinkApplicationCaptchaGenerator() {
        this(new SecureRandom(), LinkApplicationCaptchaGenerator::loadPackagedFont);
    }

    LinkApplicationCaptchaGenerator(RandomGenerator random, Font font) {
        this(random, () -> font);
    }

    LinkApplicationCaptchaGenerator(RandomGenerator random, Supplier<Font> fontLoader) {
        this.random = random;
        this.fontLoader = fontLoader;
    }

    public GeneratedCaptcha generate() {
        Font renderingFont = loadFont();
        String answer = generateAnswer();
        var image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(new Color(245, 247, 250));
            graphics.fillRect(0, 0, WIDTH, HEIGHT);
            drawNoise(graphics);
            drawAnswer(graphics, renderingFont, answer);
        } finally {
            graphics.dispose();
        }

        try (var output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "png", output)) {
                throw new IllegalStateException("No PNG image writer is available");
            }
            return new GeneratedCaptcha(answer, output.toByteArray());
        } catch (IOException error) {
            throw new IllegalStateException("Failed to encode CAPTCHA image", error);
        }
    }

    Font loadFont() {
        Font current = font;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (font == null) {
                font = fontLoader.get();
            }
            return font;
        }
    }

    private String generateAnswer() {
        var answer = new StringBuilder(ANSWER_LENGTH);
        for (int index = 0; index < ANSWER_LENGTH; index++) {
            answer.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return answer.toString();
    }

    private void drawNoise(Graphics2D graphics) {
        for (int index = 0; index < 5; index++) {
            graphics.setColor(randomColor(150, 205));
            graphics.drawLine(random.nextInt(WIDTH), random.nextInt(HEIGHT),
                random.nextInt(WIDTH), random.nextInt(HEIGHT));
        }
        for (int index = 0; index < 28; index++) {
            graphics.setColor(randomColor(120, 205));
            int size = 1 + random.nextInt(2);
            graphics.fillOval(random.nextInt(WIDTH), random.nextInt(HEIGHT), size, size);
        }
    }

    private void drawAnswer(Graphics2D graphics, Font renderingFont, String answer) {
        graphics.setFont(renderingFont.deriveFont(Font.BOLD, 30f));
        for (int index = 0; index < answer.length(); index++) {
            AffineTransform original = graphics.getTransform();
            double angle = Math.toRadians(random.nextInt(17) - 8);
            int x = 10 + index * 29 + random.nextInt(5);
            int y = 34 + random.nextInt(7) - 3;
            graphics.rotate(angle, x + 12, y - 12);
            graphics.setColor(randomColor(25, 100));
            graphics.drawString(String.valueOf(answer.charAt(index)), x, y);
            graphics.setTransform(original);
        }
    }

    private Color randomColor(int minimum, int maximumExclusive) {
        int range = maximumExclusive - minimum;
        return new Color(minimum + random.nextInt(range),
            minimum + random.nextInt(range),
            minimum + random.nextInt(range));
    }

    private static Font loadPackagedFont() {
        try (InputStream input =
            LinkApplicationCaptchaGenerator.class.getResourceAsStream(FONT_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Packaged CAPTCHA font is missing");
            }
            return Font.createFont(Font.TRUETYPE_FONT, input);
        } catch (IOException | FontFormatException error) {
            throw new IllegalStateException("Failed to load packaged CAPTCHA font", error);
        }
    }

    public record GeneratedCaptcha(String answer, byte[] png) {
    }
}
