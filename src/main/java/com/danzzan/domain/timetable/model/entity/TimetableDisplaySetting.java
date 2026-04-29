package com.danzzan.domain.timetable.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "timetable_display_setting")
public class TimetableDisplaySetting {

    private static final Long SINGLETON_ID = 1L;

    @Id
    private Long id;

    @Column(name = "coming_soon_overlay_enabled", nullable = false)
    private boolean comingSoonOverlayEnabled;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static TimetableDisplaySetting createDefault(boolean comingSoonOverlayEnabled) {
        TimetableDisplaySetting setting = new TimetableDisplaySetting();
        setting.id = SINGLETON_ID;
        setting.comingSoonOverlayEnabled = comingSoonOverlayEnabled;
        setting.updatedAt = LocalDateTime.now();
        return setting;
    }

    public void updateComingSoonOverlayEnabled(boolean comingSoonOverlayEnabled) {
        this.comingSoonOverlayEnabled = comingSoonOverlayEnabled;
        this.updatedAt = LocalDateTime.now();
    }
}
