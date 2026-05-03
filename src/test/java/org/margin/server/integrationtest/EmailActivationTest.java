package org.margin.server.integrationtest;

import org.junit.jupiter.api.Test;
import org.margin.server.authentication.entities.ActivationKey;
import org.margin.server.authentication.entities.BetaKey;
import org.margin.server.authentication.repositories.ActivationKeyRepository;
import org.margin.server.integrationtest.config.MarginTestRunner;
import org.margin.server.integrationtest.utils.ActivationTestUtils;
import org.margin.server.integrationtest.utils.AuthTestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

@Transactional(propagation = Propagation.NOT_SUPPORTED)
class EmailActivationTest extends MarginTestRunner {

    @Autowired
    private ActivationKeyRepository activationKeyRepository;

    @Test
    void activate_validToken_returnsOkAndMarksActivated() {
        ActivationKey activationKey = registerAndFetch("alice", "alice@margin.chat");

        ResponseEntity<Void> response = ActivationTestUtils.activate(activationKey.getToken());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ActivationKey reloaded = ActivationTestUtils.findByToken(activationKey.getToken());
        assertNotNull(reloaded.getActivatedAt());
        assertTrue(ActivationTestUtils.isUserActivated(activationKey.getUser()));
    }

    @Test
    void activate_alreadyActivatedToken_throwsUnauthorized() {
        ActivationKey activationKey = registerAndFetch("bob", "bob@margin.chat");
        ActivationTestUtils.activate(activationKey.getToken());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                ActivationTestUtils.activate(activationKey.getToken()));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        assertTrue(ex.getReason().toLowerCase().contains("already"));
    }

    @Test
    void activate_expiredToken_throwsUnauthorized() {
        ActivationKey activationKey = registerAndFetch("carol", "carol@margin.chat");
        activationKey.setExpiresAt(Instant.now().minusSeconds(60));
        activationKeyRepository.save(activationKey);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                ActivationTestUtils.activate(activationKey.getToken()));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        assertTrue(ex.getReason().toLowerCase().contains("expired"));
        ActivationKey reloaded = ActivationTestUtils.findByToken(activationKey.getToken());
        assertNull(reloaded.getActivatedAt());
    }

    @Test
    void activate_unknownToken_throws() {
        assertThrows(NoSuchElementException.class, () ->
                ActivationTestUtils.activate("does-not-exist"));
    }

    @Test
    void newlyRegisteredUser_isNotActivatedUntilTokenConsumed() {
        ActivationKey activationKey = registerAndFetch("dave", "dave@margin.chat");
        assertFalse(ActivationTestUtils.isUserActivated(activationKey.getUser()));
    }

    private static ActivationKey registerAndFetch(String handle, String email) {
        BetaKey key = AuthTestUtils.createBetaKey();
        return AuthTestUtils.register(handle, email, "password", key.getKey());
    }
}
