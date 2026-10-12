package com.danzzan.infra.image;

import org.springframework.web.multipart.MultipartFile;

/** Storage operations for official festival branding, home images, and poster analysis. */
public interface ImageStorage {
    String uploadPoster(Long festivalSettingId, MultipartFile file);

    String uploadLogo(Long festivalSettingId, MultipartFile file);

    String uploadHomeImage(Long festivalSettingId, MultipartFile file);

    byte[] readPoster(Long festivalSettingId, String posterUrl);
}
