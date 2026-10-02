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

    @Value("${nhn.object-storage.endpoint}")
    private String endpoint;

    @Value("${nhn.object-storage.container}")
    private String bucket;

    @Value("${nhn.object-storage.swift-auth-path:}")
    private String swiftAuthPath;

    @Value("${nhn.object-storage.public-base-url:}")
    private String publicBaseUrl;

    @Value("${nhn.object-storage.public-read:false}")
    private boolean publicRead;

    public S3UploadResult uploadTicketingBackground(MultipartFile file) {
        return upload("festival/ticketing-background", file);
    }

    public S3UploadResult uploadNoticeImage(MultipartFile file) {
        return upload("notices/images", file);
    }

    public S3UploadResult uploadAdImage(MultipartFile file) {
        return upload("ads", file);
    }

    public S3UploadResult uploadPubImage(String collegeName, String department, MultipartFile file) {
        String prefix = "pub-images/" + sanitize(collegeName) + "/" + sanitize(department);
        return upload(prefix, file);
    }

    public S3UploadResult uploadArtistImage(Long artistId, MultipartFile file) {
        return upload("artists/" + artistId, file);
    }

    private S3UploadResult upload(String prefix, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("업로드할 파일이 없습니다.");
        }

        String key = buildKey(prefix, file.getOriginalFilename());

        PutObjectRequest.Builder req = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(file.getContentType());

        if (publicRead) {
            req = req.acl(ObjectCannedACL.PUBLIC_READ);
        }

        try {
            byte[] bytes = file.getBytes();
            s3Client.putObject(req.build(), RequestBody.fromBytes(bytes));
        } catch (IOException e) {
            throw new IllegalStateException("이미지 업로드에 실패했습니다.", e);
        }

        String url;
        if (publicBaseUrl != null && !publicBaseUrl.isBlank()) {
            url = publicBaseUrl.replaceAll("/+$", "") + "/" + key;
        } else if (swiftAuthPath != null && !swiftAuthPath.isBlank()) {
            url = endpoint + "/" + swiftAuthPath + "/" + bucket + "/" + key;
        } else {
            url = s3Client.utilities()
                    .getUrl(GetUrlRequest.builder().bucket(bucket).key(key).build())
                    .toExternalForm();
        }

        return new S3UploadResult(key, url);
    }

    private String sanitize(String value) {
        if (value == null || value.isBlank()) return "unknown";
        return value.replaceAll("[^a-zA-Z0-9가-힣._-]", "_");
    }

    private String buildKey(String prefix, String originalFilename) {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String safeName = (originalFilename == null || originalFilename.isBlank())
                ? "file"
                : originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_");
        return prefix + "/" + date + "/" + UUID.randomUUID() + "_" + safeName;
    }
}

