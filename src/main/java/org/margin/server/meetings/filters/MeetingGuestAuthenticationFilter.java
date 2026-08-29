package org.margin.server.meetings.filters;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import org.margin.server.meetings.security.MeetingGuestPrincipal;
import org.margin.server.meetings.services.MeetingGuestTokenService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Component
public class MeetingGuestAuthenticationFilter extends OncePerRequestFilter {

    public static final String MEETING_GUEST = "MEETING_GUEST";

    private final MeetingGuestTokenService guestTokenService;

    public MeetingGuestAuthenticationFilter(MeetingGuestTokenService guestTokenService) {
        this.guestTokenService = guestTokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        Optional<MeetingGuestPrincipal> guest = guestTokenService.parse(header.substring(7));
        if (guest.isPresent()) {
            UsernamePasswordAuthenticationToken token =
                    new UsernamePasswordAuthenticationToken(
                            guest.get(), null, List.of(new SimpleGrantedAuthority(MEETING_GUEST)));
            token.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(token);
        }

        filterChain.doFilter(request, response);
    }
}
