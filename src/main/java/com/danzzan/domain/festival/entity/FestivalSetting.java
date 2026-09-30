package com.danzzan.domain.festival.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 축제 운영 정보. 학교(=서비스) 하나당 한 줄만 유지하므로 id 는 1 로 고정한다.
 *
 * 여기 담긴 운영 날짜(startDate~endDate)가 부스맵·타임테이블의 날짜 탭이 된다.
 * 예전에는 프론트 코드에 날짜가 박혀 있어 축제 때마다 배포가 필요했다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "festival_setting")
public class FestivalSetting {

    public static final Long SINGLETON_ID = 1L;

    @Id
    private Long id;

    @Column(name = "school_name", nullable = false, length = 100)
    private String schoolName;

    @Column(name = "festival_name", nullable = false, length = 255)
    private String festivalName;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "ticketing_enabled", nullable = false)
    private boolean ticketingEnabled;

    @Column(name = "ticketing_background_image_url", length = 2048)
    private String ticketingBackgroundImageUrl;

    public void updateTicketingBackgroundImageUrl(String url) {
        this.ticketingBackgroundImageUrl = url;
        this.updatedAt = LocalDateTime.now();
    }

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static FestivalSetting create(
            String schoolName,
            String festivalName,
            LocalDate startDate,
            LocalDate endDate,
            boolean ticketingEnabled
    ) {
        FestivalSetting setting = new FestivalSetting();
        setting.id = SINGLETON_ID;
        setting.update(schoolName, festivalName, startDate, endDate, ticketingEnabled);
        return setting;
    }

    public void update(
            String schoolName,
            String festivalName,
            LocalDate startDate,
            LocalDate endDate,
            boolean ticketingEnabled
    ) {
        this.schoolName = schoolName;
        this.festivalName = festivalName;
        this.startDate = startDate;
        this.endDate = endDate;
        this.ticketingEnabled = ticketingEnabled;
        this.updatedAt = LocalDateTime.now();
    }
}
