package com.danzzan.domain.admin.map.service;

import com.danzzan.domain.admin.map.dto.request.UpdateAdminBoothRequest;
import com.danzzan.domain.admin.map.dto.request.UpdateAdminPubRequest;
import com.danzzan.domain.admin.map.dto.request.UpsertAdminPubOperationRequest;
import com.danzzan.domain.admin.map.dto.response.AdminBoothManagementBoothResponse;
import com.danzzan.domain.admin.map.dto.response.AdminBoothManagementPubResponse;
import com.danzzan.domain.admin.map.dto.response.AdminBoothManagementResponse;
import com.danzzan.domain.admin.map.dto.response.AdminPubOperationResponse;
import com.danzzan.domain.boothmap.model.entity.Booth;
import com.danzzan.domain.boothmap.model.entity.BoothOperation;
import com.danzzan.domain.boothmap.model.entity.BoothOperationStatus;
import com.danzzan.domain.boothmap.model.entity.BoothType;
import com.danzzan.domain.boothmap.model.entity.Pub;
import com.danzzan.domain.boothmap.model.entity.PubOperation;
import com.danzzan.domain.boothmap.repository.BoothOperationRepository;
import com.danzzan.domain.boothmap.repository.BoothRepository;
import com.danzzan.domain.boothmap.repository.PubOperationRepository;
import com.danzzan.domain.boothmap.repository.PubRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminBoothManagementService {
    private final BoothRepository boothRepository;
    private final BoothOperationRepository boothOperationRepository;
    private final PubRepository pubRepository;
    private final PubOperationRepository pubOperationRepository;

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

    @Transactional
    public void updateBoothManagement(Long boothId, UpdateAdminBoothRequest request) {
        Booth booth = boothRepository.findById(boothId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 부스입니다."));

        validateTimeRange(request.getStartTime(), request.getEndTime());

        if (booth.getType() == BoothType.FOOD_TRUCK) {
            booth.updateDescription(normalizeNullableText(request.getDescription()));
        }

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
                normalizeNullableText(request.getIntro()),
                normalizeNullableText(request.getDescription()),
                normalizeNullableText(request.getInstagram())
        );
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
        if (startTime != null && endTime != null && !startTime.isBefore(endTime)) {
            throw new IllegalArgumentException("시작 시간은 종료 시간보다 빨라야 합니다.");
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
}
