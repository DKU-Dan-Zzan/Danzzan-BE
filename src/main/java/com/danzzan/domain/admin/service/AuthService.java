package com.danzzan.domain.admin.service;

import com.danzzan.domain.admin.dto.response.TokenResponse;
import com.danzzan.domain.user.model.entity.User;
import com.danzzan.domain.user.model.entity.UserRole;
import com.danzzan.domain.user.repository.UserRepository;
import com.danzzan.global.exception.AuthException;
import com.danzzan.global.jwt.JwtRevocationService;
import com.danzzan.global.jwt.JwtTokenProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtRevocationService jwtRevocationService;

    public TokenResponse login(String studentNumber, String password) {
        User admin = userRepository.findByStudentId(studentNumber)
                .orElseThrow(() -> new AuthException(HttpStatus.UNAUTHORIZED, "관리자를 찾을 수 없습니다."));

        if (!passwordEncoder.matches(password, admin.getPassword())) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "비밀번호가 일치하지 않습니다.");
        }
        validateAdminRole(admin);

        String accessToken = jwtTokenProvider.createAccessToken(
                admin.getId(),
                admin.getStudentId(),
                admin.getRole().name(),
                admin.getTokenVersion()
        );
        String refreshToken = jwtTokenProvider.createRefreshToken(admin.getId(), admin.getTokenVersion());

        jwtRevocationService.cacheUserVersion(admin.getId(), admin.getTokenVersion());

        return new TokenResponse(accessToken, refreshToken);
    }

    public TokenResponse reissue(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "Refresh Token이 없습니다.");
        }
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "유효하지 않은 Refresh Token입니다.");
        }

        Long userId = jwtTokenProvider.getUserId(refreshToken);
        int tokenVersion = jwtTokenProvider.getTokenVersion(refreshToken);

        User admin = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(HttpStatus.UNAUTHORIZED, "관리자 인증에 실패했습니다."));
        validateAdminRole(admin);
        if (admin.getTokenVersion() != tokenVersion) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "만료된 세션입니다. 다시 로그인해주세요.");
        }

        String newAccessToken = jwtTokenProvider.createAccessToken(
                admin.getId(),
                admin.getStudentId(),
                admin.getRole().name(),
                admin.getTokenVersion()
        );
        String newRefreshToken = jwtTokenProvider.createRefreshToken(admin.getId(), admin.getTokenVersion());

        jwtRevocationService.cacheUserVersion(admin.getId(), admin.getTokenVersion());

        return new TokenResponse(newAccessToken, newRefreshToken);
    }

    public void logout(String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank() && jwtTokenProvider.validateToken(refreshToken)) {
            Long userId = jwtTokenProvider.getUserId(refreshToken);
            int tokenVersion = jwtTokenProvider.getTokenVersion(refreshToken);

            userRepository.findById(userId)
                    .filter(user -> user.getRole() == UserRole.ROLE_ADMIN)
                    .filter(user -> user.getTokenVersion() == tokenVersion)
                    .ifPresent(user -> {
                        user.bumpTokenVersion();
                        userRepository.save(user);
                        jwtRevocationService.cacheUserVersion(user.getId(), user.getTokenVersion());
                    });
        }
    }

    private void validateAdminRole(User user) {
        if (user.getRole() != UserRole.ROLE_ADMIN) {
            throw new AuthException(HttpStatus.FORBIDDEN, "관리자 권한이 필요합니다.");
        }
    }
}
