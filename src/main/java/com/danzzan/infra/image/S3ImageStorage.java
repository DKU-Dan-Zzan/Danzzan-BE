package com.danzzan.infra.image;

import com.danzzan.infra.s3.S3PosterImageReader;
import com.danzzan.infra.s3.S3Uploader;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/** Retains the existing S3 uploader, object keys, URLs and poster reader unchanged. */
@Component
@Profile("!local-storage | prod")
@RequiredArgsConstructor
public class S3ImageStorage implements ImageStorage {
    private final S3Uploader uploader;
    private final S3PosterImageReader reader;

    @Override
    public String uploadPoster(Long festivalSettingId, MultipartFile file) {
        return uploader.uploadFestivalPoster(festivalSettingId, file).url();
    }

    @Override
    public String uploadLogo(Long festivalSettingId, MultipartFile file) {
        return uploader.uploadFestivalLogo(festivalSettingId, file).url();
    }

    @Override
    public byte[] readPoster(Long festivalSettingId, String posterUrl) {
        return reader.read(festivalSettingId, posterUrl);
    }
}
