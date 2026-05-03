package com.danzzan.domain.admin.map.service;

import com.danzzan.domain.admin.map.dto.request.PresignAdminPubImageRequest;
import com.danzzan.domain.admin.map.dto.request.RegisterAdminPubImagesRequest;
import com.danzzan.domain.admin.map.dto.request.UpdateAdminBoothRequest;
import com.danzzan.domain.admin.map.dto.request.UpdateAdminPubRequest;
import com.danzzan.domain.admin.map.dto.request.UpsertAdminPubOperationRequest;
import com.danzzan.domain.admin.map.dto.response.AdminBoothManagementBoothResponse;
import com.danzzan.domain.admin.map.dto.response.AdminBoothManagementPubResponse;
import com.danzzan.domain.admin.map.dto.response.AdminBoothManagementResponse;
import com.danzzan.domain.admin.map.dto.response.AdminPubOperationResponse;
import com.danzzan.domain.admin.map.dto.response.AdminPubImageResponse;
import com.danzzan.domain.boothmap.model.entity.Booth;
import com.danzzan.domain.boothmap.model.entity.BoothOperation;
import com.danzzan.domain.boothmap.model.entity.BoothOperationStatus;
import com.danzzan.domain.boothmap.model.entity.BoothType;
import com.danzzan.domain.boothmap.model.entity.Pub;
import com.danzzan.domain.boothmap.model.entity.PubImage;
import com.danzzan.domain.boothmap.model.entity.PubOperation;
import com.danzzan.domain.boothmap.repository.BoothOperationRepository;
import com.danzzan.domain.boothmap.repository.BoothRepository;
import com.danzzan.domain.boothmap.repository.PubImageRepository;
import com.danzzan.domain.boothmap.repository.PubOperationRepository;
import com.danzzan.domain.boothmap.repository.PubRepository;
import com.danzzan.infra.s3.S3PresignService;
import com.danzzan.infra.s3.S3PresignedPutResult;
import com.danzzan.infra.s3.S3UploadResult;
import com.danzzan.infra.s3.S3Uploader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminBoothManagementService {
    private final BoothRepository boothRepository;
    private final BoothOperationRepository boothOperationRepository;
    private final PubRepository pubRepository;
    private final PubImageRepository pubImageRepository;
    private final PubOperationRepository pubOperationRepository;
    private final S3PresignService s3PresignService;
    private final S3Uploader s3Uploader;

    public AdminBoothManagementResponse getBoothManagement(LocalDate operationDate) {
        List<Booth> booths = boothRepository.findAll();
        List<Pub> pubs = pubRepository.findAllWithCollege();
        List<PubOperation> pubOperations = pubOperationRepository.findAllByOrderByOperationDateAsc();

        Map<Long, BoothOperation> boothOperationsByBoothId = resolveBoothOperationsByBoothId(operationDate);
        boolean hasPubOperationForDate = operationDate != null
                && pubOperations.stream().anyMatch(pubOperation -> pubOperation.getOperationDate().equals(operationDate));

        List<AdminBoothManagementBoothResponse> boothResponses = booths.stream()
                .map(booth -> {
                    BoothOperation operation = boothOperationsByBoothId.get(booth.getId());
                    return new AdminBoothManagementBoothResponse(
                            booth.getId(),
                            booth.getType().name(),
                            booth.getName(),
                            booth.getDescription(),
                            operation != null,
                            operation != null ? operation.getOperationStatus() : BoothOperationStatus.UNKNOWN,
                            operation != null ? formatTime(operation.getStartTime()) : null,
                            operation != null ? formatTime(operation.getEndTime()) : null
                    );
                })
                .toList();

        List<AdminBoothManagementPubResponse> pubResponses = pubs.stream()
                .map(pub -> new AdminBoothManagementPubResponse(
                        pub.getId(),
                        "PUB",
                        pub.getName(),
                        pub.getIntro(),
                        pub.getDescription(),
                        pub.getCollege().getName(),
                        pub.getDepartment(),
                        pub.getInstagram(),
                        hasPubOperationForDate
                ))
                .toList();

        List<AdminPubOperationResponse> pubOperationResponses = pubOperations.stream()
                .map(pubOperation -> new AdminPubOperationResponse(
                        pubOperation.getId(),
                        pubOperation.getOperationDate().toString(),
                        formatTime(pubOperation.getStartTime()),
                        formatTime(pubOperation.getEndTime())
                ))
                .toList();

        return new AdminBoothManagementResponse(boothResponses, pubResponses, pubOperationResponses);
    }

    public List<AdminPubImageResponse> getPubImages(Long pubId) {
        ensurePubExists(pubId);
        return pubImageRepository.findByPubIdOrderByCreatedAtAscIdAsc(pubId)
                .stream()
                .map(AdminPubImageResponse::from)
                .toList();
    }

    public S3PresignedPutResult presignPubImage(Long pubId, PresignAdminPubImageRequest request) {
        Pub pub = pubRepository.findByIdWithCollege(pubId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 주점입니다."));

        return s3PresignService.presignPutPubImage(
                pub.getCollege().getName(),
                pub.getDepartment(),
                request.getFileName(),
                request.getContentType(),
                request.getFileSize()
        );
    }

    public S3UploadResult uploadPubImage(Long pubId, MultipartFile file) {
        Pub pub = pubRepository.findByIdWithCollege(pubId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 주점입니다."));

        return s3Uploader.uploadPubImage(pub.getCollege().getName(), pub.getDepartment(), file);
    }

    @Transactional
    public void updateBoothManagement(Long boothId, UpdateAdminBoothRequest request) {
        Booth booth = boothRepository.findById(boothId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 부스입니다."));

        validateTimeRange(request.getStartTime(), request.getEndTime());

        String nextName = normalizeRequiredText(request.getName(), booth.getName());
        String nextDescription = booth.getType() == BoothType.FOOD_TRUCK
                ? normalizeNullableText(request.getDescription())
                : booth.getDescription();
        booth.updateAdminInfo(nextName, nextDescription);

        BoothOperation boothOperation = boothOperationRepository.findByBoothIdAndOperationDate(boothId, request.getOperationDate())
                .orElseGet(() -> new BoothOperation(
                        booth,
                        request.getOperationDate(),
                        request.getOperationStatus(),
                        request.getStartTime(),
                        request.getEndTime()
                ));

        boothOperation.updateOperation(
                request.getOperationStatus(),
                request.getStartTime(),
                request.getEndTime()
        );
        boothOperationRepository.save(boothOperation);
    }

    @Transactional
    public void updatePubManagement(Long pubId, UpdateAdminPubRequest request) {
        Pub pub = pubRepository.findById(pubId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 주점입니다."));

        pub.updateAdminInfo(
                normalizeRequiredText(request.getName(), pub.getName()),
                normalizeNullableText(request.getIntro()),
                normalizeNullableText(request.getDescription()),
                normalizeNullableText(request.getInstagram())
        );
    }

    @Transactional
    public void registerPubImages(Long pubId, RegisterAdminPubImagesRequest request) {
        Pub pub = pubRepository.findById(pubId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 주점입니다."));

        List<String> imageUrls = request.getImageUrls().stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .toList();

        if (imageUrls.isEmpty()) {
            throw new IllegalArgumentException("등록할 이미지 URL이 비어 있습니다.");
        }

        String mainImageUrl = normalizeNullableText(request.getMainImageUrl());
        if (mainImageUrl != null && imageUrls.stream().noneMatch(mainImageUrl::equals)) {
            throw new IllegalArgumentException("대표 이미지 URL은 등록 대상 이미지 중 하나여야 합니다.");
        }

        List<PubImage> existingImages = pubImageRepository.findByPubIdOrderByCreatedAtAscIdAsc(pubId);
        boolean shouldReplaceMain = mainImageUrl != null;
        boolean hasExistingMain = existingImages.stream().anyMatch(PubImage::isMain);

        if (shouldReplaceMain) {
            existingImages.forEach(image -> image.updateMain(false));
        }

        List<PubImage> createdImages = new ArrayList<>();
        for (int index = 0; index < imageUrls.size(); index++) {
            String imageUrl = imageUrls.get(index);
            boolean isMain = shouldReplaceMain
                    ? imageUrl.equals(mainImageUrl)
                    : !hasExistingMain && index == 0;
            createdImages.add(new PubImage(pub, imageUrl, isMain));
        }

        pubImageRepository.saveAll(createdImages);
    }

    @Transactional
    public void updateMainPubImage(Long pubId, Long imageId) {
        List<PubImage> pubImages = pubImageRepository.findByPubIdOrderByCreatedAtAscIdAsc(pubId);
        if (pubImages.isEmpty()) {
            throw new IllegalArgumentException("등록된 주점 이미지가 없습니다.");
        }

        PubImage targetImage = pubImages.stream()
                .filter(pubImage -> pubImage.getId().equals(imageId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 주점 이미지입니다."));

        pubImages.forEach(image -> image.updateMain(false));
        targetImage.updateMain(true);
    }

    @Transactional
    public void deletePubImage(Long pubId, Long imageId) {
        PubImage pubImage = pubImageRepository.findByIdAndPubId(imageId, pubId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 주점 이미지입니다."));

        boolean wasMainImage = pubImage.isMain();
        pubImageRepository.delete(pubImage);

        if (!wasMainImage) {
            return;
        }

        pubImageRepository.findByPubIdOrderByCreatedAtAscIdAsc(pubId)
                .stream()
                .findFirst()
                .ifPresent(image -> image.updateMain(true));
    }

    @Transactional
    public void createPubOperation(UpsertAdminPubOperationRequest request) {
        validateTimeRange(request.getStartTime(), request.getEndTime());
        if (pubOperationRepository.findByOperationDate(request.getOperationDate()).isPresent()) {
            throw new IllegalArgumentException("해당 날짜의 주점 공통 운영정보가 이미 존재합니다.");
        }

        pubOperationRepository.save(new PubOperation(
                request.getOperationDate(),
                request.getStartTime(),
                request.getEndTime()
        ));
    }

    @Transactional
    public void updatePubOperation(Long pubOperationId, UpsertAdminPubOperationRequest request) {
        validateTimeRange(request.getStartTime(), request.getEndTime());

        PubOperation pubOperation = pubOperationRepository.findById(pubOperationId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 주점 공통 운영정보입니다."));

        pubOperationRepository.findByOperationDate(request.getOperationDate())
                .filter(existing -> !existing.getId().equals(pubOperationId))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("해당 날짜의 주점 공통 운영정보가 이미 존재합니다.");
                });

        pubOperation.updateOperation(
                request.getOperationDate(),
                request.getStartTime(),
                request.getEndTime()
        );
    }

    @Transactional
    public void deletePubOperation(Long pubOperationId) {
        PubOperation pubOperation = pubOperationRepository.findById(pubOperationId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 주점 공통 운영정보입니다."));
        pubOperationRepository.delete(pubOperation);
    }

    private Map<Long, BoothOperation> resolveBoothOperationsByBoothId(LocalDate operationDate) {
        if (operationDate == null) {
            return Map.of();
        }

        return boothOperationRepository.findAllWithBoothByOperationDate(operationDate)
                .stream()
                .collect(Collectors.toMap(
                        boothOperation -> boothOperation.getBooth().getId(),
                        Function.identity(),
                        (left, right) -> left
                ));
    }

    private void validateTimeRange(LocalTime startTime, LocalTime endTime) {
        // 자정을 넘기는 운영(예: 11:00 → 01:00)을 허용하기 위해 검증 제거
    }

    private String normalizeNullableText(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String formatTime(LocalTime time) {
        return time == null ? null : time.toString();
    }

    private String normalizeRequiredText(String value, String fallback) {
        String normalized = normalizeNullableText(value);
        return normalized == null ? fallback : normalized;
    }

    private void ensurePubExists(Long pubId) {
        if (!pubRepository.existsById(pubId)) {
            throw new IllegalArgumentException("존재하지 않는 주점입니다.");
        }
    }
}
