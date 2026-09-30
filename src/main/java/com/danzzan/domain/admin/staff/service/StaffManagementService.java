package com.danzzan.domain.admin.staff.service;

import com.danzzan.domain.admin.staff.dto.StaffMemberResponse;
import com.danzzan.domain.admin.staff.dto.StaffPageResponse;
import com.danzzan.domain.admin.staff.dto.StaffRoleChangeRequest;
import com.danzzan.domain.admin.staff.dto.StaffFilter;
import com.danzzan.domain.admin.staff.entity.StaffRoleHistory;
import com.danzzan.domain.admin.staff.exception.StaffManagementException;
import com.danzzan.domain.admin.staff.repository.StaffRoleHistoryRepository;
import com.danzzan.domain.admin.staff.repository.StaffUserQueryRepository;
import com.danzzan.domain.user.model.entity.User;
import com.danzzan.domain.user.model.entity.UserRole;
import com.danzzan.domain.user.model.entity.ManagerPermission;
import com.danzzan.domain.user.repository.UserRepository;
import com.danzzan.global.exception.AdminForbiddenException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class StaffManagementService {

    private final UserRepository userRepository;
    private final StaffUserQueryRepository staffUserQueryRepository;
    private final StaffRoleHistoryRepository historyRepository;
    private final StaffRoleChangeCachePublisher cachePublisher;
    @PersistenceContext
    private EntityManager entityManager;
    @Value("${app.staff-management.enabled:false}")
    private boolean managementEnabled;

    @Autowired
    public StaffManagementService(UserRepository userRepository, StaffUserQueryRepository staffUserQueryRepository,
                                  StaffRoleHistoryRepository historyRepository, StaffRoleChangeCachePublisher cachePublisher) {
        this.userRepository = userRepository;
        this.staffUserQueryRepository = staffUserQueryRepository;
        this.historyRepository = historyRepository;
        this.cachePublisher = cachePublisher;
    }

    StaffManagementService(UserRepository userRepository, StaffUserQueryRepository staffUserQueryRepository,
                           StaffRoleHistoryRepository historyRepository, StaffRoleChangeCachePublisher cachePublisher,
                           boolean managementEnabled) {
        this(userRepository, staffUserQueryRepository, historyRepository, cachePublisher);
        this.managementEnabled = managementEnabled;
    }

    @Transactional(readOnly = true)
    public StaffMemberResponse findCandidate(Long actorUserId, String studentId) {
        requireActiveAdmin(actorUserId);
        String normalized = normalizeStudentId(studentId);
        User user = userRepository.findByStudentIdAndDeletedFalse(normalized)
                .orElseThrow(StaffManagementException::targetNotFound);
        return StaffMemberResponse.from(user);
    }

    @Transactional(readOnly = true)
    public StaffPageResponse findStaff(Long actorUserId, Integer page, Integer size) {
        return findStaff(actorUserId, page, size, StaffFilter.ALL);
    }

    @Transactional(readOnly = true)
    public StaffPageResponse findStaff(Long actorUserId, Integer page, Integer size, StaffFilter filter) {
        requireActiveAdmin(actorUserId);
        validatePage(page, size);
        if (filter == null) throw StaffManagementException.invalidFilter();
        var result = staffUserQueryRepository.findActiveStaff(PageRequest.of(page, size, Sort.by("name", "studentId").ascending()), filter)
                .map(StaffMemberResponse::from);
        return StaffPageResponse.from(result, managementEnabled);
    }

    @Transactional
    public StaffMemberResponse changeRole(Long actorUserId, Long targetUserId, StaffRoleChangeRequest request) {
        if (!managementEnabled) {
            throw StaffManagementException.disabled();
        }
        if (targetUserId == null || targetUserId <= 0) {
            throw StaffManagementException.invalidTargetId();
        }
        if (request == null || request.role() == null) {
            throw StaffManagementException.invalidRole();
        }
        UserRole requestedRole = parseChangeableRole(request.role());
        List<ManagerPermission> requestedPermissions = parsePermissions(requestedRole, request.permissions());
        User actor = lockAndRequireActiveAdmin(actorUserId);
        if (actor.getId().equals(targetUserId)) {
            throw StaffManagementException.conflict();
        }
        User target = userRepository.findActiveByIdForUpdate(targetUserId)
                .orElseThrow(StaffManagementException::targetNotFound);
        if (target.getRole() == UserRole.ROLE_ADMIN) {
            throw StaffManagementException.conflict();
        }
        UserRole previousRole = target.getRole();
        List<ManagerPermission> previousPermissions = target.getManagerPermissions();
        if (previousRole == requestedRole && previousPermissions.equals(requestedPermissions)) {
            throw StaffManagementException.conflict();
        }
        target.changeRole(requestedRole);
        target.changeManagerPermissions(requestedPermissions);
        target.bumpTokenVersion();
        historyRepository.save(new StaffRoleHistory(actor.getId(), target.getId(), roleName(previousRole), roleName(requestedRole),
                permissionNames(previousPermissions), permissionNames(requestedPermissions)));
        cachePublisher.publishAfterCommit(target.getId(), target.getTokenVersion());
        return StaffMemberResponse.from(target);
    }

    @Transactional
    public StaffMemberResponse promoteAdmin(Long actorUserId, Long targetUserId) {
        requireManagementEnabled();
        validateTargetId(targetUserId);
        User actor = lockAndRequireActiveAdmin(actorUserId);
        if (actor.getId().equals(targetUserId)) throw StaffManagementException.conflict();
        User target = userRepository.findActiveByIdForUpdate(targetUserId)
                .orElseThrow(StaffManagementException::targetNotFound);
        refreshLocked(target);
        if (target.getRole() != UserRole.ROLE_MANAGER) throw StaffManagementException.conflict();
        changeRoleAndAudit(actor, target, UserRole.ROLE_ADMIN, List.of());
        return StaffMemberResponse.from(target);
    }

    @Transactional
    public StaffMemberResponse demoteAdmin(Long actorUserId, Long targetUserId) {
        requireManagementEnabled();
        validateTargetId(targetUserId);
        List<User> activeAdmins = userRepository.findActiveAdminsForUpdate();
        activeAdmins.forEach(this::refreshLocked);
        User actor = activeAdmins.stream().filter(user -> user.getId().equals(actorUserId)).findFirst()
                .orElseThrow(() -> new AdminForbiddenException("관리자 권한이 필요합니다."));
        if (actor.getId().equals(targetUserId)) throw StaffManagementException.conflict();
        User target = activeAdmins.stream().filter(user -> user.getId().equals(targetUserId)).findFirst()
                .orElseGet(() -> userRepository.findActiveByIdForUpdate(targetUserId)
                        .orElseThrow(StaffManagementException::targetNotFound));
        refreshLocked(target);
        if (target.getRole() != UserRole.ROLE_ADMIN) throw StaffManagementException.conflict();
        if (activeAdmins.size() <= 1) throw StaffManagementException.conflict();
        changeRoleAndAudit(actor, target, UserRole.ROLE_MANAGER,
                List.of(ManagerPermission.OPERATIONS, ManagerPermission.TICKETING));
        return StaffMemberResponse.from(target);
    }

    private void changeRoleAndAudit(User actor, User target, UserRole requestedRole,
                                    List<ManagerPermission> requestedPermissions) {
        UserRole previousRole = target.getRole();
        List<ManagerPermission> previousPermissions = target.getManagerPermissions();
        target.changeRole(requestedRole);
        target.changeManagerPermissions(requestedPermissions);
        target.bumpTokenVersion();
        historyRepository.save(new StaffRoleHistory(actor.getId(), target.getId(), roleName(previousRole), roleName(requestedRole),
                permissionNames(previousPermissions), permissionNames(target.getManagerPermissions())));
        cachePublisher.publishAfterCommit(target.getId(), target.getTokenVersion());
    }

    private User requireActiveAdmin(Long actorUserId) {
        if (actorUserId == null || actorUserId <= 0) {
            throw new AdminForbiddenException("관리자 권한이 필요합니다.");
        }
        User actor = userRepository.findActiveById(actorUserId)
                .orElseThrow(() -> new AdminForbiddenException("관리자 권한이 필요합니다."));
        if (actor.getRole() != UserRole.ROLE_ADMIN) {
            throw new AdminForbiddenException("관리자 권한이 필요합니다.");
        }
        return actor;
    }

    private User lockAndRequireActiveAdmin(Long actorUserId) {
        if (actorUserId == null || actorUserId <= 0) throw new AdminForbiddenException("관리자 권한이 필요합니다.");
        List<User> activeAdmins = userRepository.findActiveAdminsForUpdate();
        activeAdmins.forEach(this::refreshLocked);
        return activeAdmins.stream()
                .filter(user -> user.getId().equals(actorUserId))
                .findFirst()
                .orElseThrow(() -> new AdminForbiddenException("관리자 권한이 필요합니다."));
    }

    private void requireManagementEnabled() {
        if (!managementEnabled) throw StaffManagementException.disabled();
    }

    private void validateTargetId(Long targetUserId) {
        if (targetUserId == null || targetUserId <= 0) throw StaffManagementException.invalidTargetId();
    }

    private void refreshLocked(User user) {
        if (entityManager != null) entityManager.refresh(user, LockModeType.PESSIMISTIC_WRITE);
    }

    private String normalizeStudentId(String studentId) {
        if (studentId == null) throw StaffManagementException.invalidStudentId();
        String normalized = studentId.trim();
        if (normalized.isEmpty() || normalized.length() > 255) throw StaffManagementException.invalidStudentId();
        return normalized;
    }

    private void validatePage(Integer page, Integer size) {
        if (page == null || page < 0 || size == null || size < 1 || size > 100) throw StaffManagementException.invalidPage();
    }

    private UserRole parseChangeableRole(String value) {
        return switch (value) {
            case "MANAGER" -> UserRole.ROLE_MANAGER;
            case "USER" -> UserRole.ROLE_USER;
            default -> throw StaffManagementException.invalidRole();
        };
    }

    private List<ManagerPermission> parsePermissions(UserRole role, List<String> values) {
        if (role == UserRole.ROLE_USER) {
            if (values == null || values.isEmpty()) return List.of();
            throw StaffManagementException.invalidRole();
        }
        if (values == null || values.isEmpty()) throw StaffManagementException.invalidRole();
        EnumSet<ManagerPermission> result = EnumSet.noneOf(ManagerPermission.class);
        for (String value : values) {
            try {
                if (!result.add(ManagerPermission.valueOf(value))) throw StaffManagementException.invalidRole();
            } catch (IllegalArgumentException | NullPointerException e) {
                throw StaffManagementException.invalidRole();
            }
        }
        return List.copyOf(result);
    }

    private String permissionNames(List<ManagerPermission> permissions) {
        return permissions.stream().map(ManagerPermission::name).collect(Collectors.joining(","));
    }

    private String roleName(UserRole role) {
        return switch (role) {
            case ROLE_USER -> "USER";
            case ROLE_MANAGER -> "MANAGER";
            case ROLE_ADMIN -> "ADMIN";
        };
    }
}
