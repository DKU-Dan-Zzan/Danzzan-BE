package com.danzzan.infra.s3;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetUrlRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class S3PresignService {

    private static final long MAX_IMAGE_SIZE_BYTES = 5L * 1024 * 1024; // 5MB
    private static final Set<String> ALLOWED_IMAGE_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/jpg",
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
     * 공지 이미지 Presigned PUT URL 발급.
     * - contentType이 있으면 이미지 타입 검증 (image/jpeg, image/jpg, image/png, image/webp)
     * - fileSize가 있으면 5MB 제한 검증
     */
    public S3PresignedPutResult presignPutNoticeImage(String fileName, String contentType, Long fileSize) {
        if (contentType != null && !contentType.isBlank()) {
            validateImageContentType(contentType.trim());
        }
        validateMaxSize(fileSize);
        ensureBucketConfigured();
        String key = s3PathGenerator.generateNoticeImageKey(fileName);
        try {
            return presignPutObject(key, contentType);
        } catch (Exception e) {
            log.warn("공지 이미지 presign 실패 fileName={} contentType={}", fileName, contentType, e);
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "이미지 업로드 URL 발급에 실패했습니다. S3 설정(aws.s3.bucket, aws.region) 및 자격 증명을 확인해 주세요."
            );
        }
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
        // (중요) Presigned PUT은 서명에 포함되는 헤더/필드를 클라이언트가 정확히 동일하게 보내야 합니다.
        // 프론트에서 x-amz-acl 헤더가 누락/변형되는 경우 SignatureDoesNotMatch(403)가 발생할 수 있어
        // presign 경로에서는 ACL을 서명에 포함하지 않습니다. (B 정책: 객체는 private 유지)

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
        if (contentType == null || contentType.isBlank()) {
            return;
        }
        String trimmed = contentType.trim();
        if (!ALLOWED_IMAGE_CONTENT_TYPES.contains(trimmed)) {
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

    private void ensureBucketConfigured() {
        if (bucket == null || bucket.isBlank()) {
            log.error("aws.s3.bucket이 설정되지 않았습니다.");
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "S3 bucket이 설정되지 않았습니다. application 설정에 aws.s3.bucket을 추가해 주세요."
            );
        }
    }
}


