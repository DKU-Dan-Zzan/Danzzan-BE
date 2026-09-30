package com.danzzan.domain.admin.staff.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_role_change_history", indexes = @Index(name = "idx_user_role_change_history_target_changed", columnList = "target_user_id,changed_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StaffRoleHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_user_id", nullable = false)
    private Long actorUserId;

    @Column(name = "target_user_id", nullable = false)
    private Long targetUserId;

    @Column(name = "previous_role", nullable = false, length = 32)
    private String previousRole;

    @Column(name = "new_role", nullable = false, length = 32)
    private String newRole;

    @Column(name = "previous_permissions", length = 64)
    private String previousPermissions;

    @Column(name = "new_permissions", length = 64)
    private String newPermissions;

    @Column(name = "changed_at", nullable = false, columnDefinition = "datetime(6)")
    private LocalDateTime changedAt;

    public StaffRoleHistory(Long actorUserId, Long targetUserId, String previousRole, String newRole) {
        this(actorUserId, targetUserId, previousRole, newRole, null, null);
    }

    public StaffRoleHistory(Long actorUserId, Long targetUserId, String previousRole, String newRole,
                            String previousPermissions, String newPermissions) {
        this.actorUserId = actorUserId;
        this.targetUserId = targetUserId;
        this.previousRole = previousRole;
        this.newRole = newRole;
        this.previousPermissions = previousPermissions;
        this.newPermissions = newPermissions;
        this.changedAt = LocalDateTime.now();
    }
}
