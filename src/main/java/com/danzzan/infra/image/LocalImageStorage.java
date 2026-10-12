package com.danzzan.infra.image;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
@Profile("local-storage & !prod")
public class LocalImageStorage implements ImageStorage {
    private static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;
    private static final Pattern POSTER_SUFFIX = Pattern.compile(
            "[0-9]{8}/[a-fA-F0-9-]{36}_[A-Za-z0-9._-]+");

    private final Path root;
    private final URI publicBase;

    public LocalImageStorage(@Value("${local-image.root-directory}") String rootDirectory,
                             @Value("${local-image.public-base-url}") String publicBaseUrl) {
        root = Path.of(rootDirectory).toAbsolutePath().normalize();
        publicBase = URI.create(publicBaseUrl.replaceAll("/+$", ""));
        if (!("http".equalsIgnoreCase(publicBase.getScheme()) || "https".equalsIgnoreCase(publicBase.getScheme()))
                || publicBase.getHost() == null || !"/uploads".equals(publicBase.getRawPath())
                || publicBase.getRawQuery() != null || publicBase.getRawFragment() != null) {
            throw new IllegalArgumentException("local-image.public-base-url must be an HTTP(S) URL ending in /uploads");
        }
        try {
            Files.createDirectories(root);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to create local image directory", exception);
        }
    }

    public Path rootDirectory() {
        return root;
    }

    @Override
    public String uploadPoster(Long festivalSettingId, MultipartFile file) {
        return upload(festivalSettingId, "poster", file);
    }

    @Override
    public String uploadLogo(Long festivalSettingId, MultipartFile file) {
        return upload(festivalSettingId, "logo", file);
    }

    @Override
    public String uploadHomeImage(Long festivalSettingId, MultipartFile file) {
        return upload(festivalSettingId, "home", file);
    }

    @Override
    public com.danzzan.infra.s3.S3UploadResult uploadTicketingBackground(Long festivalSettingId, MultipartFile file) {
        String url = upload(festivalSettingId, "ticketing-background", file, 10 * 1024 * 1024);
        return new com.danzzan.infra.s3.S3UploadResult(url.substring((publicBase + "/").length()), url);
    }

    @Override
    public void validateTicketingBackground(Long festivalSettingId, String backgroundUrl) {
        try {
            URI url = URI.create(backgroundUrl);
            String prefix = publicBase + "/festival/" + festivalSettingId + "/ticketing-background/";
            if (!backgroundUrl.startsWith(prefix) || url.getRawQuery() != null || url.getRawFragment() != null
                    || !POSTER_SUFFIX.matcher(backgroundUrl.substring(prefix.length())).matches()) throw invalidBackground();
            Path source = root.resolve(backgroundUrl.substring((publicBase + "/").length())).normalize();
            if (!source.startsWith(root) || !source.toRealPath().startsWith(root.toRealPath())
                    || !Files.isRegularFile(source) || Files.size(source) == 0) throw invalidBackground();
        } catch (IOException | IllegalArgumentException | NullPointerException exception) {
            throw invalidBackground();
        }
    }

    private ResponseStatusException invalidBackground() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "업로드한 티켓팅 배경 이미지를 선택해 주세요.");
    }

    private String upload(Long festivalSettingId, String kind, MultipartFile file) {
        return upload(festivalSettingId, kind, file, MAX_IMAGE_BYTES);
    }

    private String upload(Long festivalSettingId, String kind, MultipartFile file, int maxBytes) {
        if (festivalSettingId == null || festivalSettingId <= 0 || file == null || file.isEmpty()
                || file.getSize() > maxBytes) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use a non-empty image up to " + (maxBytes / 1024 / 1024) + " MB");
        }
        String original = file.getOriginalFilename();
        String safeName = original == null || original.isBlank() ? "file"
                : original.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (safeName.length() > 120) safeName = safeName.substring(safeName.length() - 120);
        String key = "festival/" + festivalSettingId + "/" + kind + "/"
                + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "/"
                + UUID.randomUUID() + "_" + safeName;
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) throw new IllegalStateException("Invalid local image path");
        try {
            Files.createDirectories(target.getParent());
            if (!target.getParent().toRealPath().startsWith(root.toRealPath())) {
                throw new IllegalStateException("Invalid local image directory");
            }
            try (var input = file.getInputStream()) {
                Files.copy(input, target);
            }
            return publicBase + "/" + key;
        } catch (IOException exception) {
            try { Files.deleteIfExists(target); } catch (IOException cleanup) { exception.addSuppressed(cleanup); }
            throw new IllegalStateException("Unable to save local image", exception);
        }
    }

    @Override
    public byte[] readPoster(Long festivalSettingId, String posterUrl) {
        String key = posterKey(festivalSettingId, posterUrl);
        try {
            Path source = root.resolve(key).normalize();
            if (!source.startsWith(root) || !source.toRealPath().startsWith(root.toRealPath())) throw invalidPoster();
            if (Files.size(source) > MAX_IMAGE_BYTES) throw invalidPoster();
            try (var input = Files.newInputStream(source)) {
                byte[] bytes = input.readNBytes(MAX_IMAGE_BYTES + 1);
                if (bytes.length == 0 || bytes.length > MAX_IMAGE_BYTES) throw invalidPoster();
                return bytes;
            }
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Saved local poster image is unavailable", exception);
        }
    }

    String posterKey(Long festivalSettingId, String posterUrl) {
        try {
            URI url = URI.create(posterUrl);
            String prefix = "/uploads/festival/" + festivalSettingId + "/poster/";
            if (!publicBase.getScheme().equalsIgnoreCase(url.getScheme())
                    || !publicBase.getRawAuthority().equals(url.getRawAuthority())
                    || url.getRawPath() == null || !url.getRawPath().startsWith(prefix)
                    || url.getRawQuery() != null || url.getRawFragment() != null) throw invalidPoster();
            String suffix = url.getRawPath().substring(prefix.length());
            if (!POSTER_SUFFIX.matcher(suffix).matches()) throw invalidPoster();
            return "festival/" + festivalSettingId + "/poster/" + suffix;
        } catch (IllegalArgumentException exception) {
            throw invalidPoster();
        }
    }

    private ResponseStatusException invalidPoster() {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Saved poster URL does not identify a valid local festival poster");
    }
}
