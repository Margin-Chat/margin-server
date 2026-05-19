package org.margin.server.integrationtest;

import org.junit.jupiter.api.Test;
import org.margin.server.authentication.entities.BetaKey;
import org.margin.server.authentication.models.AuthResponse;
import org.margin.server.integrationtest.config.MarginTestRunner;
import org.margin.server.integrationtest.utils.AuthTestUtils;
import org.margin.server.users.models.User;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AuthenticationTest extends MarginTestRunner {

    @Test
    void loginWithValidCredentials_returnsTokenAndSuccess() {
        BetaKey key = AuthTestUtils.createBetaKey();
        AuthTestUtils.registerAndActivate("alice", "alice@margin.chat", "correct-password", key.getKey());

        AuthResponse response = AuthTestUtils.login("alice@margin.chat", "correct-password");

        assertTrue(response.success());
        assertNotNull(response.token());
        assertEquals("Login successful", response.message());
    }

    @Test
    void loginWithWrongPassword_returnsFalse() {
        BetaKey key = AuthTestUtils.createBetaKey();
        AuthTestUtils.registerAndActivate("bob", "bob@margin.chat", "correct-password", key.getKey());

        AuthResponse response = AuthTestUtils.login("bob@margin.chat", "wrong-password");

        assertFalse(response.success());
        assertNull(response.token());
    }

    @Test
    void loginWithUnknownEmail_returnsFalse() {
        AuthResponse response = AuthTestUtils.login("nobody@margin.chat", "password");

        assertFalse(response.success());
        assertNull(response.token());
    }

    @Test
    void loginIsCaseInsensitiveForEmail() {
        BetaKey key = AuthTestUtils.createBetaKey();
        AuthTestUtils.registerAndActivate("carol", "carol@margin.chat", "password123", key.getKey());

        AuthResponse response = AuthTestUtils.login("CAROL@MARGIN.CHAT", "password123");

        assertTrue(response.success());
        assertNotNull(response.token());
    }

    @Test
    void failedLoginIncrementsAttemptCounter() {
        BetaKey key = AuthTestUtils.createBetaKey();
        AuthTestUtils.registerAndActivate("dave", "dave@margin.chat", "correct", key.getKey());

        AuthTestUtils.login("dave@margin.chat", "wrong");

        User user = AuthTestUtils.findByEmail("dave@margin.chat");
        assertEquals(1, user.getSecurity().getFailedLoginAttempts());
    }

    @Test
    void accountLocksAfterThreeFailedAttempts() {
        BetaKey key = AuthTestUtils.createBetaKey();
        AuthTestUtils.registerAndActivate("eve", "eve@margin.chat", "correct", key.getKey());

        AuthTestUtils.login("eve@margin.chat", "wrong");
        AuthTestUtils.login("eve@margin.chat", "wrong");
        AuthTestUtils.login("eve@margin.chat", "wrong");

        User user = AuthTestUtils.findByEmail("eve@margin.chat");
        assertNotNull(user.getSecurity().getAccountLockedUntil());
        assertTrue(user.getSecurity().getAccountLockedUntil().isAfter(java.time.Instant.now()));
    }

    @Test
    void lockedAccountRejectsLoginEvenWithCorrectPassword() {
        BetaKey key = AuthTestUtils.createBetaKey();
        AuthTestUtils.registerAndActivate("frank", "frank@margin.chat", "correct", key.getKey());

        AuthTestUtils.login("frank@margin.chat", "wrong");
        AuthTestUtils.login("frank@margin.chat", "wrong");
        AuthTestUtils.login("frank@margin.chat", "wrong");

        AuthResponse response = AuthTestUtils.login("frank@margin.chat", "correct");

        assertFalse(response.success());
        assertNull(response.token());
    }

    @Test
    void successfulLoginResetsFailedAttemptCounter() {
        BetaKey key = AuthTestUtils.createBetaKey();
        AuthTestUtils.registerAndActivate("grace", "grace@margin.chat", "correct", key.getKey());

        AuthTestUtils.login("grace@margin.chat", "wrong");
        AuthTestUtils.login("grace@margin.chat", "wrong");
        AuthTestUtils.login("grace@margin.chat", "correct");

        User user = AuthTestUtils.findByEmail("grace@margin.chat");
        assertEquals(0, user.getSecurity().getFailedLoginAttempts());
        assertNull(user.getSecurity().getAccountLockedUntil());
    }

    @Test
    void registerWithValidBetaKey_persistsUser() {
        BetaKey key = AuthTestUtils.createBetaKey();

        AuthTestUtils.register("henry", "henry@margin.chat", "password", key.getKey());

        User user = AuthTestUtils.findByEmail("henry@margin.chat");
        assertNotNull(user);
        assertEquals("henry", user.getHandle());
    }

    @Test
    void registerWithValidBetaKey_loginRequiresActivation() {
        BetaKey key = AuthTestUtils.createBetaKey();
        AuthTestUtils.register("henrietta", "henrietta@margin.chat", "password", key.getKey());

        AuthResponse response = AuthTestUtils.login("henrietta@margin.chat", "password");

        assertFalse(response.success());
        assertNull(response.token());
    }

    @Test
    void registerMarksKeyAsUsed() {
        BetaKey key = AuthTestUtils.createBetaKey();
        AuthTestUtils.register("ivan", "ivan@margin.chat", "password", key.getKey());

        User user = AuthTestUtils.findByEmail("ivan@margin.chat");
        assertNotNull(user);
        assertEquals("ivan", user.getHandle());
    }

    @Test
    void registerWithDuplicateEmail_throws() {
        BetaKey key1 = AuthTestUtils.createBetaKey();
        BetaKey key2 = AuthTestUtils.createBetaKey();
        AuthTestUtils.register("lara", "lara@margin.chat", "password", key1.getKey());

        assertThrows(IllegalArgumentException.class, () ->
                AuthTestUtils.register("lara2", "lara@margin.chat", "password", key2.getKey()));
    }

    @Test
    void registerWithDuplicateHandle_throws() {
        BetaKey key1 = AuthTestUtils.createBetaKey();
        BetaKey key2 = AuthTestUtils.createBetaKey();
        AuthTestUtils.register("mike", "mike@margin.chat", "password", key1.getKey());

        assertThrows(IllegalArgumentException.class, () ->
                AuthTestUtils.register("mike", "mike2@margin.chat", "password", key2.getKey()));
    }

    @Test
    void registerHandleIsStoredLowercase() {
        BetaKey key = AuthTestUtils.createBetaKey();
        AuthTestUtils.register("NinaUpper", "nina@margin.chat", "password", key.getKey());

        User user = AuthTestUtils.findByEmail("nina@margin.chat");
        assertEquals("ninaupper", user.getHandle());
    }

    @Test
    void registerPasswordIsNotStoredInPlaintext() {
        BetaKey key = AuthTestUtils.createBetaKey();
        AuthTestUtils.register("oscar", "oscar@margin.chat", "my-secret", key.getKey());

        User user = AuthTestUtils.findByEmail("oscar@margin.chat");
        assertNotEquals("my-secret", user.getPassword());
        assertTrue(user.getPassword().startsWith("$2a$") || user.getPassword().startsWith("$2b$"));
    }
}
