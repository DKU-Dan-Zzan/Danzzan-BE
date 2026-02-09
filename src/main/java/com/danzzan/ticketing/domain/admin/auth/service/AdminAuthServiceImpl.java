package com.danzzan.ticketing.domain.admin.auth.service;

import com.danzzan.ticketing.domain.admin.auth.dto.AdminInfoDTO;
import com.danzzan.ticketing.domain.admin.auth.dto.AdminLoginRequestDTO;
import com.danzzan.ticketing.domain.admin.auth.dto.AdminLoginResponseDTO;
import com.danzzan.ticketing.domain.admin.auth.dto.AdminLogoutResponseDTO;
import com.danzzan.ticketing.domain.admin.auth.dto.AdminMeResponseDTO;
import com.danzzan.ticketing.domain.admin.auth.exception.AdminAuthenticationException;
import com.danzzan.ticketing.domain.admin.auth.exception.AdminForbiddenException;
import com.danzzan.ticketing.domain.user.exception.UserNotFoundException;
import com.danzzan.ticketing.domain.user.exception.WrongPasswordException;
import com.danzzan.ticketing.domain.user.model.entity.User;
import com.danzzan.ticketing.domain.user.model.entity.UserRole;
import com.danzzan.ticketing.domain.user.repository.UserRepository;
import com.danzzan.ticketing.domain.user.service.UserInfoService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminAuthServiceImpl implements AdminAuthService {

    private final UserRepository userRepository;
    private final UserInfoService userInfoService;
    private final PasswordEncoder passwordEncoder;
    private final AdminTokenStore adminTokenStore;

    @Override
    @Transactional(readOnly = true)
    public AdminLoginResponseDTO login(AdminLoginRequestDTO request) {
        User user = userRepository.findByStudentId(request.getStudentId())
                .orElseThrow(UserNotFoundException::new);

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new WrongPasswordException();
        }

        if (user.getRole() != UserRole.ROLE_ADMIN) {
            throw new AdminForbiddenException();
        }

        userInfoService.cacheUserInfo(user.getId(), user);

        String accessToken = adminTokenStore.issueToken(user.getId());

        return AdminLoginResponseDTO.builder()
                .accessToken(accessToken)
                .admin(AdminInfoDTO.builder()
                        .id(user.getId())
                        .name(user.getName())
                        .studentId(user.getStudentId())
                        .role(user.getRole())
                        .build())
                .system(request.getSystem())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public AdminMeResponseDTO me(String accessToken) {
        User admin = resolveAdmin(accessToken);
        return AdminMeResponseDTO.builder()
                .adminId(admin.getId())
                .adminName(admin.getName())
                .studentId(admin.getStudentId())
                .role(admin.getRole())
                .build();
    }

    @Override
    @Transactional
    public AdminLogoutResponseDTO logout(String accessToken) {
        if (adminTokenStore.getUserId(accessToken) == null) {
            throw new AdminAuthenticationException();
        }
        adminTokenStore.revoke(accessToken);
        return AdminLogoutResponseDTO.builder()
                .ok(true)
                .build();
    }

    private User resolveAdmin(String accessToken) {
        Long userId = adminTokenStore.getUserId(accessToken);
        if (userId == null) {
            throw new AdminAuthenticationException();
        }

        User user = userRepository.findById(userId)
                .orElseThrow(AdminAuthenticationException::new);

        if (user.getRole() != UserRole.ROLE_ADMIN) {
            throw new AdminForbiddenException();
        }

        return user;
    }
}
