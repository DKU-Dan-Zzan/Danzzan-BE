package com.danzzan.global.jwt;

import com.danzzan.domain.user.model.entity.User;
import com.danzzan.domain.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

// 紐⑤뱺 ?붿껌?먯꽌 Authorization ?ㅻ뜑??JWT ?좏겙??寃利앺븯怨?
// ?좏슚??寃쎌슦 SecurityContext???몄쬆 ?뺣낫瑜??ㅼ젙?섎뒗 ?꾪꽣
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    private static final java.util.Set<String> PUBLIC_PATHS = java.util.Set.of(
            "/user/login",
            "/user/reissue",
            "/auth/login",
            "/auth/reissue",
            "/auth/logout"
    );

    // CORS preflight(OPTIONS)와 인증 불필요 공개 경로는 건너뜁니다.
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
        String path = request.getRequestURI();
        return PUBLIC_PATHS.contains(path)
                || path.startsWith("/user/dku/")
                || path.startsWith("/user/password/reset/")
                || path.startsWith("/user/phone-verifications/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String token = resolveToken(request);

        if (token != null) {
            // 토큰이 있지만 서명/만료 검증 실패 → 401 반환 (프론트 갱신 트리거)
            if (!jwtTokenProvider.validateToken(token)) {
                sendUnauthorized(response, "토큰이 만료되었거나 유효하지 않습니다.");
                return;
            }

            Long userId = jwtTokenProvider.getUserId(token);
            String role = jwtTokenProvider.getRole(token);
            if (role == null || role.isBlank()) {
                filterChain.doFilter(request, response);
                return;
            }

            int tokenVersion = jwtTokenProvider.getTokenVersion(token);
            User user = userRepository.findById(userId).orElse(null);

            // tokenVersion 불일치(비밀번호 변경 등) → 401 반환 (프론트 갱신 트리거)
            if (user == null || user.getTokenVersion() != tokenVersion) {
                sendUnauthorized(response, "토큰 버전이 유효하지 않습니다. 다시 로그인해 주세요.");
                return;
            }

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            userId,
                            null,
                            List.of(new SimpleGrantedAuthority(role))
                    );

            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }

    // Authorization ?ㅻ뜑?먯꽌 "Bearer " ?묐몢???쒓굅 ???좏겙 異붿텧
    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"error\":\"" + message + "\",\"status\":401}");
    }
}
