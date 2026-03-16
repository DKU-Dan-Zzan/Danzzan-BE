package com.danzzan.domain.boothmap.service;

import com.danzzan.domain.boothmap.model.dto.BoothSummaryResponse;
import com.danzzan.domain.boothmap.model.entity.Booth;
import com.danzzan.domain.boothmap.model.entity.BoothOperation;
import com.danzzan.domain.boothmap.repository.BoothOperationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BoothService {
    private final BoothOperationRepository boothOperationRepository;

    public BoothSummaryResponse getBoothSummary(Long boothId, LocalDate operationDate) {
        BoothOperation boothOperation = boothOperationRepository.findByBoothIdAndOperationDate(boothId, operationDate)
                .orElseThrow(() -> new IllegalArgumentException("해당 날짜의 부스를 찾을 수 없습니다. id=" + boothId));

        Booth booth = boothOperation.getBooth();

        return new BoothSummaryResponse(
                booth.getId(),
                booth.getName(),
                booth.getDescription(),
                booth.getImageUrl(),
                formatTime(boothOperation.getStartTime()),
                formatTime(boothOperation.getEndTime())
        );
    }

    private String formatTime(LocalTime time) {
        return time == null ? null : time.toString();
    }
}