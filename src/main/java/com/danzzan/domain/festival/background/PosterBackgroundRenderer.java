package com.danzzan.domain.festival.background;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;

/** Bounded image decoder and cover renderer shared by generated backgrounds and home previews. */
@Component
public class PosterBackgroundRenderer {
    public record Target(int width, int height) {
        public static Target parse(String value) {
            if ("ticket".equals(value)) return new Target(400, 180);
            if ("closed".equals(value)) return new Target(360, 720);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "target은 ticket 또는 closed여야 합니다.");
        }
    }

    public BufferedImage read(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > 5 * 1024 * 1024) throw invalid();
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw invalid();
            var reader = readers.next();
            try {
                reader.setInput(input);
                String format = reader.getFormatName();
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (!("png".equalsIgnoreCase(format) || "jpeg".equalsIgnoreCase(format))
                        || width <= 0 || height <= 0 || (long) width * height > 40_000_000) throw invalid();
                BufferedImage image = reader.read(0);
                if (image == null) throw invalid();
                return image;
            } finally { reader.dispose(); }
        } catch (IOException | IllegalArgumentException ex) { throw invalid(); }
    }

    public Rectangle cropBounds(int width, int height, Target target, double x, double y) {
        if (width <= 0 || height <= 0 || target.width() <= 0 || target.height() <= 0
                || !Double.isFinite(x) || !Double.isFinite(y) || x < 0 || x > 1 || y < 0 || y > 1)
            throw new IllegalArgumentException("Invalid crop parameters");
        double ratio = (double) target.width() / target.height();
        int w = Math.min(width, Math.max(1, (int) Math.round(height * ratio)));
        int h = Math.min(height, Math.max(1, (int) Math.round(width / ratio)));
        int left = Math.max(0, Math.min(width - w, (int) Math.round(x * width - w / 2.0)));
        int top = Math.max(0, Math.min(height - h, (int) Math.round(y * height - h / 2.0)));
        return new Rectangle(left, top, w, h);
    }

    public byte[] render(BufferedImage image, Target target, double x, double y) {
        Rectangle crop = cropBounds(image.getWidth(), image.getHeight(), target, x, y);
        BufferedImage result = new BufferedImage(target.width() * 2, target.height() * 2, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = result.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, result.getWidth(), result.getHeight());
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(image, 0, 0, result.getWidth(), result.getHeight(), crop.x, crop.y,
                    crop.x + crop.width, crop.y + crop.height, null);
        } finally { graphics.dispose(); }
        try (var output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(result, "jpeg", output)) throw new IOException();
            return output.toByteArray();
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "배경 이미지를 만들지 못했습니다.");
        }
    }

    private ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "공식 포스터는 5MB·4천만 화소 이하 JPG 또는 PNG여야 합니다.");
    }
}
