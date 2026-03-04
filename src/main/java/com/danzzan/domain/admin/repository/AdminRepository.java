package com.danzzan.domain.admin.repository;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.danzzan.domain.admin.entity.AdminEntity;

public interface AdminRepository extends JpaRepository<AdminEntity, Long> {
    Optional<AdminEntity> findByStudentNumber(String studentNumber);
}
