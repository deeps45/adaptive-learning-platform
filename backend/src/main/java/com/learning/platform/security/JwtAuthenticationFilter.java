package com.learning.platform.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            Claims claims = jwtService.parseClaims(token);
            // Explicitly reject a refresh token presented as an access token - without this
            // check, a leaked/logged refresh token could be used directly against every
            // protected endpoint instead of only the /auth/refresh endpoint it's meant for.
            if (claims != null && !jwtService.isRefreshToken(claims)) {
                UUID userId = UUID.fromString(claims.getSubject());
                String email = claims.get("email", String.class);
                String role = claims.get("role", String.class);
                UserPrincipal principal = new UserPrincipal(userId, email, role);

                var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
                var authentication =
                        new UsernamePasswordAuthenticationToken(principal, null, authorities);
                // Explicit new context + setContext(), not getContext().setAuthentication() on
                // the existing one - the pattern Spring Security's own reference docs currently
                // recommend for a custom authentication filter.
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(authentication);
                SecurityContextHolder.setContext(context);
            }
        }
        filterChain.doFilter(request, response);
    }
}
