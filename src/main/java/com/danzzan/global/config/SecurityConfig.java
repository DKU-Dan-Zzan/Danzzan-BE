package com.danzzan.global.config;

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

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/auth/**").permitAll()
                        .requestMatchers(
                                "/user/login",
                                "/user/reissue",
                                "/user/dku/**",
                                "/user/password/reset/**"
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
                                "/lost-items/**",
                                "/tickets/events",
                                "/api/ads",
                                "/api/ads/**"
                        ).permitAll()
                        .requestMatchers("/tickets/request", "/tickets/status", "/tickets/redis/**").permitAll()
                        .requestMatchers("/api/admin/**", "/admin/map/**").hasRole("ADMIN")
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(ticketingJwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

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
        source.registerCorsConfiguration("/user/**", ticketingCors);
        source.registerCorsConfiguration("/tickets/**", ticketingCors);
        source.registerCorsConfiguration("/api/admin/events/**", ticketingCors);
        source.registerCorsConfiguration("/api/admin/ticket/**", ticketingCors);
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
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
