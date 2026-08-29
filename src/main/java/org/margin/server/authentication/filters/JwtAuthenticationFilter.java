package org.margin.server.authentication.filters;

import org.margin.server.shared.security.AuthenticatedUser;
import org.margin.server.authentication.services.UserSecurityService;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.margin.server.authentication.services.JwtService;
import org.margin.server.users.models.User;
import org.margin.server.users.api.UserLookup;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String MARGIN_USER = "MARGIN_USER";

    private final JwtService jwtService;
    private final UserLookup userLookup;
    private final UserSecurityService userSecurityService;

    public JwtAuthenticationFilter(JwtService jwtService,
                                   UserLookup userLookup,
                                   UserSecurityService userSecurityService) {
        this.jwtService = jwtService;
        this.userLookup = userLookup;
        this.userSecurityService = userSecurityService;
    }

    private static List<GrantedAuthority> marginUserAuthorities(User user) {
        List<GrantedAuthority> authorities = new ArrayList<>(user.getAuthorities());
        authorities.add(new SimpleGrantedAuthority(MARGIN_USER));
        return authorities;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String jwt = authHeader.substring(7);
            if (jwt.isBlank()) {
                filterChain.doFilter(request, response);
                return;
            }
            String email = jwtService.extractEmail(jwt).toLowerCase();

            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                User user = userLookup.findByEmail(email)
                        .orElse(null);

                if (user != null
                        && !user.isGuest()
                        && jwtService.isTokenValid(jwt, user.getEmail())
                        && userSecurityService.get(user.getId()).getTokenVersion() == jwtService.extractTokenVersion(jwt)) {
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    new AuthenticatedUser(user.getId(), user.getEmail(), user.getDisplayName()),
                                    null,
                                    marginUserAuthorities(user)
                            );

                    authToken.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request)
                    );

                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (ExpiredJwtException e) {
            log.debug("Expired JWT for {}", e.getClaims().getSubject());
        } catch (Exception e) {
            log.warn("Cannot set user authentication: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}