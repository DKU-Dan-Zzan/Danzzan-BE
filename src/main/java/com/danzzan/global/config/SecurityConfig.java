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
import org.springframework.security.authorization.AuthorizationDecision;
import com.danzzan.global.security.UserAdminAuthorizationService;
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

    private final com.danzzan.global.jwt.JwtAuthenticationFilter ticketingJwtAuthenticationFilter;
    private final TicketingApiGateFilter ticketingApiGateFilter;
    private final UserAdminAuthorizationService userAdminAuthorizationService;

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

                    // 티켓팅 사용 여부는 관리자 축제 설정에서 정한다. 껐을 때 /tickets/** 를
                    // 막는 일은 TicketingApiGateFilter 가 맡는다. 로그인·내 정보(/user/**)는
                    // 티켓팅과 무관하게 항상 열어 둔다.

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
                        .requestMatchers("/api/admin/staff/**").hasRole("ADMIN")
                        .requestMatchers("/api/admin/events/**", "/admin/festival/ticketing-settings", "/admin/festival/ticketing-background")
                        .access((authentication, context) -> new AuthorizationDecision(
                                userAdminAuthorizationService.hasTicketingRole(authentication.get())))
                        .requestMatchers("/api/admin/**", "/admin/map/**", "/admin/timetable/**", "/admin/festival/**")
                        .access((authentication, context) -> new AuthorizationDecision(
                                userAdminAuthorizationService.hasOperationsRole(authentication.get())))
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated();
                })
                .addFilterBefore(ticketingApiGateFilter, UsernamePasswordAuthenticationFilter.class)
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
    public FilterRegistrationBean<TicketingApiGateFilter> ticketingApiGateFilterRegistration(
            TicketingApiGateFilter filter) {
        // 시큐리티 체인에만 두고 서블릿 체인에는 중복 등록하지 않는다.
        FilterRegistrationBean<TicketingApiGateFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
