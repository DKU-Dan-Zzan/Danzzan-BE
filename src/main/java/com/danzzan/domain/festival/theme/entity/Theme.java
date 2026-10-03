package com.danzzan.domain.festival.theme.entity;

import com.danzzan.domain.festival.entity.FestivalSetting;
import com.danzzan.domain.festival.theme.dto.ThemeData;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "theme", indexes = @Index(name = "idx_theme_festival_setting", columnList = "festival_setting_id"))
public class Theme {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "festival_setting_id", nullable = false)
    private FestivalSetting festivalSetting;

    @Column(nullable = false, length = 100)
    private String name;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "theme_data", nullable = false, columnDefinition = "json")
    private ThemeData themeData;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static Theme create(FestivalSetting festivalSetting, String name, ThemeData themeData) {
        Theme theme = new Theme();
        theme.festivalSetting = festivalSetting;
        theme.update(name, themeData);
        return theme;
    }

    public void update(String name, ThemeData themeData) {
        this.name = name.trim();
        this.themeData = themeData;
        this.updatedAt = LocalDateTime.now();
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
