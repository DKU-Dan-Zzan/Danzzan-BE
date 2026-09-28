package com.danzzan.domain.user.model.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false, unique = true)
    private String studentId;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String college;

    @Column(nullable = false)
    private String major;

    @Enumerated(EnumType.STRING)
    @Column(name = "academic_status", nullable = false)
    private AcademicStatus academicStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @Column(name = "manager_operations", nullable = false)
    private boolean managerOperations;

    @Column(name = "manager_ticketing", nullable = false)
    private boolean managerTicketing;

    @Column(name = "phone_number", unique = true, length = 32)
    private String phoneNumber;

    @Column(name = "is_phone_verified", nullable = false)
    private boolean phoneVerified;

    @Column(name = "phone_verified_at")
    private LocalDateTime phoneVerifiedAt;

    @Column(name = "token_version", nullable = false, columnDefinition = "int default 0")
    private int tokenVersion;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Builder
    public User(
            String studentId,
            String password,
            String name,
            String college,
            String major,
            AcademicStatus academicStatus,
            UserRole role,
            String phoneNumber,
            boolean phoneVerified,
            LocalDateTime phoneVerifiedAt
    ) {
        this.studentId = studentId;
        this.password = password;
        this.name = name;
        this.college = college;
        this.major = major;
        this.academicStatus = academicStatus;
        this.role = role != null ? role : UserRole.ROLE_USER;
        this.phoneNumber = phoneNumber;
        this.phoneVerified = phoneVerified;
        this.phoneVerifiedAt = phoneVerifiedAt;
        this.tokenVersion = 0;
        this.deleted = false;
        this.deletedAt = null;
        this.createdAt = LocalDateTime.now();
    }

    public void changePassword(String password) {
        this.password = password;
    }

    public void changeRole(UserRole role) {
        this.role = role;
        if (role != UserRole.ROLE_MANAGER) {
            this.managerOperations = false;
            this.managerTicketing = false;
        }
    }

    public void changeManagerPermissions(List<ManagerPermission> permissions) {
        EnumSet<ManagerPermission> values = permissions == null || permissions.isEmpty()
                ? EnumSet.noneOf(ManagerPermission.class)
                : EnumSet.copyOf(permissions);
        this.managerOperations = values.contains(ManagerPermission.OPERATIONS);
        this.managerTicketing = values.contains(ManagerPermission.TICKETING);
    }

    public List<ManagerPermission> getManagerPermissions() {
        if (role == UserRole.ROLE_ADMIN) return List.of(ManagerPermission.OPERATIONS, ManagerPermission.TICKETING);
        if (role != UserRole.ROLE_MANAGER) return List.of();
        return java.util.stream.Stream.of(ManagerPermission.values()).filter(this::hasManagerPermission).toList();
    }

    public boolean hasManagerPermission(ManagerPermission permission) {
        if (role == UserRole.ROLE_ADMIN) return true;
        if (role != UserRole.ROLE_MANAGER) return false;
        return permission == ManagerPermission.OPERATIONS ? managerOperations : managerTicketing;
    }

    public void bumpTokenVersion() {
        this.tokenVersion += 1;
    }

    public void markPhoneVerified(String phoneNumber, LocalDateTime verifiedAt) {
        this.phoneNumber = phoneNumber;
        this.phoneVerified = true;
        this.phoneVerifiedAt = verifiedAt;
    }

    public void withdraw(String maskedStudentId, String encodedRandomPassword) {
        this.studentId = maskedStudentId;
        this.password = encodedRandomPassword;
        this.college = "WITHDRAWN";
        this.major = "WITHDRAWN";
        this.phoneNumber = null;
        this.phoneVerified = false;
        this.phoneVerifiedAt = null;
        this.deleted = true;
        this.deletedAt = LocalDateTime.now();
        bumpTokenVersion();
    }
}
