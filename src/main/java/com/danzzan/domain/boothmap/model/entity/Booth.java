package com.danzzan.domain.boothmap.model.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "booth")
public class Booth {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private BoothType type;

    @Column(name = "description")
    private String description;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "location_x", nullable = false)
    private Double locationX;

    @Column(name = "location_y", nullable = false)
    private Double locationY;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    public Booth(String name, BoothType type, String description, String imageUrl, Double locationX, Double locationY) {
        this.name = name;
        this.type = type;
        this.description = description;
        this.imageUrl = imageUrl;
        this.locationX = locationX;
        this.locationY = locationY;
    }

    public void updateLocation(Double locationX, Double locationY) {
        this.locationX = locationX;
        this.locationY = locationY;
    }

    public void clearLocation() {
        this.locationX = null;
        this.locationY = null;
    }

    public void updateDescription(String description) {
        this.description = description;
    }

    public void updateAdminInfo(String name, String description) {
        this.name = name;
        this.description = description;
    }
}
