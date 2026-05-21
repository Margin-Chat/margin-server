package org.margin.server.integrationtest;

import org.junit.jupiter.api.Test;
import org.margin.server.authentication.entities.ActivationKey;
import org.margin.server.authentication.entities.BetaKey;
import org.margin.server.authentication.entities.PasswordResetToken;
import org.margin.server.authentication.models.AuthResponse;
import org.margin.server.integrationtest.config.MarginTestRunner;
import org.margin.server.integrationtest.utils.ActivationTestUtils;
import org.margin.server.integrationtest.utils.AuthTestUtils;
import org.margin.server.integrationtest.utils.PasswordResetTestUtils;
import org.margin.server.users.models.User;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PasswordResetTest extends MarginTestRunner {

    @Test
    void requestReset_validEmail_persistsToken() {
        User user = registerAndActivate("alice", "alice@margin.chat");

        PasswordResetTestUtils.requestReset("alice@margin.chat");

        PasswordResetToken token = PasswordResetTestUtils.getTokenForUser(user);
        assertNotNull(token.getToken());
        assertNull(token.getUsedAt());
        assertTrue(token.getExpiresAt().isAfter(java.time.Instant.now()));
    }

    @Test
    void requestReset_unknownEmail_doesNotThrow() {
        assertDoesNotThrow(() ->
                PasswordResetTestUtils.requestReset("nobody@margin.chat"));
    }

    @Test
    void requestReset_calledTwice_replacesOldToken() {
        User user = registerAndActivate("bob", "bob@margin.chat");

        PasswordResetTestUtils.requestReset("bob@margin.chat");
        String firstToken = PasswordResetTestUtils.getTokenForUser(user).getToken();

        PasswordResetTestUtils.requestReset("bob@margin.chat");
        String secondToken = PasswordResetTestUtils.getTokenForUser(user).getToken();

        assertNotEquals(firstToken, secondToken);
    }

    @Test
    void resetPassword_validToken_updatesPassword() {
        User user = registerAndActivate("carol", "carol@margin.chat");
        PasswordResetTestUtils.requestReset("carol@margin.chat");
        PasswordResetToken token = PasswordResetTestUtils.getTokenForUser(user);

        PasswordResetTestUtils.resetPassword(token.getToken(), "new-password-123");

        AuthResponse response = AuthTestUtils.login("carol@margin.chat", "new-password-123");
        assertTrue(response.success());
        assertNotNull(response.token());
    }

    @Test
    void resetPassword_validToken_oldPasswordNoLongerWorks() {
        User user = registerAndActivate("dave", "dave@margin.chat");
        PasswordResetTestUtils.requestReset("dave@margin.chat");
        PasswordResetToken token = PasswordResetTestUtils.getTokenForUser(user);

        PasswordResetTestUtils.resetPassword(token.getToken(), "new-password-456");

        AuthResponse response = AuthTestUtils.login("dave@margin.chat", "password");
        assertFalse(response.success());
    }

    @Test
    void resetPassword_validToken_marksTokenAsUsed() {
        User user = registerAndActivate("eve", "eve@margin.chat");
        PasswordResetTestUtils.requestReset("eve@margin.chat");
        PasswordResetToken token = PasswordResetTestUtils.getTokenForUser(user);

        PasswordResetTestUtils.resetPassword(token.getToken(), "new-password-789");

        PasswordResetToken reloaded = PasswordResetTestUtils.getTokenForUser(user);
        assertNotNull(reloaded.getUsedAt());
    }

    @Test
    void resetPassword_expiredToken_throwsBadRequest() {
        User user = registerAndActivate("frank", "frank@margin.chat");
        PasswordResetToken expired = PasswordResetTestUtils.saveExpiredToken(user);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                PasswordResetTestUtils.resetPassword(expired.getToken(), "new-password"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().toLowerCase().contains("expired"));
    }

    @Test
    void resetPassword_usedToken_throwsBadRequest() {
        User user = registerAndActivate("grace", "grace@margin.chat");
        PasswordResetToken used = PasswordResetTestUtils.saveUsedToken(user);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                PasswordResetTestUtils.resetPassword(used.getToken(), "new-password"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().toLowerCase().contains("already been used"));
    }

    @Test
    void resetPassword_invalidToken_throwsBadRequest() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                PasswordResetTestUtils.resetPassword("does-not-exist", "new-password"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    private static User registerAndActivate(String handle, String email) {
        BetaKey key = AuthTestUtils.createBetaKey();
        ActivationKey activationKey = AuthTestUtils.register(handle, email, "password", key.getKey());
        ActivationTestUtils.activate(activationKey.getToken());
        return activationKey.getUser();
    }
}
