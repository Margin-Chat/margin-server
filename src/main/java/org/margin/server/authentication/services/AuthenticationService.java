package org.margin.server.authentication.services;

import lombok.extern.slf4j.Slf4j;
import org.margin.server.social.models.space.SpaceMember;
import org.margin.server.social.models.space.enums.SpaceMemberRole;
import org.margin.server.social.repositories.SpaceMemberRepository;
import org.margin.server.social.services.SpacesService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.margin.server.authentication.models.AuthResponse;
import org.margin.server.users.models.Role;
import org.margin.server.users.models.User;
import org.margin.server.users.repositories.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@Slf4j
public class AuthenticationService {
    private static final int MAX_FAILED_ATTEMPTS = 3;
    private static final int LOCK_DURATION_SECONDS = 30;

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final SpaceMemberRepository spaceMemberRepository;
    private final SpacesService spacesService;

    public AuthenticationService(
            AuthenticationManager authenticationManager,
            UserRepository userRepository,
            JwtService jwtService,
            PasswordEncoder passwordEncoder,
            SpaceMemberRepository spaceMemberRepository, SpacesService spacesService) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.spaceMemberRepository = spaceMemberRepository;
        this.spacesService = spacesService;
    }

    public AuthResponse authenticateUser(String username, String password) {
        try {
            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

            if (isAccountLocked(user)) {
                throw new BadCredentialsException("Account is locked until " + user.getAccountLockedUntil());
            }

            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(user.getUsername(), password)
            );

            resetFailedAttempts(user);

            String token = jwtService.generateToken(user.getUsername(), user.getId());

            log.info("User {} authenticated successfully", user.getUsername());

            return new AuthResponse(true,
                    "Login successful",
                    token, user.getPublicKey(),
                    user.getEncryptedPrivateKey(),
                    user.getSalt(),
                    user.getIv());

        } catch (BadCredentialsException e) {
            handleFailedLogin(username);
            throw new BadCredentialsException("Invalid credentials");
        }
    }

    public void registerUser(String username,
                             String email,
                             String password,
                             String privateKey,
                             String publicKey,
                             String salt,
                             String iv) {
        if (userRepository.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("Username already exists");
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(Role.USER);
        user.setSalt(salt);
        user.setIv(iv);
        user.setPublicKey(publicKey);
        user.setEncryptedPrivateKey(privateKey);

        userRepository.save(user);

        // it's only temporarily I've added new users to general chat like this
        SpaceMember member = new SpaceMember();
        member.setSpace(spacesService.getById(1L));
        member.setUser(user);
        member.setJoinedAt(LocalDateTime.now());
        member.setRole(SpaceMemberRole.MEMBER);

        spaceMemberRepository.save(member);
    }

    private boolean isAccountLocked(User user) {
        if (user.getAccountLockedUntil() == null) {
            return false;
        }

        return user.getAccountLockedUntil().isAfter(LocalDateTime.now());
    }

    private void handleFailedLogin(String username) {
        userRepository.findByUsername(username).ifPresent(user -> {
            user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);
            user.setLastFailedLoginAttempt(LocalDateTime.now());

            if (user.getFailedLoginAttempts() >= MAX_FAILED_ATTEMPTS) {
                user.setAccountLockedUntil(LocalDateTime.now().plusSeconds(LOCK_DURATION_SECONDS));
                log.warn("Account locked for user {} until {}",
                        user.getUsername(), user.getAccountLockedUntil());
            }

            userRepository.save(user);
        });
    }

    private void resetFailedAttempts(User user) {
        if (user.getFailedLoginAttempts() > 0) {
            user.setFailedLoginAttempts(0);
            user.setLastFailedLoginAttempt(null);
            user.setAccountLockedUntil(null);
            userRepository.save(user);
        }
    }
}
