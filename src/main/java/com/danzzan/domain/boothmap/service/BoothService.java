package com.danzzan.domain.boothmap.service;

import com.danzzan.domain.boothmap.model.dto.BoothSummaryResponse;
import com.danzzan.domain.boothmap.model.entity.Booth;
import com.danzzan.domain.boothmap.repository.BoothRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BoothService {
    private final BoothRepository boothRepository;

    public BoothSummaryResponse getBoothSummary(Long boothId) {
        Booth booth = boothRepository.findById(boothId)
                .orElseThrow(() -> new IllegalArgumentException("해당 부스를 찾을 수 없습니다. id=" + boothId));
        
        return new BoothSummaryResponse(
                booth.getId(),
                booth.getName(),
                booth.getDescription(),
                booth.getImageUrl()
        );
    }
}