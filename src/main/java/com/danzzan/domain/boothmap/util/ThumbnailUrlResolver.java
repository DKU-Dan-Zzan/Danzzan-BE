package com.danzzan.domain.boothmap.util;

public final class ThumbnailUrlResolver {
    private ThumbnailUrlResolver() {
    }

    public static String toThumbnailUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }

        int queryIndex = imageUrl.indexOf('?');
        int hashIndex = imageUrl.indexOf('#');
        int suffixIndex = -1;

        if (queryIndex >= 0 && hashIndex >= 0) {
            suffixIndex = Math.min(queryIndex, hashIndex);
        } else if (queryIndex >= 0) {
            suffixIndex = queryIndex;
        } else if (hashIndex >= 0) {
            suffixIndex = hashIndex;
        }

        String baseUrl = suffixIndex >= 0 ? imageUrl.substring(0, suffixIndex) : imageUrl;
        String suffix = suffixIndex >= 0 ? imageUrl.substring(suffixIndex) : "";

        int lastSlashIndex = baseUrl.lastIndexOf('/');
        if (lastSlashIndex < 0 || lastSlashIndex == baseUrl.length() - 1) {
            return imageUrl;
        }

        String directory = baseUrl.substring(0, lastSlashIndex + 1);
        String filename = baseUrl.substring(lastSlashIndex + 1);
        int extensionIndex = filename.lastIndexOf('.');
        String baseName = extensionIndex > 0 ? filename.substring(0, extensionIndex) : filename;
        String thumbnailDirectory = directory.endsWith("/thumb/") ? directory : directory + "thumb/";

        return thumbnailDirectory + baseName + ".webp" + suffix;
    }
}
