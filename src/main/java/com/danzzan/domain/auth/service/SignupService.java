package com.danzzan.domain.auth.service;

import com.danzzan.domain.auth.dto.RequestSignupDto;
import com.danzzan.domain.user.exception.AlreadyStudentIdException;
import com.danzzan.domain.user.model.entity.AcademicStatus;
import com.danzzan.domain.user.model.entity.User;
import com.danzzan.domain.user.model.entity.UserRole;
import com.danzzan.domain.user.phoneverification.service.PhoneVerificationService;
import com.danzzan.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SignupService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SignupTokenStore signupTokenStore;
    private final PhoneVerificationService phoneVerificationService;

    public void cacheStudentInfo(String signupToken, String studentId, String name,
                                 String college, String major, AcademicStatus academicStatus) {
        signupTokenStore.cacheStudentInfo(signupToken, studentId, name, college, major, academicStatus);
    }

    public StudentInfoCache getCachedStudentInfo(String signupToken) {
        SignupTokenStore.StudentInfoCache cache = signupTokenStore.getCachedStudentInfo(signupToken);
        return new StudentInfoCache(
                cache.studentId(),
                cache.name(),
                cache.college(),
                cache.major(),
                cache.academicStatus()
        );
    }

    @Transactional
    public void signup(RequestSignupDto dto, String signupToken) {
        StudentInfoCache cache = getCachedStudentInfo(signupToken);
        String verifiedPhoneNumber = phoneVerificationService.consumeVerifiedPhoneNumber(
                signupToken,
                dto.getPhoneVerificationSessionId().trim()
        );

        if (userRepository.existsByStudentId(cache.studentId())) {
            throw new AlreadyStudentIdException();
        }

        String encodedPassword = passwordEncoder.encode(dto.getPassword());
        LocalDateTime phoneVerifiedAt = LocalDateTime.now();

        User user = User.builder()
                .studentId(cache.studentId())
                .password(encodedPassword)
                .name(cache.name())
                .college(cache.college())
                .major(cache.major())
                .academicStatus(cache.academicStatus())
                .role(UserRole.ROLE_USER)
                .phoneNumber(verifiedPhoneNumber)
                .phoneVerified(true)
                .phoneVerifiedAt(phoneVerifiedAt)
                .build();

        userRepository.save(user);
        signupTokenStore.remove(signupToken);
    }

    public record StudentInfoCache(
            String studentId,
            String name,
            String college,
            String major,
            AcademicStatus academicStatus
    ) {}
}
