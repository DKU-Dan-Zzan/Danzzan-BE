package com.danzzan.domain.auth.service;

import com.danzzan.domain.auth.dto.RequestSignupDto;
import com.danzzan.domain.user.exception.AlreadyStudentIdException;
import com.danzzan.domain.user.model.entity.AcademicStatus;
import com.danzzan.domain.user.model.entity.User;
import com.danzzan.domain.user.model.entity.UserRole;
import com.danzzan.domain.user.phoneverification.exception.PhoneVerificationErrorType;
import com.danzzan.domain.user.phoneverification.exception.PhoneVerificationException;
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
        SignupTokenStore.VerifiedPhoneCache verifiedPhone = signupTokenStore.getVerifiedPhone(signupToken)
                .orElseThrow(() -> new PhoneVerificationException(PhoneVerificationErrorType.NOT_VERIFIED));

        if (userRepository.existsByStudentId(cache.studentId())) {
            throw new AlreadyStudentIdException();
        }

        String encodedPassword = passwordEncoder.encode(dto.getPassword());

        User user = User.builder()
                .studentId(cache.studentId())
                .password(encodedPassword)
                .naverId(dto.getNaverId().trim())
                .name(cache.name())
                .college(cache.college())
                .major(cache.major())
                .academicStatus(cache.academicStatus())
                .role(UserRole.ROLE_USER)
                .phoneNumber(verifiedPhone.phoneNumber())
                .phoneVerified(true)
                .phoneVerifiedAt(verifiedPhone.verifiedAt())
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
