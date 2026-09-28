package com.danzzan.domain.user.repository;

import com.danzzan.domain.user.model.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByStudentId(String studentId);

    Optional<User> findByStudentIdAndDeletedFalse(String studentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.studentId = :studentId and u.deleted = false")
    Optional<User> findByStudentIdAndDeletedFalseForUpdate(@Param("studentId") String studentId);

    @Query("select u from User u where u.id = :userId and u.deleted = false")
    Optional<User> findActiveById(@Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :userId and u.deleted = false")
    Optional<User> findActiveByIdForUpdate(@Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.deleted = false and u.role = 'ROLE_ADMIN' order by u.id asc")
    List<User> findActiveAdminsForUpdate();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :userId")
    Optional<User> findByIdForUpdate(@Param("userId") Long userId);

    boolean existsByStudentId(String studentId);

    boolean existsByPhoneNumber(String phoneNumber);
}
