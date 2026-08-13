package com.danzzan.domain.admin.map.service;

import com.danzzan.domain.admin.map.dto.request.CreateAdminBoothRequest;
import com.danzzan.domain.admin.map.dto.request.CreateAdminPubRequest;
import com.danzzan.domain.admin.map.dto.request.PresignAdminPubImageRequest;
import com.danzzan.domain.admin.map.dto.request.RegisterAdminPubImagesRequest;
import com.danzzan.domain.admin.map.dto.request.UpdateAdminBoothRequest;
import com.danzzan.domain.admin.map.dto.request.UpdateAdminPubRequest;
import com.danzzan.domain.admin.map.dto.request.UpsertAdminPubOperationRequest;
import com.danzzan.domain.admin.map.dto.response.AdminBoothManagementBoothResponse;
import com.danzzan.domain.admin.map.dto.response.AdminBoothManagementPubResponse;
import com.danzzan.domain.admin.map.dto.response.AdminBoothManagementResponse;
import com.danzzan.domain.admin.map.dto.response.AdminCollegeOptionResponse;
import com.danzzan.domain.admin.map.dto.response.AdminPubImageResponse;
import com.danzzan.domain.admin.map.dto.response.AdminPubOperationResponse;
import com.danzzan.domain.boothmap.model.entity.Booth;
import com.danzzan.domain.boothmap.model.entity.BoothOperation;
import com.danzzan.domain.boothmap.model.entity.BoothOperationStatus;
import com.danzzan.domain.boothmap.model.entity.BoothType;
import com.danzzan.domain.boothmap.model.entity.College;
import com.danzzan.domain.boothmap.model.entity.Pub;
import com.danzzan.domain.boothmap.model.entity.PubDisplayDay;
import com.danzzan.domain.boothmap.model.entity.PubImage;
import com.danzzan.domain.boothmap.model.entity.PubOperation;
import com.danzzan.domain.boothmap.repository.BoothOperationRepository;
import com.danzzan.domain.boothmap.repository.BoothRepository;
import com.danzzan.domain.boothmap.repository.CollegeRepository;
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
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminBoothManagementService {
    private final BoothRepository boothRepository;
    private final BoothOperationRepository boothOperationRepository;
    private final CollegeRepository collegeRepository;
    private final PubRepository pubRepository;
    private final PubImageRepository pubImageRepository;
    private final PubOperationRepository pubOperationRepository;
    private final S3PresignService s3PresignService;
    private final S3Uploader s3Uploader;

    public AdminBoothManagementResponse getBoothManagement(LocalDate operationDate) {
        List<Booth> booths = boothRepository.findAll();
        List<Pub> pubs = pubRepository.findAllWithCollegeAndDisplayDays();
        List<PubOperation> pubOperations = pubOperationRepository.findAllByOrderByOperationDateAsc();
        List<AdminCollegeOptionResponse> colleges = collegeRepository.findAll().stream()
                .map(college -> new AdminCollegeOptionResponse(college.getId(), college.getName()))
                .toList();
        Long selectedPubOperationId = pubOperations.stream()
                .filter(pubOperation -> operationDate != null && pubOperation.getOperationDate().equals(operationDate))
                .map(PubOperation::getId)
                .findFirst()
                .orElse(null);

        Map<Long, BoothOperation> boothOperationsByBoothId = resolveBoothOperationsByBoothId(operationDate);

        List<AdminBoothManagementBoothResponse> boothResponses = booths.stream()
                .map(booth -> {
                    BoothOperation operation = boothOperationsByBoothId.get(booth.getId());
                    return new AdminBoothManagementBoothResponse(
                            booth.getId(),
                            booth.getType().name(),
                            booth.getName(),
                            booth.getDescription(),
                            booth.getLocationX(),
                            booth.getLocationY(),
                            booth.hasLocation(),
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
                        pub.getCollege().getId(),
                        pub.getName(),
                        pub.getIntro(),
                        pub.getDescription(),
                        pub.getCollege().getName(),
                        pub.getDepartment(),
                        pub.getInstagram(),
                        isVisibleOnSelectedDate(pub, selectedPubOperationId),
                        pub.getDisplayDays().stream()
                                .map(PubDisplayDay::getPubOperation)
                                .map(PubOperation::getId)
                                .distinct()
                                .sorted()
                                .toList()
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

        return new AdminBoothManagementResponse(boothResponses, pubResponses, pubOperationResponses, colleges);
    }

    @Transactional
    public Long createPubManagement(CreateAdminPubRequest request) {
        College college = collegeRepository.findById(request.getCollegeId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 단과대입니다."));
        List<PubOperation> displayOperations = resolveDisplayOperations(request.getDisplayOperationIds());

        Pub pub = new Pub(
                college,
                normalizeRequiredText(request.getDepartment(), ""),
                normalizeRequiredText(request.getName(), ""),
                normalizeNullableText(request.getIntro()),
                normalizeNullableText(request.getDescription()),
                normalizeNullableText(request.getInstagram())
        );
        pub.replaceDisplayDays(displayOperations);
        return pubRepository.save(pub).getId();
    }

    @Transactional
    public Long createBoothManagement(CreateAdminBoothRequest request) {
        validateTimeRange(request.getStartTime(), request.getEndTime());

        Set<LocalDate> distinctOperationDates = new LinkedHashSet<>(request.getOperationDates());
        if (distinctOperationDates.isEmpty()) {
            throw new IllegalArgumentException("운영 날짜를 최소 1개 이상 선택해야 합니다.");
        }

        List<PubOperation> supportedOperations = pubOperationRepository.findAllByOperationDateIn(distinctOperationDates);
        Set<LocalDate> supportedOperationDates = supportedOperations.stream()
                .map(PubOperation::getOperationDate)
                .collect(Collectors.toCollection(HashSet::new));
        if (supportedOperationDates.size() != distinctOperationDates.size()
                || !supportedOperationDates.containsAll(distinctOperationDates)) {
            throw new IllegalArgumentException("지원하지 않는 운영 날짜가 포함되어 있습니다.");
        }

        Booth booth = boothRepository.save(new Booth(
                normalizeRequiredText(request.getName(), ""),
                request.getType(),
                request.getType() == BoothType.FOOD_TRUCK ? normalizeNullableText(request.getDescription()) : null,
                null,
                null,
                null
        ));

        List<BoothOperation> operations = distinctOperationDates.stream()
                .map(operationDate -> new BoothOperation(
                        booth,
                        operationDate,
                        request.getOperationStatus(),
                        request.getStartTime(),
                        request.getEndTime()
                ))
                .toList();
        boothOperationRepository.saveAll(operations);

        return booth.getId();
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
        List<PubOperation> displayOperations = resolveDisplayOperations(request.getDisplayOperationIds());

        pub.updateAdminInfo(
                normalizeRequiredText(request.getName(), pub.getName()),
                normalizeNullableText(request.getIntro()),
                normalizeNullableText(request.getDescription()),
                normalizeNullableText(request.getInstagram())
        );
        pub.replaceDisplayDays(displayOperations);
    }

    @Transactional
    public void hidePubManagement(Long pubId) {
        Pub pub = pubRepository.findById(pubId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 주점입니다."));
        pub.replaceDisplayDays(List.of());
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
            throw new IllegalArgumentException("해당 날짜의 주점 공통 운영 정보가 이미 존재합니다.");
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
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 주점 공통 운영 정보입니다."));

        pubOperationRepository.findByOperationDate(request.getOperationDate())
                .filter(existing -> !existing.getId().equals(pubOperationId))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("해당 날짜의 주점 공통 운영 정보가 이미 존재합니다.");
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
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 주점 공통 운영 정보입니다."));
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
        // 자정을 넘기는 운영(예: 11:00 ~ 01:00)도 허용하기 위해 검증을 두지 않음
    }

    private void validateLocation(Double locationX, Double locationY) {
        if (locationX == null || locationY == null) {
            throw new IllegalArgumentException("좌표 값이 비어 있을 수 없습니다.");
        }

        if (locationX < -180 || locationX > 180) {
            throw new IllegalArgumentException("경도(locationX) 범위가 올바르지 않습니다.");
        }

        if (locationY < -90 || locationY > 90) {
            throw new IllegalArgumentException("위도(locationY) 범위가 올바르지 않습니다.");
        }
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

    private boolean isVisibleOnSelectedDate(Pub pub, Long pubOperationId) {
        if (pubOperationId == null) {
            return false;
        }

        return pub.getDisplayDays().stream()
                .map(PubDisplayDay::getPubOperation)
                .map(PubOperation::getId)
                .anyMatch(pubOperationId::equals);
    }

    private List<PubOperation> resolveDisplayOperations(List<Long> displayOperationIds) {
        Set<Long> distinctIds = new LinkedHashSet<>(displayOperationIds);
        List<PubOperation> operations = pubOperationRepository.findAllById(distinctIds);
        if (operations.size() != distinctIds.size()) {
            throw new IllegalArgumentException("존재하지 않는 주점 운영일이 포함되어 있습니다.");
        }

        Map<Long, PubOperation> operationById = operations.stream()
                .collect(Collectors.toMap(PubOperation::getId, Function.identity()));

        return distinctIds.stream()
                .map(operationById::get)
                .toList();
    }

    private void ensurePubExists(Long pubId) {
        if (!pubRepository.existsById(pubId)) {
            throw new IllegalArgumentException("존재하지 않는 주점입니다.");
        }
    }
}
