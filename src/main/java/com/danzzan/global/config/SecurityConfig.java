package com.danzzan.global.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Value("${app.cors.allowed-origin-patterns:http://localhost:*,http://127.0.0.1:*}")
    private String allowedOriginPatterns;

    /**
     * 가을 축제는 티켓팅을 하지 않는다. 프론트에서 화면을 막아도 API는 그대로 열려 있어
     * URL로 직접 호출하면 응답하므로, 여기서 함께 차단한다.
     *
     * 코드를 지우지 않고 플래그로 끄는 이유는 내년 봄에 되살릴 때 이 값만 true로
     * 바꾸면 되게 하기 위해서다.
     */
    @Value("${app.ticketing.api-enabled:false}")
    private boolean ticketingApiEnabled;

    private final com.danzzan.global.jwt.JwtAuthenticationFilter ticketingJwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .authorizeHttpRequests(auth -> {
                    // OPTIONS 는 CORS 프리플라이트라 차단보다 먼저 허용해야 한다.
                    auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();

                    if (!ticketingApiEnabled) {
                        // /auth/** 는 관리자 로그인 경로이므로 건드리지 않는다.
                        auth.requestMatchers("/tickets/**", "/user/**").denyAll();
                    }

                    auth
                        .requestMatchers("/auth/**").permitAll()
                        .requestMatchers(
                                "/user/login",
                                "/user/reissue",
                                "/user/dku/**",
                                "/user/password/reset/**",
                                "/user/phone-verifications/**"
                        )
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/user/{signup-token}").permitAll()
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                        .permitAll()
                        .requestMatchers("/actuator/health", "/actuator/prometheus")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/",
                                "/health",
                                "/home/**",
                                "/notices/**",
                                "/timetable/**",
                                "/map/**",
                                "/booths/**",
                                "/tickets/events",
                                "/api/ads",
                                "/api/ads/list",
                                "/festival/settings"
                        ).permitAll()
                        .requestMatchers("/tickets/request", "/tickets/status", "/tickets/redis/**").permitAll()
                        .requestMatchers("/api/admin/**", "/admin/map/**", "/admin/timetable/**", "/admin/festival/**").hasRole("ADMIN")
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated();
                })
                .addFilterBefore(ticketingJwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) ->
                                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized"))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden")));

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        List<String> parsedAllowedOrigins = parseCsv(allowedOrigins);
        List<String> parsedAllowedOriginPatterns = parseCsv(allowedOriginPatterns);

        CorsConfiguration festivalCors = new CorsConfiguration();
        festivalCors.setAllowedOrigins(parsedAllowedOrigins);
        festivalCors.setAllowedOriginPatterns(parsedAllowedOriginPatterns);
        festivalCors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        festivalCors.setAllowedHeaders(List.of("*"));
        festivalCors.setAllowCredentials(true);

        CorsConfiguration ticketingCors = new CorsConfiguration();
        ticketingCors.setAllowedOrigins(parsedAllowedOrigins);
        ticketingCors.setAllowedOriginPatterns(parsedAllowedOriginPatterns);
        ticketingCors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        ticketingCors.setAllowedHeaders(List.of("*"));
        ticketingCors.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/auth/**", ticketingCors);
        source.registerCorsConfiguration("/user/**", ticketingCors);
        source.registerCorsConfiguration("/tickets/**", ticketingCors);
        source.registerCorsConfiguration("/api/admin/**", ticketingCors);
        source.registerCorsConfiguration("/**", festivalCors);
        return source;
    }

    private List<String> parseCsv(String value) {
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    @Bean
    public FilterRegistrationBean<com.danzzan.global.jwt.JwtAuthenticationFilter> userJwtFilterRegistration(
            com.danzzan.global.jwt.JwtAuthenticationFilter filter) {
        FilterRegistrationBean<com.danzzan.global.jwt.JwtAuthenticationFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
