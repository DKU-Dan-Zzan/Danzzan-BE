package com.danzzan.domain.festival.service;

import com.danzzan.domain.festival.dto.response.PosterColorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Small, bounded palette analysis independent of Theme generation. */
@Component
public class PosterColorExtractor {
    private static final int MAX_SIDE = 128;
    private static final int MAX_CANDIDATES = 6;

    public PosterColorResponse extract(byte[] imageBytes) {
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(imageBytes))) {
            if (input == null) throw invalidImage();
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw invalidImage();
            var reader = readers.next();
            try {
                reader.setInput(input);
                String format = reader.getFormatName();
                if (!"png".equalsIgnoreCase(format) && !"jpeg".equalsIgnoreCase(format)) throw invalidImage();
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > 40_000_000L) throw invalidImage();
                ImageReadParam params = reader.getDefaultReadParam();
                int sample = Math.max(1, (int) Math.ceil(Math.max(width, height) / (double) MAX_SIDE));
                params.setSourceSubsampling(sample, sample, 0, 0);
                var image = reader.read(0, params);
                if (image == null) throw invalidImage();
                Map<Integer, Bucket> buckets = new HashMap<>();
                int visiblePixels = 0;
                for (int y = 0; y < image.getHeight(); y++) {
                    for (int x = 0; x < image.getWidth(); x++) {
                        int argb = image.getRGB(x, y);
                        int alpha = argb >>> 24;
                        if (alpha < 32) continue;
                        int r = (argb >>> 16) & 255, g = (argb >>> 8) & 255, b = argb & 255;
                        int key = (r >> 4 << 8) | (g >> 4 << 4) | (b >> 4);
                        double edge = Math.min(Math.min(x, image.getWidth() - 1 - x),
                                Math.min(y, image.getHeight() - 1 - y));
                        double weight = (0.55 + 0.45 * Math.min(1, edge / 12)) * alpha / 255.0;
                        buckets.computeIfAbsent(key, unused -> new Bucket()).add(r, g, b, weight);
                        visiblePixels++;
                    }
                }
                if (visiblePixels == 0) throw invalidImage();
                int totalPixels = visiblePixels;
                List<Color> colors = buckets.values().stream().map(bucket -> bucket.toColor(totalPixels))
                        .sorted(Comparator.comparingDouble(Color::score).reversed()).toList();
                List<Color> chosen = new ArrayList<>();
                // Chromatic colors lead; neutrals are a fallback for monochrome posters.
                addDistinct(colors.stream().filter(color -> !color.neutral()).toList(), chosen, 18);
                if (chosen.size() < 4) addDistinct(colors, chosen, 18);
                if (chosen.size() < 4) addDistinct(colors, chosen, 12);
                return new PosterColorResponse(chosen.stream().map(color -> new PosterColorResponse.ColorCandidate(
                        String.format("#%02X%02X%02X", color.r(), color.g(), color.b()),
                        Math.round(color.population() * 10_000) / 10_000.0)).toList());
            } finally { reader.dispose(); }
        } catch (IOException | IllegalArgumentException exception) {
            throw invalidImage();
        }
    }

    private void addDistinct(List<Color> colors, List<Color> chosen, double minimumDistance) {
        for (Color color : colors) {
            if (chosen.size() == MAX_CANDIDATES) return;
            if (chosen.stream().allMatch(existing -> distance(existing.lab(), color.lab()) >= minimumDistance)) {
                chosen.add(color);
            }
        }
    }

    private static double distance(double[] first, double[] second) {
        double l = first[0] - second[0], a = first[1] - second[1], b = first[2] - second[2];
        return Math.sqrt(l * l + a * a + b * b);
    }

    private static double[] lab(int r, int g, int b) {
        double red = linear(r), green = linear(g), blue = linear(b);
        double x = (0.4124564 * red + 0.3575761 * green + 0.1804375 * blue) / 0.95047;
        double y = 0.2126729 * red + 0.7151522 * green + 0.0721750 * blue;
        double z = (0.0193339 * red + 0.1191920 * green + 0.9503041 * blue) / 1.08883;
        double fx = curve(x), fy = curve(y), fz = curve(z);
        return new double[]{116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz)};
    }

    private static double linear(int channel) {
        double value = channel / 255.0;
        return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }

    private static double curve(double value) {
        return value > 0.008856 ? Math.cbrt(value) : (7.787 * value + 16.0 / 116);
    }

    private ResponseStatusException invalidImage() {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "The saved poster is not a readable JPG or PNG image");
    }

    private static final class Bucket {
        double red, green, blue, weight;
        int count;

        void add(int r, int g, int b, double sampleWeight) {
            red += r * sampleWeight; green += g * sampleWeight; blue += b * sampleWeight;
            weight += sampleWeight; count++;
        }

        Color toColor(int total) {
            int r = (int) Math.round(red / weight), g = (int) Math.round(green / weight);
            int b = (int) Math.round(blue / weight);
            double[] lab = lab(r, g, b);
            double chroma = Math.hypot(lab[1], lab[2]);
            boolean neutral = lab[0] < 12 || (lab[0] > 92 && chroma < 16);
            double score = Math.sqrt(weight) * (1 + Math.min(chroma, 90) / 45);
            return new Color(r, g, b, count / (double) total, lab, neutral, score);
        }
    }

    private record Color(int r, int g, int b, double population, double[] lab, boolean neutral, double score) {}
}
