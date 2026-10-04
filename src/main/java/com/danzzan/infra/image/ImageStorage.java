package com.danzzan.infra.image;

import org.springframework.web.multipart.MultipartFile;

/** Storage operations needed by official festival branding and poster analysis. */
public interface ImageStorage {
    String uploadPoster(Long festivalSettingId, MultipartFile file);

    String uploadLogo(Long festivalSettingId, MultipartFile file);

    byte[] readPoster(Long festivalSettingId, String posterUrl);
}
