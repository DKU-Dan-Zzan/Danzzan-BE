package com.danzzan.infra.s3;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetUrlRequest;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class S3PresignService {

    private static final long MAX_IMAGE_SIZE_BYTES = 5L * 1024 * 1024; // 5MB
    private static final Set<String> ALLOWED_IMAGE_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    private final S3Presigner s3Presigner;
    private final S3Client s3Client;
    private final S3PathGenerator s3PathGenerator;

    @Value("${aws.s3.bucket}")
    private String bucket;

    /**
     * true면 업로드 시 객체 ACL을 PUBLIC_READ로 설정합니다.
     * (버킷 정책으로 공개를 관리한다면 false로 두세요)
     */
    @Value("${aws.s3.public-read:false}")
    private boolean publicRead;

    @Value("${aws.s3.presign.put-expiration-sec:600}")
    private long putExpirationSec;

    /**
     * 공지 대표 이미지 Presigned PUT URL 발급.
     * (파일 크기는 클라이언트에서만 체크한다고 가정, 타입만 검증)
     */
    public S3PresignedPutResult presignPutNoticeImage(String fileName, String contentType) {
        validateImageContentType(contentType);
        String key = s3PathGenerator.generateNoticeImageKey(fileName);
        return presignPutObject(key, contentType);
    }

    /**
     * 광고 이미지 Presigned PUT URL 발급.
     * - 이미지 타입 검증
     * - 5MB 제한 (fileSize가 넘어오면 서버에서도 1차 필터링)
     */
    public S3PresignedPutResult presignPutAdImage(String fileName, String contentType, Long fileSize) {
        validateImageContentType(contentType);
        validateMaxSize(fileSize);
        String key = s3PathGenerator.generateAdImageKey(fileName);
        return presignPutObject(key, contentType);
    }

    private S3PresignedPutResult presignPutObject(String key, String contentType) {
        PutObjectRequest.Builder putReq = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key);

        if (contentType != null && !contentType.isBlank()) {
            putReq = putReq.contentType(contentType.trim());
        }
        if (publicRead) {
            putReq = putReq.acl(ObjectCannedACL.PUBLIC_READ);
        }

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(putExpirationSec))
                .putObjectRequest(putReq.build())
                .build();

        PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(presignRequest);

        String publicUrl = s3Client.utilities()
                .getUrl(GetUrlRequest.builder().bucket(bucket).key(key).build())
                .toExternalForm();

        return new S3PresignedPutResult(
                key,
                publicUrl,
                presigned.url().toExternalForm(),
                Instant.now().plusSeconds(putExpirationSec)
        );
    }

    private void validateImageContentType(String contentType) {
        if (contentType == null || !ALLOWED_IMAGE_CONTENT_TYPES.contains(contentType)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "허용되지 않은 파일 타입입니다. (image/jpeg, image/png, image/webp만 허용)"
            );
        }
    }

    private void validateMaxSize(Long fileSize) {
        if (fileSize == null) {
            return;
        }
        if (fileSize > MAX_IMAGE_SIZE_BYTES) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "파일 크기가 5MB를 초과했습니다."
            );
        }
    }
}


