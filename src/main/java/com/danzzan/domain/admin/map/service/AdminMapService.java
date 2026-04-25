package com.danzzan.domain.admin.map.service;

import com.danzzan.domain.admin.map.dto.request.UpdateMapLocationRequest;
import com.danzzan.domain.admin.map.dto.response.AdminMapBoothResponse;
import com.danzzan.domain.admin.map.dto.response.AdminMapCollegeResponse;
import com.danzzan.domain.admin.map.dto.response.AdminMapResponse;
import com.danzzan.domain.boothmap.model.entity.Booth;
import com.danzzan.domain.boothmap.model.entity.BoothOperation;
import com.danzzan.domain.boothmap.model.entity.College;
import com.danzzan.domain.boothmap.repository.BoothOperationRepository;
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
    private final CollegeRepository collegeRepository;
    private final BoothRepository boothRepository;
    private final BoothOperationRepository boothOperationRepository;
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

        List<AdminMapBoothResponse> booths = (resolvedOperationDate == null
                ? List.<Booth>of()
                : boothRepository.findAllByOperationDate(resolvedOperationDate)).stream()
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
                .orElseThrow(() -> new IllegalArgumentException("議댁옱?섏? ?딅뒗 ?④낵??숈엯?덈떎."));

        validateLocation(request.getLocationX(), request.getLocationY());
        college.updateLocation(request.getLocationX(), request.getLocationY());
    }

    @Transactional
    public void updateBoothLocation(Long boothId, UpdateMapLocationRequest request) {
        Booth booth = boothRepository.findById(boothId)
                .orElseThrow(() -> new IllegalArgumentException("議댁옱?섏? ?딅뒗 遺?ㅼ엯?덈떎."));

        validateLocation(request.getLocationX(), request.getLocationY());
        booth.updateLocation(request.getLocationX(), request.getLocationY());
    }

    @Transactional
    public void clearBoothLocation(Long boothId) {
        Booth booth = boothRepository.findById(boothId)
                .orElseThrow(() -> new IllegalArgumentException("議댁옱?섏? ?딅뒗 遺?ㅼ엯?덈떎."));

        booth.clearLocation();
    }

    private void validateLocation(Double locationX, Double locationY) {
        if (locationX == null || locationY == null) {
            throw new IllegalArgumentException("醫뚰몴 媛믪? 鍮꾩뼱 ?덉쓣 ???놁뒿?덈떎.");
        }

        if (locationX < -180 || locationX > 180) {
            throw new IllegalArgumentException("寃쎈룄(locationX) 踰붿쐞媛 ?щ컮瑜댁? ?딆뒿?덈떎.");
        }

        if (locationY < -90 || locationY > 90) {
            throw new IllegalArgumentException("?꾨룄(locationY) 踰붿쐞媛 ?щ컮瑜댁? ?딆뒿?덈떎.");
        }
    }

    private LocalDate resolveOperationDate(LocalDate operationDate) {
        if (operationDate != null) {
            return operationDate;
        }

        return boothOperationRepository.findAll().stream()
                .map(BoothOperation::getOperationDate)
                .sorted()
                .findFirst()
                .orElse(null);
    }
}
