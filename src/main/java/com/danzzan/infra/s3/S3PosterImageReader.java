package com.danzzan.infra.s3;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.io.IOException;
import java.net.URI;
import java.util.regex.Pattern;

/** Reads only an official poster object stored in our existing bucket. Never fetches an arbitrary URL. */
@Component
@RequiredArgsConstructor
public class S3PosterImageReader {
    private static final int MAX_BYTES = 5 * 1024 * 1024;
    private final S3Client s3Client;

    @Value("${nhn.object-storage.container}")
    private String bucket;

    public byte[] read(Long festivalSettingId, String posterImageUrl) {
        String key = objectKey(festivalSettingId, posterImageUrl);
        try (var stream = s3Client.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build())) {
            Long contentLength = stream.response().contentLength();
            if (contentLength != null && contentLength > MAX_BYTES) throw invalidPoster();
            byte[] bytes = stream.readNBytes(MAX_BYTES + 1);
            if (bytes.length == 0 || bytes.length > MAX_BYTES) throw invalidPoster();
            return bytes;
        } catch (IOException | SdkException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to read the saved poster image", exception);
        }
    }

    String objectKey(Long festivalSettingId, String url) {
        try {
            String path = URI.create(url).getRawPath();
            String prefix = "festival/" + festivalSettingId + "/poster/";
            int start = path == null ? -1 : path.lastIndexOf("/" + prefix);
            if (start < 0) throw invalidPoster();
            String key = path.substring(start + 1);
            String suffix = key.substring(prefix.length());
            if (!Pattern.matches("[0-9]{8}/[a-fA-F0-9-]{36}_[A-Za-z0-9._-]+", suffix)) throw invalidPoster();
            return key;
        } catch (IllegalArgumentException exception) {
            throw invalidPoster();
        }
    }

    private ResponseStatusException invalidPoster() {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Saved poster URL does not identify a valid festival poster");
    }
}
