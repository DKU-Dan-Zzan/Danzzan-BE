package com.danzzan.domain.admin.staff.repository;

import com.danzzan.domain.user.model.entity.AcademicStatus;
import com.danzzan.domain.user.model.entity.User;
import com.danzzan.domain.user.model.entity.UserRole;
import com.danzzan.domain.user.model.entity.ManagerPermission;
import com.danzzan.domain.admin.staff.dto.StaffFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect")
@Import(StaffUserQueryRepository.class)
class StaffUserQueryRepositoryTest {
    @Autowired private StaffUserQueryRepository repository;
    @Autowired private TestEntityManager entityManager;

    @Test
    void 활성_ADMIN과_MANAGER를_이름순으로_조회하고_동명이인은_학번순으로_페이징한다() {
        persist("003", "김매니저", UserRole.ROLE_MANAGER, false);
        persist("001", "박관리자", UserRole.ROLE_ADMIN, false);
        persist("006", "김매니저", UserRole.ROLE_MANAGER, false);
        persist("002", UserRole.ROLE_USER, false);
        persist("004", UserRole.ROLE_ADMIN, true);
        persist("005", UserRole.ROLE_MANAGER, true);
        entityManager.flush();
        entityManager.clear();

        var first = repository.findActiveStaff(PageRequest.of(0, 1), StaffFilter.ALL);
        var second = repository.findActiveStaff(PageRequest.of(1, 1), StaffFilter.ALL);
        var third = repository.findActiveStaff(PageRequest.of(2, 1), StaffFilter.ALL);
        assertThat(first.getContent()).extracting(User::getStudentId).containsExactly("003");
        assertThat(second.getContent()).extracting(User::getStudentId).containsExactly("006");
        assertThat(third.getContent()).extracting(User::getStudentId).containsExactly("001");
        assertThat(first.getTotalElements()).isEqualTo(3);
        assertThat(second.getTotalElements()).isEqualTo(3);
        assertThat(first.getTotalPages()).isEqualTo(3);
    }

    @Test
    void 권한_필터는_해당_업무_권한을_보유한_매니저와_최고관리자를_집계한다() {
        persist("001", UserRole.ROLE_ADMIN, false);
        persist("002", UserRole.ROLE_MANAGER, false, ManagerPermission.TICKETING);
        persist("003", UserRole.ROLE_MANAGER, false, ManagerPermission.OPERATIONS);
        persist("004", UserRole.ROLE_MANAGER, false, ManagerPermission.OPERATIONS, ManagerPermission.TICKETING);
        persist("005", UserRole.ROLE_USER, false, ManagerPermission.TICKETING);
        persist("006", UserRole.ROLE_MANAGER, true, ManagerPermission.OPERATIONS, ManagerPermission.TICKETING);
        persist("007", UserRole.ROLE_MANAGER, false, ManagerPermission.OPERATIONS, ManagerPermission.TICKETING);
        persist("008", UserRole.ROLE_ADMIN, true);
        entityManager.flush();
        entityManager.clear();

        assertThat(repository.findActiveStaff(PageRequest.of(0, 10), StaffFilter.ADMIN).getContent())
                .extracting(User::getStudentId).containsExactly("001");
        var ticketingFirst = repository.findActiveStaff(PageRequest.of(0, 2), StaffFilter.TICKETING);
        var ticketingSecond = repository.findActiveStaff(PageRequest.of(1, 2), StaffFilter.TICKETING);
        assertThat(ticketingFirst.getContent()).extracting(User::getStudentId).containsExactly("001", "002");
        assertThat(ticketingSecond.getContent()).extracting(User::getStudentId).containsExactly("004", "007");
        assertThat(ticketingFirst.getTotalElements()).isEqualTo(4);
        assertThat(ticketingFirst.getTotalPages()).isEqualTo(2);
        assertThat(repository.findActiveStaff(PageRequest.of(0, 10), StaffFilter.OPERATIONS).getContent())
                .extracting(User::getStudentId).containsExactly("001", "003", "004", "007");
        var bothFirst = repository.findActiveStaff(PageRequest.of(0, 1), StaffFilter.BOTH);
        var bothSecond = repository.findActiveStaff(PageRequest.of(1, 1), StaffFilter.BOTH);
        var bothThird = repository.findActiveStaff(PageRequest.of(2, 1), StaffFilter.BOTH);
        assertThat(bothFirst.getContent()).extracting(User::getStudentId).containsExactly("001");
        assertThat(bothSecond.getContent()).extracting(User::getStudentId).containsExactly("004");
        assertThat(bothThird.getContent()).extracting(User::getStudentId).containsExactly("007");
        assertThat(bothFirst.getTotalElements()).isEqualTo(3);
        assertThat(bothFirst.getTotalPages()).isEqualTo(3);
    }

    private void persist(String studentId, UserRole role, boolean deleted) {
        persist(studentId, role, deleted, new ManagerPermission[0]);
    }

    private void persist(String studentId, UserRole role, boolean deleted, ManagerPermission... permissions) {
        persist(studentId, "테스트", role, deleted, permissions);
    }

    private void persist(String studentId, String name, UserRole role, boolean deleted, ManagerPermission... permissions) {
        User user = User.builder().studentId(studentId).password("encoded").name(name)
                .college("공과대학").major("컴퓨터공학").academicStatus(AcademicStatus.ENROLLED).role(role).build();
        user.changeManagerPermissions(java.util.List.of(permissions));
        if (deleted) user.withdraw(studentId, "withdrawn");
        entityManager.persist(user);
    }
}
