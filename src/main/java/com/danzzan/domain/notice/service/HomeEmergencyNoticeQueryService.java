package com.danzzan.domain.notice.service;

import com.danzzan.domain.notice.dto.response.HomeEmergencyNoticeDto;
import com.danzzan.domain.notice.repository.EmergencyNoticeRepository;
import com.danzzan.infra.translation.LocalizedText;
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
    public HomeEmergencyNoticeDto getActiveEmergencyNotice(boolean english) {
        return emergencyNoticeRepository.findFirstByOrderByIdAsc()
                .filter(notice -> Boolean.TRUE.equals(notice.getIsActive()))
                .filter(notice -> notice.getMessage() != null && !notice.getMessage().isBlank())
                .map(notice -> new HomeEmergencyNoticeDto(
                        notice.getId() == null ? null : notice.getId().intValue(),
                        LocalizedText.pick(english, notice.getMessage(), notice.getMessageEn()),
                        formatTime(notice.getUpdatedAt(), english)
                ))
                .orElse(null);
    }

    /**
     * 상대 시간은 서버가 문자열로 만들어 내려주므로 여기서도 언어를 갈라야 한다.
     * 본문만 영어로 바꾸고 "방금 전"이 남으면 문장 하나에 두 언어가 섞인다.
     */
    private String formatTime(LocalDateTime time, boolean english) {
        if (time == null) {
            return null;
        }

        Duration duration = Duration.between(time, LocalDateTime.now());
        long minutes = duration.toMinutes();

        if (minutes < 1) {
            return english ? "just now" : "방금 전";
        }
        if (minutes < 60) {
            return english ? plural(minutes, "minute") + " ago" : minutes + "분 전";
        }

        long hours = duration.toHours();
        if (hours < 24) {
            return english ? plural(hours, "hour") + " ago" : hours + "시간 전";
        }

        long days = duration.toDays();
        return english ? plural(days, "day") + " ago" : days + "일 전";
    }

    private String plural(long value, String unit) {
        return value + " " + unit + (value == 1 ? "" : "s");
    }
}
