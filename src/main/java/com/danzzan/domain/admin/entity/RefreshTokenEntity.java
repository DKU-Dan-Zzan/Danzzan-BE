package com.danzzan.domain.admin.entity;

import java.time.LocalDateTime;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor

public class RefreshTokenEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long adminId;

    @Column(nullable = false)
    private String token;

    private LocalDateTime expiryDate;

    public RefreshTokenEntity(Long adminId, String token, LocalDateTime expiryDate) {
        this.adminId = adminId;
        this.token = token;
        this.expiryDate = expiryDate;
    }
}
