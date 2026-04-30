package org.margin.server.integrationtest;

import org.junit.jupiter.api.Test;
import org.margin.server.authentication.entities.ActivationKey;
import org.margin.server.authentication.entities.BetaKey;
import org.margin.server.integrationtest.config.MarginTestRunner;
import org.margin.server.integrationtest.utils.ActivationTestUtils;
import org.margin.server.integrationtest.utils.AuthTestUtils;
import org.margin.server.users.models.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

@Transactional(propagation = Propagation.NOT_SUPPORTED)
class EmailActivationTest extends MarginTestRunner {

    @Test
    void activate_validToken_returnsOkAndMarksActivated() {
        User user = registerAndFetch("alice", "alice@margin.chat");
        ActivationKey key = ActivationTestUtils.generateActivationKey(user);

        ResponseEntity<Void> response = ActivationTestUtils.activate(key.getToken());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ActivationKey reloaded = ActivationTestUtils.findByToken(key.getToken());
        assertNotNull(reloaded.getActivatedAt());
        assertTrue(ActivationTestUtils.isUserActivated(user));
    }

    @Test
    void activate_alreadyActivatedToken_throwsUnauthorized() {
        User user = registerAndFetch("bob", "bob@margin.chat");
        ActivationKey key = ActivationTestUtils.generateActivationKey(user);
        ActivationTestUtils.activate(key.getToken());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                ActivationTestUtils.activate(key.getToken()));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        assertTrue(ex.getReason().toLowerCase().contains("already"));
    }

    @Test
    void activate_expiredToken_throwsUnauthorized() {
        User user = registerAndFetch("carol", "carol@margin.chat");
        ActivationKey key = ActivationTestUtils.generateExpiredActivationKey(user);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                ActivationTestUtils.activate(key.getToken()));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        assertTrue(ex.getReason().toLowerCase().contains("expired"));
        ActivationKey reloaded = ActivationTestUtils.findByToken(key.getToken());
        assertNull(reloaded.getActivatedAt());
    }

    @Test
    void activate_unknownToken_throws() {
        assertThrows(NoSuchElementException.class, () ->
                ActivationTestUtils.activate("does-not-exist"));
    }

    @Test
    void newlyRegisteredUser_isNotActivatedUntilTokenConsumed() {
        User user = registerAndFetch("dave", "dave@margin.chat");
        ActivationTestUtils.generateActivationKey(user);

        assertFalse(ActivationTestUtils.isUserActivated(user));
    }

    private static User registerAndFetch(String handle, String email) {
        BetaKey key = AuthTestUtils.createBetaKey();
        AuthTestUtils.register(handle, email, "password", key.getKey());
        return AuthTestUtils.findByEmail(email);
    }
}
