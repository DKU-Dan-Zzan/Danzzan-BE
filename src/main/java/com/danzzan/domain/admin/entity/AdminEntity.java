package com.danzzan.domain.admin.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor
public class AdminEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String studentNumber;

    @Column(nullable = false)
    private String password;

    private AdminEntity(String studentNumber, String password) {
        this.studentNumber = studentNumber;
        this.password = password;
    }

    public static AdminEntity create(String studentNumber, String encodedPassword) {
        return new AdminEntity(studentNumber, encodedPassword);
    }
}
