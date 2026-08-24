package com.danzzan.domain.timetable.service;

import com.danzzan.domain.timetable.model.entity.Performance;
import com.danzzan.domain.timetable.model.dto.TimetablePerformanceDto;
import com.danzzan.domain.timetable.model.dto.TimetableResponseDto;
import com.danzzan.domain.timetable.repository.PerformanceRepository;

import com.danzzan.domain.timetable.model.dto.ContentImageDto;
import com.danzzan.domain.timetable.repository.ContentImageRepository;

import com.danzzan.infra.translation.LocalizedText;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TimetableService {
    private final PerformanceRepository performanceRepository;
    private final ContentImageRepository contentImageRepository;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    public TimetableResponseDto getPerformances(LocalDate date, boolean english) {
        List<Performance> performances = performanceRepository.findByDateWithArtist(date);

        List<TimetablePerformanceDto> performanceDtos = performances.stream()
            .map(performance -> new TimetablePerformanceDto(
                performance.getId(),
                performance.getStartTime().format(TIME_FORMATTER),
                performance.getEndTime().format(TIME_FORMATTER),
                LocalizedText.pick(english, performance.getStage(), performance.getStageEn()),
                performance.getArtist().getId(),
                LocalizedText.pick(english, performance.getArtist().getName(), performance.getArtist().getNameEn()),
                performance.getArtist().getImageUrl(),
                LocalizedText.pick(english, performance.getArtist().getDescription(), performance.getArtist().getDescriptionEn())
            ))
            .toList();

        return new TimetableResponseDto(date, performanceDtos);
    }

    public List<ContentImageDto> getContentImages() {
        return contentImageRepository.findAllByOrderByDisplayOrderAsc()
                .stream()
                .map(image -> new ContentImageDto(
                    image.getId(),
                    image.getName(),
                    image.getPreviewImageUrl(),
                    image.getDetailImageUrl()
                ))
                .toList();
    }
}
