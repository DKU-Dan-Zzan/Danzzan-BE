package com.danzzan.infra.image;

import org.springframework.web.multipart.MultipartFile;

/** Storage operations for official festival branding, home images, and poster analysis. */
public interface ImageStorage {
    String uploadPoster(Long festivalSettingId, MultipartFile file);

    String uploadLogo(Long festivalSettingId, MultipartFile file);

    String uploadHomeImage(Long festivalSettingId, MultipartFile file);

    com.danzzan.infra.s3.S3UploadResult uploadTicketingBackground(Long festivalSettingId, MultipartFile file);

    /** Reject URLs outside our upload namespace or whose backing object is missing. */
    void validateTicketingBackground(Long festivalSettingId, String url);

    byte[] readPoster(Long festivalSettingId, String posterUrl);
}
