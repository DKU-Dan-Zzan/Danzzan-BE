package com.danzzan.domain.boothmap.service;

import com.danzzan.domain.boothmap.model.dto.BoothSummaryResponse;
import com.danzzan.domain.boothmap.model.entity.Booth;
import com.danzzan.domain.boothmap.model.entity.BoothOperation;
import com.danzzan.domain.boothmap.model.entity.BoothOperationStatus;
import com.danzzan.domain.boothmap.repository.BoothOperationRepository;
import com.danzzan.domain.boothmap.repository.BoothRepository;
import com.danzzan.domain.boothmap.util.ThumbnailUrlResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BoothService {
    private final BoothRepository boothRepository;
    private final BoothOperationRepository boothOperationRepository;

    public BoothSummaryResponse getBoothSummary(Long boothId, LocalDate operationDate) {
        Booth booth = boothRepository.findById(boothId)
                .orElseThrow(() -> new IllegalArgumentException("해당 부스를 찾을 수 없습니다. id=" + boothId));
        BoothOperation boothOperation = (operationDate == null)
                ? boothOperationRepository.findFirstByBoothIdOrderByOperationDateAsc(boothId).orElse(null)
                : boothOperationRepository.findByBoothIdAndOperationDate(boothId, operationDate).orElse(null);

        return new BoothSummaryResponse(
                booth.getId(),
                booth.getName(),
                booth.getDescription(),
                booth.getImageUrl(),
                ThumbnailUrlResolver.toThumbnailUrl(booth.getImageUrl()),
                boothOperation != null ? boothOperation.getOperationStatus() : BoothOperationStatus.UNKNOWN,
                boothOperation != null ? formatTime(boothOperation.getStartTime()) : null,
                boothOperation != null ? formatTime(boothOperation.getEndTime()) : null
        );
    }

    private String formatTime(LocalTime time) {
        return time == null ? null : time.toString();
    }
}
