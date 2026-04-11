package com.danzzan.domain.user.phoneverification.repository;

import com.danzzan.domain.user.phoneverification.model.entity.PhoneVerificationSession;
import com.danzzan.domain.user.phoneverification.model.entity.PhoneVerificationStatus;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;

public interface PhoneVerificationSessionRepository extends JpaRepository<PhoneVerificationSession, Long> {

    Optional<PhoneVerificationSession> findBySessionId(String sessionId);

    Optional<PhoneVerificationSession> findTopBySignupTokenOrderByCreatedAtDesc(String signupToken);

    long countBySignupTokenAndCreatedAtAfter(String signupToken, LocalDateTime createdAt);

    long countByRequestIpHashAndCreatedAtAfter(String requestIpHash, LocalDateTime createdAt);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update PhoneVerificationSession s
               set s.status = :expiredStatus,
                   s.lastCheckedAt = :now
             where s.status in :activeStatuses
               and s.expiresAt <= :now
            """)
    int expireSessions(
            @Param("activeStatuses") Collection<PhoneVerificationStatus> activeStatuses,
            @Param("expiredStatus") PhoneVerificationStatus expiredStatus,
            @Param("now") LocalDateTime now
    );

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from PhoneVerificationSession s
             where s.status in :cleanupStatuses
               and s.updatedAt < :cutoff
            """)
    int deleteRetainedSessions(
            @Param("cleanupStatuses") Collection<PhoneVerificationStatus> cleanupStatuses,
            @Param("cutoff") LocalDateTime cutoff
    );
}
