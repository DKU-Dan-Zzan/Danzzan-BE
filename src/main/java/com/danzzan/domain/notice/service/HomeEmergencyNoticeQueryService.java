package com.danzzan.domain.notice.service;

import com.danzzan.domain.notice.dto.response.HomeEmergencyNoticeDto;
import com.danzzan.domain.notice.repository.EmergencyNoticeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class HomeEmergencyNoticeQueryService {
    private final EmergencyNoticeRepository emergencyNoticeRepository;

    @Transactional(readOnly = true)
    public HomeEmergencyNoticeDto getActiveEmergencyNotice() {
        return emergencyNoticeRepository.findFirstByOrderByIdAsc()
                .filter(notice -> Boolean.TRUE.equals(notice.getIsActive()))
                .filter(notice -> notice.getMessage() != null && !notice.getMessage().isBlank())
                .map(notice -> new HomeEmergencyNoticeDto(
                        notice.getId() == null ? null : notice.getId().intValue(),
                        notice.getMessage(),
                        formatTime(notice.getCreatedAt()),
                        formatTime(notice.getUpdatedAt()),
                        notice.getIsActive()
                ))
                .orElse(null);
    }

    private String formatTime(LocalDateTime time) {
        if (time == null) {
            return null;
        }

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
