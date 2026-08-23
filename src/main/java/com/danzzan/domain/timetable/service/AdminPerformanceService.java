package com.danzzan.domain.timetable.service;

import com.danzzan.domain.timetable.dto.admin.request.CreatePerformanceRequest;
import com.danzzan.domain.timetable.dto.admin.request.UpdatePerformanceRequest;
import com.danzzan.domain.timetable.dto.admin.response.AdminPerformanceListResponse;
import com.danzzan.domain.timetable.dto.admin.response.AdminPerformanceResponse;
import com.danzzan.domain.timetable.model.entity.Artist;
import com.danzzan.domain.timetable.model.entity.Performance;
import com.danzzan.domain.timetable.repository.ArtistRepository;
import com.danzzan.domain.timetable.repository.PerformanceRepository;
import com.danzzan.infra.translation.TranslationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminPerformanceService {

    private final PerformanceRepository performanceRepository;
    private final ArtistRepository artistRepository;
    private final TranslationService translationService;

    @Transactional(readOnly = true)
    public AdminPerformanceListResponse getPerformancesByDate(LocalDate date) {
        List<Performance> performances = performanceRepository.findByDateWithArtist(date);
        List<AdminPerformanceResponse> dtos = performances.stream()
                .map(AdminPerformanceResponse::from)
                .toList();
        return new AdminPerformanceListResponse(date, dtos);
    }

    @Transactional
    public AdminPerformanceResponse createPerformance(CreatePerformanceRequest request) {
        validateTimeRange(request.getStartTime(), request.getEndTime());
        Artist artist = findArtist(request.getArtistId());

        Performance performance = Performance.create(
                request.getPerformanceDate(),
                request.getStartTime(),
                request.getEndTime(),
                artist,
                trimToNull(request.getStage())
        );

        String translatedStage = translationService.translate(performance.getStage());
        performance.applyTranslation(translatedStage);

        Performance saved = performanceRepository.save(performance);
        return AdminPerformanceResponse.from(saved);
    }

    @Transactional
    public AdminPerformanceResponse updatePerformance(Integer performanceId, UpdatePerformanceRequest request) {
        Performance performance = performanceRepository.findByIdWithArtist(performanceId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "공연을 찾을 수 없습니다."
                ));

        LocalTime newStart = request.getStartTime() != null ? request.getStartTime() : performance.getStartTime();
        LocalTime newEnd = request.getEndTime() != null ? request.getEndTime() : performance.getEndTime();
        validateTimeRange(newStart, newEnd);

        Artist artist = request.getArtistId() != null
                ? findArtist(request.getArtistId())
                : null;

        String stage = request.getStage() != null
                ? trimToNull(request.getStage())
                : performance.getStage();

        String previousStage = performance.getStage();

        performance.update(
                request.getPerformanceDate(),
                request.getStartTime(),
                request.getEndTime(),
                artist,
                stage
        );

        if (!java.util.Objects.equals(previousStage, performance.getStage())) {
            String translatedStage = translationService.translate(performance.getStage());
            performance.applyTranslation(translatedStage);
        }

        return AdminPerformanceResponse.from(performance);
    }

    @Transactional
    public void deletePerformance(Integer performanceId) {
        if (!performanceRepository.existsById(performanceId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "공연을 찾을 수 없습니다.");
        }
        performanceRepository.deleteById(performanceId);
    }

    private Artist findArtist(Integer artistId) {
        return artistRepository.findById(artistId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "아티스트를 찾을 수 없습니다."
                ));
    }

    private void validateTimeRange(LocalTime start, LocalTime end) {
        // 자정을 넘기는 공연(예: 23:00 → 01:00)을 허용하기 위해 검증 제거
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
