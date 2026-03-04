package com.danzzan.domain.admin.repository;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.danzzan.domain.admin.entity.RefreshTokenEntity;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, Long> {
    Optional<RefreshTokenEntity> findByToken(String token);
    void deleteByToken(String token);
}
