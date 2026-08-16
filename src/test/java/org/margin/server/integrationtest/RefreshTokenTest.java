package org.margin.server.integrationtest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.margin.server.authentication.entities.RefreshToken;
import org.margin.server.authentication.exceptions.InvalidRefreshTokenException;
import org.margin.server.authentication.models.AuthResponse;
import org.margin.server.authentication.repositories.RefreshTokenRepository;
import org.margin.server.authentication.services.AuthenticationService;
import org.margin.server.authentication.services.RefreshTokenService;
import org.margin.server.authentication.services.UserSecurityService;
import org.margin.server.integrationtest.config.MarginTestRunner;
import org.margin.server.integrationtest.utils.AuthTestUtils;
import org.margin.server.users.models.User;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RefreshTokenTest extends MarginTestRunner {

    private static final String PASSWORD = "Password123!";

    @Autowired
    private AuthenticationService authenticationService;
    @Autowired
    private RefreshTokenService refreshTokenService;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private UserSecurityService userSecurityService;

    private User user;

    @BeforeEach
    void setUp() {
        AuthTestUtils.registerAndActivate("refresher", "refresher@margin.chat", PASSWORD, null);
        user = AuthTestUtils.findByEmail("refresher@margin.chat");
    }

    private String loginAndGetRefreshToken() {
        AuthResponse response = AuthTestUtils.login("refresher@margin.chat", PASSWORD);
        assertTrue(response.success());
        return response.refreshToken();
    }

    @Test
    @DisplayName("login issues both an access token and a refresh token")
    void login_IssuesBothTokens() {
        AuthResponse response = AuthTestUtils.login("refresher@margin.chat", PASSWORD);

        assertNotNull(response.token());
        assertNotNull(response.refreshToken());
        assertNotEquals(response.token(), response.refreshToken());
    }

    @Test
    @DisplayName("the raw refresh token is never stored — only its hash")
    void refreshToken_IsStoredHashed() {
        String refreshToken = loginAndGetRefreshToken();

        List<RefreshToken> stored = refreshTokenRepository.findAllByUserId(user.getId());
        assertEquals(1, stored.size());
        assertNotEquals(refreshToken, stored.getFirst().getTokenHash());
        assertEquals(64, stored.getFirst().getTokenHash().length(), "expected a SHA-256 hex digest");
    }

    @Test
    @DisplayName("refresh returns a new access token and a new refresh token")
    void refresh_ReturnsANewPair() {
        String refreshToken = loginAndGetRefreshToken();

        AuthResponse refreshed = authenticationService.refresh(refreshToken);

        assertTrue(refreshed.success());
        assertNotNull(refreshed.token());
        assertNotEquals(refreshToken, refreshed.refreshToken());
    }

    @Test
    @DisplayName("the rotated token stops working once it has been exchanged")
    void refresh_InvalidatesTheOldToken() {
        String first = loginAndGetRefreshToken();
        String second = authenticationService.refresh(first).refreshToken();

        assertThrows(InvalidRefreshTokenException.class, () -> authenticationService.refresh(first));
        assertNotNull(second);
    }

    @Test
    @DisplayName("replaying a rotated token revokes every session for that user")
    void replay_RevokesTheWholeFamily() {
        String first = loginAndGetRefreshToken();
        String second = authenticationService.refresh(first).refreshToken();

        assertThrows(InvalidRefreshTokenException.class, () -> authenticationService.refresh(first));
        assertThrows(InvalidRefreshTokenException.class, () -> authenticationService.refresh(second));
        assertTrue(refreshTokenRepository.findAllByUserId(user.getId()).stream()
                .allMatch(RefreshToken::isRevoked));
    }

    @Test
    @DisplayName("an unknown refresh token is rejected")
    void unknownToken_IsRejected() {
        assertThrows(InvalidRefreshTokenException.class,
                () -> authenticationService.refresh("not-a-real-token"));
    }

    @Test
    @DisplayName("an expired refresh token is rejected")
    void expiredToken_IsRejected() {
        String refreshToken = loginAndGetRefreshToken();
        RefreshToken stored = refreshTokenRepository.findAllByUserId(user.getId()).getFirst();
        stored.setExpiresAt(Instant.now().minus(Duration.ofMinutes(1)));
        refreshTokenRepository.save(stored);

        assertThrows(InvalidRefreshTokenException.class,
                () -> authenticationService.refresh(refreshToken));
    }

    @Test
    @DisplayName("logging out one device leaves the other device signed in")
    void logout_IsScopedToOneDevice() {
        String phone = loginAndGetRefreshToken();
        String desktop = loginAndGetRefreshToken();

        authenticationService.logoutUser(user.getId(), phone);

        assertThrows(InvalidRefreshTokenException.class, () -> authenticationService.refresh(phone));
        assertDoesNotThrow(() -> authenticationService.refresh(desktop));
    }

    @Test
    @DisplayName("presenting a logged-out token does not sign out the other devices")
    void loggedOutToken_IsNotTreatedAsAReplay() {
        String phone = loginAndGetRefreshToken();
        String desktop = loginAndGetRefreshToken();
        authenticationService.logoutUser(user.getId(), phone);

        // The phone retries with the token it just gave up. That is a stale client, not a thief:
        // escalating it to a full revocation would knock the desktop out for nothing.
        assertThrows(InvalidRefreshTokenException.class, () -> authenticationService.refresh(phone));
        assertThrows(InvalidRefreshTokenException.class, () -> authenticationService.refresh(phone));

        assertDoesNotThrow(() -> authenticationService.refresh(desktop));
    }

    @Test
    @DisplayName("logging out without a refresh token still signs out everything")
    void logout_WithoutATokenSignsOutAllDevices() {
        String phone = loginAndGetRefreshToken();
        String desktop = loginAndGetRefreshToken();
        int versionBefore = userSecurityService.get(user.getId()).getTokenVersion();

        authenticationService.logoutUser(user.getId(), null);

        assertThrows(InvalidRefreshTokenException.class, () -> authenticationService.refresh(phone));
        assertThrows(InvalidRefreshTokenException.class, () -> authenticationService.refresh(desktop));
        assertTrue(userSecurityService.get(user.getId()).getTokenVersion() > versionBefore,
                "outstanding access tokens must be invalidated too");
    }

    @Test
    @DisplayName("signing out everywhere invalidates outstanding access tokens as well")
    void logoutAllDevices_BumpsTheTokenVersion() {
        String refreshToken = loginAndGetRefreshToken();
        int versionBefore = userSecurityService.get(user.getId()).getTokenVersion();

        authenticationService.logoutAllDevices(user.getId());

        assertThrows(InvalidRefreshTokenException.class,
                () -> authenticationService.refresh(refreshToken));
        assertTrue(userSecurityService.get(user.getId()).getTokenVersion() > versionBefore);
    }

    @Test
    @DisplayName("expired rows are pruned and live ones are left alone")
    void deleteExpired_PrunesOnlyExpiredRows() {
        loginAndGetRefreshToken();
        RefreshToken stale = refreshTokenRepository.save(
                new RefreshToken(user.getId(), "a".repeat(64), Instant.now().minus(Duration.ofDays(1))));

        int deleted = refreshTokenService.deleteExpired();

        assertEquals(1, deleted);
        assertFalse(refreshTokenRepository.findById(stale.getId()).isPresent());
        assertEquals(1, refreshTokenRepository.findAllByUserId(user.getId()).size());
    }
}
