package com.danzzan.domain.admin.map.service;

import com.danzzan.domain.admin.map.dto.request.UpdateMapLocationRequest;
import com.danzzan.domain.admin.map.dto.response.AdminMapBoothResponse;
import com.danzzan.domain.admin.map.dto.response.AdminMapCollegeResponse;
import com.danzzan.domain.admin.map.dto.response.AdminMapResponse;
import com.danzzan.domain.boothmap.model.entity.Booth;
import com.danzzan.domain.boothmap.model.entity.College;
import com.danzzan.domain.boothmap.repository.BoothRepository;
import com.danzzan.domain.boothmap.repository.CollegeRepository;
import com.danzzan.domain.timetable.service.TimetableDisplaySettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminMapService {
    private static final LocalDate DEFAULT_OPERATION_DATE = LocalDate.of(2026, 5, 12);

    private final CollegeRepository collegeRepository;
    private final BoothRepository boothRepository;
    private final TimetableDisplaySettingService timetableDisplaySettingService;

    public AdminMapResponse getAdminMap(LocalDate operationDate) {
        LocalDate resolvedOperationDate = resolveOperationDate(operationDate);

        List<AdminMapCollegeResponse> colleges = collegeRepository.findAll().stream()
                .map(college -> new AdminMapCollegeResponse(
                        college.getId(),
                        college.getName(),
                        college.getLocationX(),
                        college.getLocationY()
                ))
                .toList();

        List<AdminMapBoothResponse> booths = boothRepository.findAllByOperationDate(resolvedOperationDate).stream()
                .map(booth -> new AdminMapBoothResponse(
                        booth.getId(),
                        booth.getName(),
                        booth.getType().name(),
                        booth.getLocationX(),
                        booth.getLocationY(),
                        booth.getLocationX() != null && booth.getLocationY() != null
                ))
                .toList();

        return new AdminMapResponse(
                timetableDisplaySettingService.isComingSoonOverlayEnabled(),
                colleges,
                booths
        );
    }

    @Transactional
    public void updateCollegeLocation(Long collegeId, UpdateMapLocationRequest request) {
        College college = collegeRepository.findById(collegeId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 단과대입니다."));

        validateLocation(request.getLocationX(), request.getLocationY());
        college.updateLocation(request.getLocationX(), request.getLocationY());
    }

    @Transactional
    public void updateBoothLocation(Long boothId, UpdateMapLocationRequest request) {
        Booth booth = boothRepository.findById(boothId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 부스입니다."));

        validateLocation(request.getLocationX(), request.getLocationY());
        booth.updateLocation(request.getLocationX(), request.getLocationY());
    }

    @Transactional
    public void clearBoothLocation(Long boothId) {
        Booth booth = boothRepository.findById(boothId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 부스입니다."));

        booth.clearLocation();
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

    private LocalDate resolveOperationDate(LocalDate operationDate) {
        if (operationDate != null) {
            return operationDate;
        }

        return DEFAULT_OPERATION_DATE;
    }
}
