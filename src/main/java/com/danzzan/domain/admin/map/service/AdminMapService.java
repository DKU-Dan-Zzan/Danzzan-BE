package com.danzzan.domain.admin.map.service;

import com.danzzan.domain.admin.map.dto.request.UpdateMapLocationRequest;
import com.danzzan.domain.admin.map.dto.request.UpdateActiveOperationDateRequest;
import com.danzzan.domain.admin.map.dto.response.AdminMapBoothResponse;
import com.danzzan.domain.admin.map.dto.response.AdminMapCollegeResponse;
import com.danzzan.domain.admin.map.dto.response.AdminMapResponse;
import com.danzzan.domain.boothmap.model.entity.Booth;
import com.danzzan.domain.boothmap.model.entity.College;
import com.danzzan.domain.admin.map.model.entity.FestivalMapSetting;
import com.danzzan.domain.boothmap.repository.BoothRepository;
import com.danzzan.domain.boothmap.repository.CollegeRepository;
import com.danzzan.domain.admin.map.repository.FestivalMapSettingRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminMapService {
    private final CollegeRepository collegeRepository;
    private final BoothRepository boothRepository;
    private final FestivalMapSettingRepository festivalMapSettingRepository;

    public AdminMapResponse getAdminMap(LocalDate operationDate) {
        FestivalMapSetting setting = getSetting();

        List<AdminMapCollegeResponse> colleges = collegeRepository.findAll().stream()
                .map(college -> new AdminMapCollegeResponse(
                        college.getId(),
                        college.getName(),
                        college.getLocationX(),
                        college.getLocationY()
                ))
                .toList();

        List<AdminMapBoothResponse> booths = boothRepository.findAllByOperationDate(operationDate).stream()
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
                setting.getActiveOperationDate().toString(),
                colleges,
                booths
        );
    }

    @Transactional
    public void updateCollegeLocation(Long collegeId, UpdateMapLocationRequest request) {
        College college = collegeRepository.findById(collegeId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 단과대학입니다."));

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
            throw new IllegalArgumentException("좌표 값은 비어 있을 수 없습니다.");
        }

        if (locationX < -180 || locationX > 180) {
            throw new IllegalArgumentException("경도(locationX) 범위가 올바르지 않습니다.");
        }

        if (locationY < -90 || locationY > 90) {
            throw new IllegalArgumentException("위도(locationY) 범위가 올바르지 않습니다.");
        }
    }

    private FestivalMapSetting getSetting() {
        return festivalMapSettingRepository.findById(1L)
                .orElseThrow(() -> new IllegalArgumentException("지도 설정 정보가 존재하지 않습니다."));
    }

    public LocalDate getActiveOperationDate() {
        return getSetting().getActiveOperationDate();
    }

    @Transactional
    public void updateActiveDate(UpdateActiveOperationDateRequest request) {
        LocalDate operationDate = LocalDate.parse(request.getOperationDate());

        FestivalMapSetting setting = getSetting();
        setting.updateActiveOperationDate(operationDate);
    }
}