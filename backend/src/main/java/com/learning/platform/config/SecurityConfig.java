package com.learning.platform.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.platform.security.JwtAuthenticationFilter;
import com.learning.platform.security.RateLimitFilter;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity // enables @PreAuthorize on controller/service methods - see RBAC checks
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitFilter rateLimitFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, RateLimitFilter rateLimitFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.rateLimitFilter = rateLimitFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * This app never uses Spring Security's own AuthenticationManager/UserDetailsService flow -
     * AuthService checks passwords with BCrypt directly and issues JWTs itself (see AuthService,
     * JwtAuthenticationFilter). Without *some* UserDetailsService bean present, Spring Boot's
     * autoconfiguration silently creates a default in-memory one with a random generated
     * password logged on every startup - a confusing, unused fallback "user" that's more
     * attack-surface-shaped noise than anything this app actually relies on. An empty one here
     * suppresses that without pretending this app has a real username/password store beyond
     * UserRepository.
     */
    @Bean
    public org.springframework.security.core.userdetails.UserDetailsService userDetailsService() {
        return username -> {
            throw new org.springframework.security.core.userdetails.UsernameNotFoundException(
                    "This application authenticates via /api/auth/login, not Spring Security's UserDetailsService");
        };
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable()) // stateless JWT API, no cookies/sessions to protect against CSRF
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(unauthenticatedEntryPoint()))
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers("/api/auth/**").permitAll()
                                        .requestMatchers(
                                                "/swagger-ui/**",
                                                "/v3/api-docs/**",
                                                "/actuator/health")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .addFilterBefore(jwtAuthenticationFilter, AuthorizationFilter.class)
                // Rate limiting runs before authentication is even attempted - a brute-force
                // login attempt shouldn't get a free JWT-parsing pass before being throttled.
                .addFilterBefore(rateLimitFilter, JwtAuthenticationFilter.class);
        return http.build();
    }

    /**
     * Without this, Spring Security's default behavior for a stateless (no formLogin/httpBasic)
     * filter chain is to answer a request with no/invalid credentials with 403 Forbidden - the
     * same status as "you're logged in but not allowed to do this" ({@code @PreAuthorize}
     * failures, handled separately in GlobalExceptionHandler). Conflating "who are you?" with
     * "you can't do that" is a real, easy-to-miss REST API correctness issue, not just a style
     * preference - a client can't distinguish "log in" from "this isn't for you" without it.
     * Caught by AuthIntegrationTest asserting 401 specifically, not just "any 4xx".
     */
    @Bean
    public AuthenticationEntryPoint unauthenticatedEntryPoint() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        return (request, response, authException) -> {
            response.setStatus(401);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("timestamp", Instant.now().toString());
            body.put("status", 401);
            body.put("error", "Authentication required");
            objectMapper.writeValue(response.getWriter(), body);
        };
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("http://localhost:*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
