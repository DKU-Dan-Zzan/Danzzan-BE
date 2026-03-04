package com.danzzan.global.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
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

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String token = resolveToken(request);

        if (token != null && jwtTokenProvider.validateToken(token)) {
            Long userId = jwtTokenProvider.getUserId(token);
            String role = jwtTokenProvider.getRole(token);
            if (role == null || role.isBlank()) {
                filterChain.doFilter(request, response);
                return;
            }

            // SecurityContext???몄쬆 ?뺣낫 ?ㅼ젙
            // principal??userId瑜??ｌ뼱??而⑦듃濡ㅻ윭/?쒕퉬?ㅼ뿉??爰쇰궡 ?????덈룄濡???
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
}
