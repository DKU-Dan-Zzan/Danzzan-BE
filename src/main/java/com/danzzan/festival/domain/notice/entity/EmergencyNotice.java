package com.danzzan.festival.domain.notice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 긴급 공지 (한 줄 메시지). 테이블에 항상 1개 레코드만 유지, 수정만 가능.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "emergency_notice")
public class EmergencyNotice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "VARCHAR(500)")
    private String message;

    @Column(nullable = false)
    private Boolean isActive = false;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    void createdAt() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void updatedAt() {
        this.updatedAt = LocalDateTime.now();
    }
}
