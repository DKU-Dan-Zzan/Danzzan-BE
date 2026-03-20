package com.danzzan.domain.admin.map.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "festival_map_setting")
public class FestivalMapSetting {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "active_operation_date", nullable = false)
    private LocalDate activeOperationDate;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public void updateActiveOperationDate(LocalDate activeOperationDate) {
        this.activeOperationDate = activeOperationDate;
        this.updatedAt = LocalDateTime.now();
    }
}