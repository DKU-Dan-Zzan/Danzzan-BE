package com.danzzan.domain.home;

import com.danzzan.domain.festival.background.PosterBackgroundRenderer;
import com.danzzan.domain.home.service.HomeBannerRenderer;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class HomeBannerRendererTest {
    final HomeBannerRenderer renderer = new HomeBannerRenderer(new PosterBackgroundRenderer());

    byte[] png(BufferedImage image) throws Exception {
        var out = new ByteArrayOutputStream(); ImageIO.write(image, "png", out); return out.toByteArray();
    }
    BufferedImage render(byte[] input, String layout, Double x, Double y) throws Exception {
        return ImageIO.read(new ByteArrayInputStream(renderer.render(input, layout, x, y)));
    }

    @Test void coverFocusSelectsLeftAndRightAndIsDeterministic() throws Exception {
        var source = new BufferedImage(300, 100, BufferedImage.TYPE_INT_RGB);
        var g = source.createGraphics();
        g.setColor(Color.RED); g.fillRect(0, 0, 150, 100);
        g.setColor(Color.BLUE); g.fillRect(150, 0, 150, 100); g.dispose();
        byte[] input = png(source);
        var left = render(input, "cover", 0.0, .5); var right = render(input, "cover", 1.0, .5);
        assertEquals(900, left.getWidth()); assertEquals(1600, left.getHeight());
        assertTrue(new Color(left.getRGB(450, 800)).getRed() > 240);
        assertTrue(new Color(right.getRGB(450, 800)).getBlue() > 240);
        assertArrayEquals(renderer.render(input, "cover", 0.0, .5), renderer.render(input, "cover", 0.0, .5));
    }

    @Test void containKeepsLandscapeEdgesAndBlursBackdropWithoutBlurringForeground() throws Exception {
        var source = new BufferedImage(60, 20, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 60; x++) for (int y = 0; y < 20; y++)
            source.setRGB(x, y, x % 2 == 0 ? Color.BLACK.getRGB() : Color.WHITE.getRGB());
        var output = render(png(source), "contain", null, null);
        // Entire 60:20 poster fits at y=650..949; both outer edges survive as sharp foreground.
        assertTrue(new Color(output.getRGB(3, 800)).getRed() < 50);
        assertTrue(new Color(output.getRGB(896, 800)).getRed() > 200);
        int backdropA = new Color(output.getRGB(450, 300)).getRed();
        int backdropB = new Color(output.getRGB(465, 300)).getRed();
        assertTrue(backdropA > 50 && backdropA < 205, "backdrop should blend the black/white stripes");
        assertTrue(Math.abs(backdropA - backdropB) < 50, "backdrop should smooth adjacent stripes");
    }

    @Test void rejectsInvalidFocusAndImagesBeforeRendering() {
        assertThrows(ResponseStatusException.class, () -> renderer.render(new byte[]{1}, "cover", Double.NaN, .5));
        assertThrows(ResponseStatusException.class, () -> renderer.render(new byte[]{1}, "contain", .5, 2.0));
        assertThrows(ResponseStatusException.class, () -> renderer.render(new byte[]{1}, "contain", null, null));
    }
}
