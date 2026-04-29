package com.danzzan.domain.boothmap.service;

import com.danzzan.domain.boothmap.model.dto.BoothMapItemResponse;
import com.danzzan.domain.boothmap.model.dto.BoothMapResponse;
import com.danzzan.domain.boothmap.model.dto.CollegeMapItemResponse;
import com.danzzan.domain.boothmap.model.entity.Booth;
import com.danzzan.domain.boothmap.model.entity.BoothOperation;
import com.danzzan.domain.boothmap.model.entity.BoothOperationStatus;
import com.danzzan.domain.boothmap.model.entity.BoothSubType;
import com.danzzan.domain.boothmap.model.entity.College;
import com.danzzan.domain.boothmap.repository.BoothRepository;
import com.danzzan.domain.boothmap.repository.BoothOperationRepository;
import com.danzzan.domain.boothmap.repository.CollegeRepository;
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
public class BoothMapService {
    private final CollegeRepository collegeRepository;
    private final BoothRepository boothRepository;
    private final BoothOperationRepository boothOperationRepository;

    public BoothMapResponse getBoothMap(LocalDate operationDate) {
        List<College> colleges = collegeRepository.findAll();

        List<BoothOperation> boothOperations = (operationDate == null)
                ? boothOperationRepository.findAllWithBooth()
                : boothOperationRepository.findAllWithBoothByOperationDate(operationDate);
        Map<Long, BoothOperation> boothOperationMap = boothOperations.stream()
                .collect(Collectors.toMap(
                        boothOperation -> boothOperation.getBooth().getId(),
                        Function.identity(),
                        (existing, ignored) -> existing
                ));
        List<Booth> booths = (operationDate == null)
                ? boothRepository.findAll()
                : boothRepository.findAllByOperationDate(operationDate);

        List<CollegeMapItemResponse> collegeDtos = colleges.stream()
                .map(college -> new CollegeMapItemResponse(
                        college.getId(),
                        college.getName(),
                        college.getLocationX(),
                        college.getLocationY()
                ))
                .toList();

        List<BoothMapItemResponse> boothDtos = booths.stream()
                .map(booth -> {
                    BoothOperation boothOperation = boothOperationMap.get(booth.getId());
                    return new BoothMapItemResponse(
                            booth.getId(),
                            booth.getName(),
                            booth.getType(),
                            BoothSubType.resolve(booth),
                            booth.getLocationX(),
                            booth.getLocationY(),
                            boothOperation != null ? boothOperation.getOperationStatus() : BoothOperationStatus.UNKNOWN,
                            boothOperation != null ? formatTime(boothOperation.getStartTime()) : null,
                            boothOperation != null ? formatTime(boothOperation.getEndTime()) : null
                    );
                })
                .toList();

        return new BoothMapResponse(collegeDtos, boothDtos);
    }

    private String formatTime(LocalTime time) {
        return time == null ? null : time.toString();
    }
}
