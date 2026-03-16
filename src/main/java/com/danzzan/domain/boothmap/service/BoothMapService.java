package com.danzzan.domain.boothmap.service;

import com.danzzan.domain.boothmap.model.dto.BoothMapItemResponse;
import com.danzzan.domain.boothmap.model.dto.BoothMapResponse;
import com.danzzan.domain.boothmap.model.dto.CollegeMapItemResponse;
import com.danzzan.domain.boothmap.model.entity.Booth;
import com.danzzan.domain.boothmap.model.entity.BoothOperation;
import com.danzzan.domain.boothmap.model.entity.College;
import com.danzzan.domain.boothmap.repository.BoothOperationRepository;
import com.danzzan.domain.boothmap.repository.CollegeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BoothMapService {
    private final CollegeRepository collegeRepository;
    private final BoothOperationRepository boothOperationRepository;

    public BoothMapResponse getBoothMap(LocalDate operationDate) {
        List<College> colleges = collegeRepository.findAll();
        List<BoothOperation> boothOperations = boothOperationRepository.findAllWithBoothByOperationDate(operationDate);

        List<CollegeMapItemResponse> collegeDtos = colleges.stream()
                .map(college -> new CollegeMapItemResponse(
                        college.getId(),
                        college.getName(),
                        college.getLocationX(),
                        college.getLocationY()
                ))
                .toList();

        List<BoothMapItemResponse> boothDtos = boothOperations.stream()
                .map(boothOperation -> {
                    Booth booth = boothOperation.getBooth();
                    return new BoothMapItemResponse(
                            booth.getId(),
                            booth.getName(),
                            booth.getType(),
                            booth.getLocationX(),
                            booth.getLocationY(),
                            formatTime(boothOperation.getStartTime()),
                            formatTime(boothOperation.getEndTime())
                    );
                })
                .toList();

        return new BoothMapResponse(collegeDtos, boothDtos);
    }

    private String formatTime(LocalTime time) {
        return time == null ? null : time.toString();
    }
}