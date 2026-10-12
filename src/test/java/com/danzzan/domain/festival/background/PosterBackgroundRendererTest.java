package com.danzzan.domain.festival.background;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import static org.junit.jupiter.api.Assertions.*;

class PosterBackgroundRendererTest {
    final PosterBackgroundRenderer renderer = new PosterBackgroundRenderer();
    @Test void cropsStayInsideSourceAtAllEdgesAndPreserveTargetRatio() {
        for (String target : new String[]{"ticket", "closed"}) {
            var size = PosterBackgroundRenderer.Target.parse(target);
            for (int[] source : new int[][]{{1000, 2000}, {2000, 1000}, {1, 1}, {1, 2000}})
                for (double x : new double[]{0, .5, 1}) for (double y : new double[]{0, .5, 1}) {
                    var crop = renderer.cropBounds(source[0], source[1], size, x, y);
                    assertTrue(crop.x >= 0 && crop.y >= 0 && crop.width > 0 && crop.height > 0);
                    assertTrue(crop.x + crop.width <= source[0] && crop.y + crop.height <= source[1]);
                    if (source[0] > 1) assertEquals((double) size.width() / size.height(), (double) crop.width / crop.height, .003);
                }
        }
        var size = PosterBackgroundRenderer.Target.parse("ticket");
        assertEquals(0, renderer.cropBounds(1000, 2000, size, .5, 0).y);
        var bottom = renderer.cropBounds(1000, 2000, size, .5, 1);
        assertEquals(2000, bottom.y + bottom.height);
    }
    @Test void rejectsInvalidFocusAndCorruptOrOversizedPosters() {
        for (double x : new double[]{-0.1, 1.1, Double.NaN, Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class, () -> renderer.cropBounds(100, 100, new PosterBackgroundRenderer.Target(400, 180), x, .5));
        assertThrows(ResponseStatusException.class, () -> renderer.read("not an image".getBytes()));
        assertThrows(ResponseStatusException.class, () -> renderer.read(new byte[5 * 1024 * 1024 + 1]));
        assertThrows(ResponseStatusException.class, () -> PosterBackgroundRenderer.Target.parse("other"));
    }
    @Test void rendersRealJpegAtDoubleResolutionForBothTargets() throws Exception {
        var source = new BufferedImage(100, 200, BufferedImage.TYPE_INT_ARGB);
        var input = new ByteArrayOutputStream();
        ImageIO.write(source, "png", input);
        assertNotNull(renderer.read(input.toByteArray()));
        for (String target : new String[]{"ticket", "closed"}) {
            var size = PosterBackgroundRenderer.Target.parse(target);
            byte[] bytes = renderer.render(source, size, .5, .5);
            assertEquals(0xff, bytes[0] & 0xff);
            var output = ImageIO.read(new ByteArrayInputStream(bytes));
            assertEquals(size.width() * 2, output.getWidth());
            assertEquals(size.height() * 2, output.getHeight());
            assertEquals(0xffffff, output.getRGB(0, 0) & 0xffffff);
        }
    }
}
