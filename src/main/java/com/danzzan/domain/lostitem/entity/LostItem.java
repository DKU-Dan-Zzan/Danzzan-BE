package com.danzzan.domain.lostitem.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "lost_item")
@Getter
@Setter
@NoArgsConstructor
public class LostItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String itemName;

    private String imageUrl;

    @Column(nullable = false)
    private String foundLocation;

    @Column(nullable = false)
    private LocalDate foundDate;

    @Column(nullable = false)
    private Boolean isClaimed = false;

    private String receiverName;

    @Column(columnDefinition = "TEXT")
    private String receiverNote;

    @Column(nullable = false)
    private Boolean isActive = true;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}

