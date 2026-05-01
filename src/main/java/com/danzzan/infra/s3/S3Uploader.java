package com.danzzan.infra.s3;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetUrlRequest;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class S3Uploader {

    private final S3Client s3Client;

    @Value("${nhn.object-storage.container}")
    private String bucket;

    @Value("${nhn.object-storage.public-read:false}")
    private boolean publicRead;

    public S3UploadResult uploadNoticeImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("업로드할 파일이 없습니다.");
        }

        String key = buildKey("notices/images", file.getOriginalFilename());

        PutObjectRequest.Builder req = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(file.getContentType());

        if (publicRead) {
            req = req.acl(ObjectCannedACL.PUBLIC_READ);
        }

        try {
            s3Client.putObject(req.build(), RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        } catch (IOException e) {
            throw new IllegalStateException("S3 업로드에 실패했습니다.", e);
        }

        String url = s3Client.utilities()
                .getUrl(GetUrlRequest.builder().bucket(bucket).key(key).build())
                .toExternalForm();

        return new S3UploadResult(key, url);
    }

    private String buildKey(String prefix, String originalFilename) {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String safeName = (originalFilename == null || originalFilename.isBlank())
                ? "file"
                : originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_");
        return prefix + "/" + date + "/" + UUID.randomUUID() + "_" + safeName;
    }
}

