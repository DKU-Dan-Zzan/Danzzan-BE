package com.danzzan.domain.admin.staff.service;

import com.danzzan.domain.admin.staff.dto.StaffRoleChangeRequest;
import com.danzzan.domain.admin.staff.dto.StaffFilter;
import com.danzzan.domain.admin.staff.entity.StaffRoleHistory;
import com.danzzan.domain.admin.staff.exception.StaffManagementException;
import com.danzzan.domain.admin.staff.repository.StaffRoleHistoryRepository;
import com.danzzan.domain.admin.staff.repository.StaffUserQueryRepository;
import com.danzzan.domain.user.model.entity.AcademicStatus;
import com.danzzan.domain.user.model.entity.User;
import com.danzzan.domain.user.model.entity.UserRole;
import com.danzzan.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StaffManagementServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private StaffUserQueryRepository staffUserQueryRepository;
    @Mock private StaffRoleHistoryRepository historyRepository;
    @Mock private StaffRoleChangeCachePublisher cachePublisher;

    private StaffManagementService service;
    private final Map<Long, User> users = new LinkedHashMap<>();

    @BeforeEach
    void setUp() {
        service = new StaffManagementService(userRepository, staffUserQueryRepository, historyRepository, cachePublisher, true);
        lenient().when(userRepository.findActiveAdminsForUpdate()).thenAnswer(invocation -> users.values().stream()
                .filter(user -> user.getRole() == UserRole.ROLE_ADMIN && !user.isDeleted()).toList());
    }

    @Test
    void 활성_ADMIN이_USER에게_MANAGER를_부여하면_토큰을_무효화하고_이력을_한번_기록한다() {
        User admin = user(1L, "32100001", UserRole.ROLE_ADMIN);
        User target = user(2L, "32100002", UserRole.ROLE_USER);
        when(userRepository.findActiveById(1L)).thenReturn(Optional.of(admin));
        when(userRepository.findActiveByIdForUpdate(2L)).thenReturn(Optional.of(target));

        var result = service.changeRole(1L, 2L, new StaffRoleChangeRequest("MANAGER", java.util.List.of("OPERATIONS")));

        assertThat(result.role()).isEqualTo("MANAGER");
        assertThat(target.getRole()).isEqualTo(UserRole.ROLE_MANAGER);
        assertThat(target.getTokenVersion()).isEqualTo(1);
        ArgumentCaptor<StaffRoleHistory> history = ArgumentCaptor.forClass(StaffRoleHistory.class);
        verify(historyRepository).save(history.capture());
        assertThat(history.getValue().getActorUserId()).isEqualTo(1L);
        assertThat(history.getValue().getTargetUserId()).isEqualTo(2L);
        assertThat(history.getValue().getPreviousRole()).isEqualTo("USER");
        assertThat(history.getValue().getNewRole()).isEqualTo("MANAGER");
        verify(cachePublisher).publishAfterCommit(2L, 1);
    }

    @Test
    void 이미_MANAGER인_대상에게_중복_부여하면_충돌이다() {
        User admin = user(1L, "32100001", UserRole.ROLE_ADMIN);
        User target = user(2L, "32100002", UserRole.ROLE_MANAGER);
        when(userRepository.findActiveById(1L)).thenReturn(Optional.of(admin));
        when(userRepository.findActiveByIdForUpdate(2L)).thenReturn(Optional.of(target));

        target.changeManagerPermissions(java.util.List.of(com.danzzan.domain.user.model.entity.ManagerPermission.OPERATIONS));
        assertThatThrownBy(() -> service.changeRole(1L, 2L, new StaffRoleChangeRequest("MANAGER", java.util.List.of("OPERATIONS"))))
                .isInstanceOf(StaffManagementException.class)
                .hasFieldOrPropertyWithValue("code", "STAFF_ROLE_CONFLICT");
        verifyNoInteractions(historyRepository, cachePublisher);
    }

    @Test
    void 기존_MANAGER의_권한_묶음은_역할_변경없이_교체하고_감사_이력을_남긴다() {
        User admin = user(1L, "32100001", UserRole.ROLE_ADMIN);
        User target = user(2L, "32100002", UserRole.ROLE_MANAGER);
        target.changeManagerPermissions(java.util.List.of(com.danzzan.domain.user.model.entity.ManagerPermission.OPERATIONS));
        when(userRepository.findActiveById(1L)).thenReturn(Optional.of(admin));
        when(userRepository.findActiveByIdForUpdate(2L)).thenReturn(Optional.of(target));

        var result = service.changeRole(1L, 2L, new StaffRoleChangeRequest("MANAGER", java.util.List.of("TICKETING")));

        assertThat(result.permissions()).containsExactly("TICKETING");
        assertThat(target.getTokenVersion()).isEqualTo(1);
        ArgumentCaptor<StaffRoleHistory> history = ArgumentCaptor.forClass(StaffRoleHistory.class);
        verify(historyRepository).save(history.capture());
        assertThat(history.getValue().getPreviousPermissions()).isEqualTo("OPERATIONS");
        assertThat(history.getValue().getNewPermissions()).isEqualTo("TICKETING");
    }

    @Test
    void MANAGER에는_비어있거나_중복되거나_알수없는_권한을_줄수없다() {
        assertThatThrownBy(() -> service.changeRole(1L, 2L, new StaffRoleChangeRequest("MANAGER", java.util.List.of())))
                .isInstanceOf(StaffManagementException.class);
        assertThatThrownBy(() -> service.changeRole(1L, 2L, new StaffRoleChangeRequest("MANAGER", java.util.List.of("OTHER"))))
                .isInstanceOf(StaffManagementException.class);
        assertThatThrownBy(() -> service.changeRole(1L, 2L, new StaffRoleChangeRequest("MANAGER", java.util.List.of("OPERATIONS", "OPERATIONS"))))
                .isInstanceOf(StaffManagementException.class);
    }

    @Test
    void 자기_계정이나_ADMIN_대상은_변경할_수_없다() {
        User admin = user(1L, "32100001", UserRole.ROLE_ADMIN);
        when(userRepository.findActiveById(1L)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.changeRole(1L, 1L, new StaffRoleChangeRequest("USER")))
                .isInstanceOf(StaffManagementException.class)
                .hasFieldOrPropertyWithValue("code", "STAFF_ROLE_CONFLICT");
        verify(userRepository, never()).findActiveByIdForUpdate(anyLong());
    }

    @Test
    void 기능이_꺼진_경우_변경을_막는다() {
        service = new StaffManagementService(userRepository, staffUserQueryRepository, historyRepository, cachePublisher, false);

        assertThatThrownBy(() -> service.changeRole(1L, 2L, new StaffRoleChangeRequest("MANAGER", java.util.List.of("OPERATIONS"))))
                .isInstanceOf(StaffManagementException.class)
                .hasFieldOrPropertyWithValue("code", "STAFF_MANAGEMENT_DISABLED");
        verifyNoInteractions(userRepository, historyRepository, cachePublisher);
    }

    @Test
    void 삭제되었거나_없는_대상은_찾지_못한다() {
        User admin = user(1L, "32100001", UserRole.ROLE_ADMIN);
        when(userRepository.findActiveById(1L)).thenReturn(Optional.of(admin));
        when(userRepository.findActiveByIdForUpdate(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changeRole(1L, 2L, new StaffRoleChangeRequest("USER")))
                .isInstanceOf(StaffManagementException.class)
                .hasFieldOrPropertyWithValue("code", "STAFF_TARGET_NOT_FOUND");
    }

    @Test
    void 이력_저장에_실패하면_캐시를_발행하지_않아_트랜잭션_롤백을_방해하지_않는다() {
        User admin = user(1L, "32100001", UserRole.ROLE_ADMIN);
        User target = user(2L, "32100002", UserRole.ROLE_USER);
        when(userRepository.findActiveById(1L)).thenReturn(Optional.of(admin));
        when(userRepository.findActiveByIdForUpdate(2L)).thenReturn(Optional.of(target));
        doThrow(new IllegalStateException("history failure")).when(historyRepository).save(any(StaffRoleHistory.class));

        assertThatThrownBy(() -> service.changeRole(1L, 2L, new StaffRoleChangeRequest("MANAGER", java.util.List.of("OPERATIONS"))))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(cachePublisher);
    }

    @Test
    void 후보_학번은_공백을_제거한_정확한_문자열로_조회한다() {
        User admin = user(1L, "32100001", UserRole.ROLE_ADMIN);
        User candidate = user(2L, "a-001", UserRole.ROLE_USER);
        when(userRepository.findActiveById(1L)).thenReturn(Optional.of(admin));
        when(userRepository.findByStudentIdAndDeletedFalse("a-001")).thenReturn(Optional.of(candidate));

        assertThat(service.findCandidate(1L, "  a-001  ").studentId()).isEqualTo("a-001");
    }

    @Test
    void 비어있거나_너무_긴_학번과_잘못된_페이지는_400_업무_오류다() {
        User admin = user(1L, "32100001", UserRole.ROLE_ADMIN);
        when(userRepository.findActiveById(1L)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.findCandidate(1L, "   "))
                .isInstanceOf(StaffManagementException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_STUDENT_ID");
        assertThatThrownBy(() -> service.findCandidate(1L, "x".repeat(256)))
                .isInstanceOf(StaffManagementException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_STUDENT_ID");
        assertThatThrownBy(() -> service.findStaff(1L, -1, 20))
                .isInstanceOf(StaffManagementException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_PAGE_REQUEST");
        assertThatThrownBy(() -> service.findStaff(1L, 0, 101))
                .isInstanceOf(StaffManagementException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_PAGE_REQUEST");
    }

    @Test
    void 활성_ADMIN만_서비스에서_후보와_목록을_조회할_수_있다() {
        when(userRepository.findActiveById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findCandidate(1L, "32100002"))
                .isInstanceOf(com.danzzan.global.exception.AdminForbiddenException.class);
        assertThatThrownBy(() -> service.findStaff(null, 0, 20))
                .isInstanceOf(com.danzzan.global.exception.AdminForbiddenException.class);
    }

    @Test
    void 목록은_ADMIN과_MANAGER를_이름과_학번_오름차순_페이지로_조회한다() {
        User admin = user(1L, "32100001", UserRole.ROLE_ADMIN);
        User manager = user(2L, "32100002", UserRole.ROLE_MANAGER);
        when(userRepository.findActiveById(1L)).thenReturn(Optional.of(admin));
        when(staffUserQueryRepository.findActiveStaff(any(Pageable.class), eq(StaffFilter.ALL)))
                .thenReturn(new PageImpl<>(java.util.List.of(admin, manager), org.springframework.data.domain.PageRequest.of(0, 20), 2));

        assertThat(service.findStaff(1L, 0, 20).items()).extracting(com.danzzan.domain.admin.staff.dto.StaffMemberResponse::role).containsExactly("ADMIN", "MANAGER");
        var page = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(staffUserQueryRepository).findActiveStaff(page.capture(), eq(StaffFilter.ALL));
        assertThat(page.getValue().getSort()).isEqualTo(org.springframework.data.domain.Sort.by("name", "studentId").ascending());
    }

    @Test
    void 목록_필터를_저장소_페이징_조회에_전달한다() {
        User admin = user(1L, "32100001", UserRole.ROLE_ADMIN);
        when(userRepository.findActiveById(1L)).thenReturn(Optional.of(admin));
        when(staffUserQueryRepository.findActiveStaff(any(Pageable.class), eq(StaffFilter.TICKETING)))
                .thenReturn(new PageImpl<>(java.util.List.of(), org.springframework.data.domain.PageRequest.of(0, 20), 0));

        service.findStaff(1L, 0, 20, StaffFilter.TICKETING);

        verify(staffUserQueryRepository).findActiveStaff(any(Pageable.class), eq(StaffFilter.TICKETING));
    }

    @Test
    void MANAGER를_최고관리자로_승격하면_유효권한을_감사하고_세션을_무효화한다() {
        User admin = user(1L, "32100001", UserRole.ROLE_ADMIN);
        User manager = user(2L, "32100002", UserRole.ROLE_MANAGER);
        manager.changeManagerPermissions(java.util.List.of(com.danzzan.domain.user.model.entity.ManagerPermission.TICKETING));
        when(userRepository.findActiveByIdForUpdate(2L)).thenReturn(Optional.of(manager));

        var result = service.promoteAdmin(1L, 2L);

        assertThat(result.role()).isEqualTo("ADMIN");
        assertThat(manager.getTokenVersion()).isEqualTo(1);
        ArgumentCaptor<StaffRoleHistory> history = ArgumentCaptor.forClass(StaffRoleHistory.class);
        verify(historyRepository).save(history.capture());
        assertThat(history.getValue().getPreviousPermissions()).isEqualTo("TICKETING");
        assertThat(history.getValue().getNewPermissions()).isEqualTo("OPERATIONS,TICKETING");
        verify(cachePublisher).publishAfterCommit(2L, 1);
    }

    @Test
    void 최고관리자_강등은_둘이상일때만_두권한_MANAGER로_수행한다() {
        User actor = user(1L, "32100001", UserRole.ROLE_ADMIN);
        User target = user(2L, "32100002", UserRole.ROLE_ADMIN);

        var result = service.demoteAdmin(1L, 2L);

        assertThat(result.role()).isEqualTo("MANAGER");
        assertThat(result.permissions()).containsExactly("OPERATIONS", "TICKETING");
        assertThat(target.getTokenVersion()).isEqualTo(1);
        assertThatThrownBy(() -> service.demoteAdmin(1L, 1L)).isInstanceOf(StaffManagementException.class);
    }

    @Test
    void 마지막_최고관리자와_USER의_직접승격은_거부한다() {
        User admin = user(1L, "32100001", UserRole.ROLE_ADMIN);
        User regular = user(2L, "32100002", UserRole.ROLE_USER);
        when(userRepository.findActiveByIdForUpdate(2L)).thenReturn(Optional.of(regular));

        assertThatThrownBy(() -> service.demoteAdmin(1L, 2L)).isInstanceOf(StaffManagementException.class);
        assertThatThrownBy(() -> service.promoteAdmin(1L, 2L)).isInstanceOf(StaffManagementException.class);
    }

    private User user(Long id, String studentId, UserRole role) {
        User user = User.builder().studentId(studentId).password("encoded").name("이름")
                .college("단국대학교").major("컴퓨터공학과").academicStatus(AcademicStatus.ENROLLED).role(role).build();
        ReflectionTestUtils.setField(user, "id", id);
        users.put(id, user);
        return user;
    }
}
