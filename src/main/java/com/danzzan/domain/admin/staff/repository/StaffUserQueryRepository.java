package com.danzzan.domain.admin.staff.repository;

import com.danzzan.domain.admin.staff.dto.StaffFilter;
import com.danzzan.domain.user.model.entity.User;
import com.danzzan.domain.user.model.entity.UserRole;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class StaffUserQueryRepository {

    private final EntityManager entityManager;

    public Page<User> findActiveStaff(Pageable pageable, StaffFilter filter) {
        String condition = switch (filter) {
            case ALL -> "u.role in :staffRoles";
            case ADMIN -> "u.role = :adminRole";
            case TICKETING -> "(u.role = :adminRole or (u.role = :managerRole and u.managerTicketing = true))";
            case OPERATIONS -> "(u.role = :adminRole or (u.role = :managerRole and u.managerOperations = true))";
            case BOTH -> "(u.role = :adminRole or (u.role = :managerRole and u.managerTicketing = true and u.managerOperations = true))";
        };
        TypedQuery<User> contentQuery = entityManager.createQuery("""
                        select u from User u
                        where u.deleted = false and (%s)
                        order by u.name asc, u.studentId asc
                        """.formatted(condition), User.class);
        List<User> content = bindFilter(contentQuery, filter)
                .setFirstResult((int) pageable.getOffset())
                .setMaxResults(pageable.getPageSize())
                .getResultList();
        TypedQuery<Long> totalQuery = entityManager.createQuery("""
                        select count(u) from User u
                        where u.deleted = false and (%s)
                        """.formatted(condition), Long.class);
        Long total = bindFilter(totalQuery, filter).getSingleResult();
        return new PageImpl<>(content, pageable, total);
    }

    private <T> TypedQuery<T> bindFilter(TypedQuery<T> query, StaffFilter filter) {
        return switch (filter) {
            case ALL -> query.setParameter("staffRoles", List.of(UserRole.ROLE_ADMIN, UserRole.ROLE_MANAGER));
            case ADMIN -> query.setParameter("adminRole", UserRole.ROLE_ADMIN);
            case TICKETING, OPERATIONS, BOTH -> query.setParameter("adminRole", UserRole.ROLE_ADMIN)
                    .setParameter("managerRole", UserRole.ROLE_MANAGER);
        };
    }
}
