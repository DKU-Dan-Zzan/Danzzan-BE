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
import java.util.Arrays;

/** Deterministic portrait home preview. No generated imagery or AI request is involved. */
@Component
@RequiredArgsConstructor
public class HomeBannerRenderer {
    public static final int WIDTH = 900;
    public static final int HEIGHT = 1600;
    private final PosterBackgroundRenderer decoder;

    public byte[] render(byte[] bytes, String layout, Double focusX, Double focusY) {
        double x = focusX == null ? .5 : focusX, y = focusY == null ? .5 : focusY;
        if (!("contain".equals(layout) || "cover".equals(layout)) || !Double.isFinite(x) || !Double.isFinite(y)
                || x < 0 || x > 1 || y < 0 || y > 1)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid home preview layout or focus");
        BufferedImage source = decoder.read(bytes);
        BufferedImage result = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = result.createGraphics();
        try {
            g.setColor(Color.WHITE); g.fillRect(0, 0, WIDTH, HEIGHT);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            if ("cover".equals(layout)) {
                drawCover(g, source, WIDTH, HEIGHT, x, y);
            } else {
                // Blur a reduced cover image to bound CPU/memory cost before enlarging the backdrop.
                BufferedImage small = new BufferedImage(90, 160, BufferedImage.TYPE_INT_RGB);
                Graphics2D background = small.createGraphics();
                try {
                    background.setColor(Color.WHITE); background.fillRect(0, 0, 90, 160);
                    background.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    drawCover(background, source, 90, 160, .5, .5);
                } finally { background.dispose(); }
                float[] weights = new float[17]; Arrays.fill(weights, 1f / weights.length);
                var horizontal = new ConvolveOp(new Kernel(17, 1, weights), ConvolveOp.EDGE_NO_OP, null).filter(small, null);
                var blurred = new ConvolveOp(new Kernel(1, 17, weights), ConvolveOp.EDGE_NO_OP, null).filter(horizontal, null);
                g.drawImage(blurred, 0, 0, WIDTH, HEIGHT, null);
                double scale = Math.min((double) WIDTH / source.getWidth(), (double) HEIGHT / source.getHeight());
                int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
                int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
                g.drawImage(source, (WIDTH - width) / 2, (HEIGHT - height) / 2, width, height, null);
            }
        } finally { g.dispose(); }
        try (var out = new ByteArrayOutputStream()) {
            if (!ImageIO.write(result, "jpeg", out)) throw new IOException("JPEG writer unavailable");
            return out.toByteArray();
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "홈 이미지 미리보기를 만들 수 없습니다.", ex);
        }
    }

    private void drawCover(Graphics2D g, BufferedImage source, int width, int height, double x, double y) {
        Rectangle crop = decoder.cropBounds(source.getWidth(), source.getHeight(),
                new PosterBackgroundRenderer.Target(width, height), x, y);
        g.drawImage(source, 0, 0, width, height, crop.x, crop.y, crop.x + crop.width, crop.y + crop.height, null);
    }
}
