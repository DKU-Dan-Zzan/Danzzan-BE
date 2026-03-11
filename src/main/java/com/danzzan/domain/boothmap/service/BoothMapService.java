package com.danzzan.domain.boothmap.service;

import com.danzzan.domain.boothmap.model.dto.BoothMapResponse;
import com.danzzan.domain.boothmap.model.dto.BoothMapItemResponse;
import com.danzzan.domain.boothmap.model.dto.CollegeMapItemResponse;
import com.danzzan.domain.boothmap.model.entity.Booth;
import com.danzzan.domain.boothmap.model.entity.College;
import com.danzzan.domain.boothmap.repository.BoothRepository;
import com.danzzan.domain.boothmap.repository.CollegeRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BoothMapService {
    private final CollegeRepository collegeRepository;
    private final BoothRepository boothRepository;

    public BoothMapResponse getBoothMap() {
        List<College> colleges = collegeRepository.findAll();
        List<Booth> booths = boothRepository.findAll();

        List<CollegeMapItemResponse> collegeDtos = colleges.stream()
                .map(college -> new CollegeMapItemResponse(
                        college.getId(),
                        college.getName(),
                        college.getLocationX(),
                        college.getLocationY()
                ))
                .toList();

        List<BoothMapItemResponse> boothDtos = booths.stream()
                .map(booth -> new BoothMapItemResponse(
                        booth.getId(),
                        booth.getName(),
                        booth.getType(),
                        booth.getLocationX(),
                        booth.getLocationY()
                ))
                .toList();

        return new BoothMapResponse(collegeDtos, boothDtos);
    }
}