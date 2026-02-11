package com.danzzan.ticketing.global.config;

import com.danzzan.ticketing.domain.user.model.entity.AcademicStatus;
import com.danzzan.ticketing.domain.user.model.entity.User;
import com.danzzan.ticketing.domain.user.model.entity.UserRole;
import com.danzzan.ticketing.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DevDataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        // 관리자 계정이 없으면 생성
        if (!userRepository.existsByStudentId("1234")) {
            User admin = User.builder()
                    .studentId("1234")
                    .password(passwordEncoder.encode("1234"))
                    .name("관리자")
                    .college("SW융합대학")
                    .major("소프트웨어학과")
                    .academicStatus(AcademicStatus.ENROLLED)
                    .role(UserRole.ROLE_ADMIN)
                    .build();
            userRepository.save(admin);
            log.info("개발용 관리자 계정 생성 완료: studentId=1234, password=1234");
        }
    }
}
