package com.danzzan.domain.home.service;

import com.danzzan.domain.festival.background.PosterBackgroundRenderer;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/** Deterministic home-preview renderer. No generated imagery or AI request is involved. */
@Component
@RequiredArgsConstructor
public class HomeBannerRenderer {
    public static final int WIDTH = 900;
    public static final int HEIGHT = 1600;
    private final PosterBackgroundRenderer decoder;

    public record Rendered(byte[] bytes, int width, int height) {}

    public byte[] render(byte[] bytes, String layout, Double focusX, Double focusY) {
        return renderImage(bytes, layout, focusX, focusY).bytes();
    }

    public Rendered renderImage(byte[] bytes, String layout, Double focusX, Double focusY) {
        double x = focusX == null ? .5 : focusX, y = focusY == null ? .5 : focusY;
        if (!("contain".equals(layout) || "cover".equals(layout)) || !Double.isFinite(x) || !Double.isFinite(y)
                || x < 0 || x > 1 || y < 0 || y > 1)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid home preview layout or focus");
        BufferedImage source = decoder.read(bytes);
        BufferedImage result = "cover".equals(layout)
                ? new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB)
                : new BufferedImage(containedWidth(source), containedHeight(source), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = result.createGraphics();
        try {
            g.setColor(Color.WHITE); g.fillRect(0, 0, result.getWidth(), result.getHeight());
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            if ("cover".equals(layout)) {
                drawCover(g, source, WIDTH, HEIGHT, x, y);
            } else {
                g.drawImage(source, 0, 0, result.getWidth(), result.getHeight(), null);
            }
        } finally { g.dispose(); }
        try (var out = new ByteArrayOutputStream()) {
            if (!ImageIO.write(result, "jpeg", out)) throw new IOException("JPEG writer unavailable");
            return new Rendered(out.toByteArray(), result.getWidth(), result.getHeight());
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "홈 이미지 미리보기를 만들 수 없습니다.", ex);
        }
    }

    private int containedWidth(BufferedImage source) {
        double scale = Math.min((double) WIDTH / source.getWidth(), (double) HEIGHT / source.getHeight());
        return Math.max(1, (int) Math.round(source.getWidth() * scale));
    }

    private int containedHeight(BufferedImage source) {
        double scale = Math.min((double) WIDTH / source.getWidth(), (double) HEIGHT / source.getHeight());
        return Math.max(1, (int) Math.round(source.getHeight() * scale));
    }

    private void drawCover(Graphics2D g, BufferedImage source, int width, int height, double x, double y) {
        Rectangle crop = decoder.cropBounds(source.getWidth(), source.getHeight(),
                new PosterBackgroundRenderer.Target(width, height), x, y);
        g.drawImage(source, 0, 0, width, height, crop.x, crop.y, crop.x + crop.width, crop.y + crop.height, null);
    }
}
