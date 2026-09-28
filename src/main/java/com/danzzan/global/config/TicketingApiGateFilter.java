package com.danzzan.global.config;

import com.danzzan.domain.festival.service.TicketingAccessPolicy;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 티켓팅을 쓰지 않는 축제에서는 티켓팅 API 를 닫는다.
 *
 * 프론트에서 화면을 감춰도 URL 을 직접 치면 API 는 응답하므로 서버에서도 막는다.
 * 로그인·회원가입·내 정보(/user/**)는 티켓팅과 무관하게 항상 열어 둔다. 내 정보는
 * 티켓팅을 하지 않는 축제에도 필요하다.
 */
@Component
@RequiredArgsConstructor
public class TicketingApiGateFilter extends OncePerRequestFilter {

    private final TicketingAccessPolicy ticketingAccessPolicy;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (isTicketingPath(request) && !ticketingAccessPolicy.isTicketingEnabled()) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"message\":\"티켓팅을 사용하지 않는 축제입니다.\",\"status\":403}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isTicketingPath(HttpServletRequest request) {
        // CORS 프리플라이트까지 막으면 브라우저가 에러 응답조차 읽지 못한다.
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        return path.equals("/tickets") || path.startsWith("/tickets/");
    }
}
