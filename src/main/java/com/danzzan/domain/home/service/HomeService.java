package com.danzzan.domain.home.service;

import com.danzzan.domain.home.model.dto.HomeImageDto;
import com.danzzan.domain.home.model.entity.HomeImage;
import com.danzzan.domain.home.repository.HomeImageRepository;

import com.danzzan.domain.home.model.dto.EmergencyNoticeDto;
import com.danzzan.domain.home.model.entity.EmergencyNotice;
import com.danzzan.domain.home.repository.EmergencyNoticeRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.time.LocalDateTime;
import java.time.Duration;

@Service
@RequiredArgsConstructor
public class HomeService {
    private final HomeImageRepository homeImageRepository;
    private final EmergencyNoticeRepository emergencyNoticeRepository;

    public List<HomeImageDto> getHomeImages() {
        return homeImageRepository.findAllByOrderByDisplayOrderAsc()
                .stream()
                .map(image -> new HomeImageDto(
                    image.getId(),
                    image.getImageUrl()
                ))
                .toList();
    }

    public EmergencyNoticeDto getLatestEmergencyNotice() {
        return emergencyNoticeRepository.findTopByOrderByCreatedAtDesc()
                .map(notice -> new EmergencyNoticeDto(
                    notice.getId(),
                    notice.getContent(),
                    formatTime(notice.getCreatedAt())
                ))
                .orElse(null);
    }

    private String formatTime(LocalDateTime time) {
        Duration duration = Duration.between(time, LocalDateTime.now());

        long minutes = duration.toMinutes();

        if (minutes < 1) return "방금 전";
        if (minutes < 60) return minutes + "분 전";

        long hours = duration.toHours();
        if (hours < 24) return hours + "시간 전";

        long days = duration.toDays();
        return days + "일 전";
    }
}