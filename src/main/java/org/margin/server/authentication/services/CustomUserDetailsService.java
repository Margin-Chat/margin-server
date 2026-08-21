package org.margin.server.authentication.services;

import org.margin.server.users.models.User;
import org.margin.server.users.api.UserLookup;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserLookup userLookup;
    private final UserSecurityService userSecurityService;

    public CustomUserDetailsService(UserLookup userLookup, UserSecurityService userSecurityService) {
        this.userLookup = userLookup;
        this.userSecurityService = userSecurityService;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userLookup.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));

        if (user.isGuest()) {
            throw new UsernameNotFoundException("User not found: " + email);
        }

        Instant lockedUntil = userSecurityService.get(user.getId()).getAccountLockedUntil();
        if (lockedUntil != null && lockedUntil.isAfter(Instant.now())) {
            throw new UsernameNotFoundException("Account is locked");
        }

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password(user.getPassword())
                .roles("USER")
                .build();
    }
}