package com.danzzan.global.config;

import com.danzzan.domain.admin.entity.AdminEntity;
import com.danzzan.domain.admin.repository.AdminRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.seed.dev-admin", name = "enabled", havingValue = "true")
public class DevAdminEntityInitializer implements CommandLineRunner {

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed.dev-admin.student-number:1234}")
    private String studentNumber;

    @Value("${app.seed.dev-admin.password:1234}")
    private String rawPassword;

    @Override
    public void run(String... args) {
        if (adminRepository.findByStudentNumber(studentNumber).isPresent()) {
            return;
        }

        adminRepository.save(AdminEntity.create(studentNumber, passwordEncoder.encode(rawPassword)));
        log.info("개발용 admin_entity 계정 생성 완료: studentNumber={}", studentNumber);
    }
}
