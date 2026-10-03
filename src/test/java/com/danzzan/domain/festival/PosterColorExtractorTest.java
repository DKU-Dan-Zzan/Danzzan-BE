package com.danzzan.domain.festival;

import com.danzzan.domain.festival.service.PosterColorExtractor;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

class PosterColorExtractorTest {
    private final PosterColorExtractor extractor = new PosterColorExtractor();

    @Test void extractsDiverseColorsWithoutLettingWhiteBorderDominate() throws Exception {
        Color[] palette = {new Color(225, 43, 33), new Color(25, 115, 220), new Color(47, 171, 90),
                new Color(242, 186, 36), new Color(137, 63, 180), new Color(22, 183, 186)};
        BufferedImage poster = new BufferedImage(240, 180, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 180; y++) for (int x = 0; x < 240; x++) {
            int rgb = x < 25 || y < 25 || x >= 215 || y >= 155
                    ? Color.WHITE.getRGB() : palette[((x - 25) / 64 + (y - 25) / 44) % palette.length].getRGB();
            poster.setRGB(x, y, rgb);
        }
        var colors = extractor.extract(png(poster)).colors();
        assertTrue(colors.size() >= 4 && colors.size() <= 8);
        assertEquals(colors.size(), new HashSet<>(colors.stream().map(c -> c.hex()).toList()).size());
        assertTrue(colors.stream().allMatch(c -> c.hex().matches("#[0-9A-F]{6}") && c.population() > 0 && c.population() <= 1));
        assertNotEquals("#FFFFFF", colors.get(0).hex());
    }

    @Test void mergesSimilarColorsAndRetainsSmallAccentOnDarkPoster() throws Exception {
        BufferedImage poster = new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
            int rgb = x < 60 ? new Color(19, 19, 22).getRGB()
                    : x < 70 ? new Color(224, 51 + x % 3, 31).getRGB()
                    : y < 42 ? new Color(43, 121, 207).getRGB()
                    : y < 84 ? new Color(230, 190, 30).getRGB() : new Color(46, 162, 80).getRGB();
            poster.setRGB(x, y, rgb);
        }
        var colors = extractor.extract(png(poster)).colors();
        assertTrue(colors.stream().anyMatch(c -> {
            Color color = Color.decode(c.hex());
            return color.getRed() > 200 && color.getGreen() < 90 && color.getBlue() < 90;
        }));
        assertEquals(1, colors.stream().filter(c -> {
            Color color = Color.decode(c.hex());
            return color.getRed() > 200 && color.getGreen() < 90 && color.getBlue() < 90;
        }).count());
        assertNotEquals("#131316", colors.get(0).hex());
    }

    @Test void rejectsUnusableOrFullyTransparentImage() throws Exception {
        assertThrows(ResponseStatusException.class, () -> extractor.extract("not an image".getBytes()));
        BufferedImage transparent = new BufferedImage(30, 30, BufferedImage.TYPE_INT_ARGB);
        assertThrows(ResponseStatusException.class, () -> extractor.extract(png(transparent)));
    }

    private byte[] png(BufferedImage image) throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
