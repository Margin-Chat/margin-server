package org.margin.server.unittest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.margin.server.authentication.entities.ActivationKey;
import org.margin.server.authentication.repositories.ActivationKeyRepository;
import org.margin.server.authentication.services.ActivationKeyService;
import org.margin.server.users.models.User;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivationKeyServiceTest {

    @Mock
    private ActivationKeyRepository activationKeyRepository;

    @InjectMocks
    private ActivationKeyService activationKeyService;

    @Test
    void generateActivationKey_createsTokenAndPersists() {
        User user = makeUser();
        when(activationKeyRepository.save(any(ActivationKey.class))).thenAnswer(i -> i.getArgument(0));

        ActivationKey key = activationKeyService.generateActivationKey(user);

        assertNotNull(key.getToken());
        assertEquals(user, key.getUser());
        assertNull(key.getActivatedAt());
        assertTrue(key.getExpiresAt().isAfter(Instant.now()));
        verify(activationKeyRepository).save(key);
    }

    @Test
    void generateActivationKey_producesUniqueTokens() {
        User user = makeUser();
        when(activationKeyRepository.save(any(ActivationKey.class))).thenAnswer(i -> i.getArgument(0));

        String first = activationKeyService.generateActivationKey(user).getToken();
        String second = activationKeyService.generateActivationKey(user).getToken();

        assertNotEquals(first, second);
    }

    @Test
    void findAndConsumeActivationKey_validToken_setsActivatedAt() {
        ActivationKey key = makeKey(false, false);
        when(activationKeyRepository.findActivationKeyByToken("token")).thenReturn(Optional.of(key));

        activationKeyService.findAndConsumeActivationKey("token");

        assertNotNull(key.getActivatedAt());
        verify(activationKeyRepository).save(key);
    }

    @Test
    void findAndConsumeActivationKey_expiredToken_throwsUnauthorized() {
        ActivationKey key = makeKey(true, false);
        when(activationKeyRepository.findActivationKeyByToken("expired")).thenReturn(Optional.of(key));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                activationKeyService.findAndConsumeActivationKey("expired"));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        assertTrue(ex.getReason().toLowerCase().contains("expired"));
        assertNull(key.getActivatedAt());
    }

    @Test
    void findAndConsumeActivationKey_alreadyActivated_throwsUnauthorized() {
        ActivationKey key = makeKey(false, true);
        Instant originalActivatedAt = key.getActivatedAt();
        when(activationKeyRepository.findActivationKeyByToken("used")).thenReturn(Optional.of(key));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                activationKeyService.findAndConsumeActivationKey("used"));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        assertTrue(ex.getReason().toLowerCase().contains("already"));
        assertEquals(originalActivatedAt, key.getActivatedAt());
    }

    @Test
    void findAndConsumeActivationKey_unknownToken_throws() {
        when(activationKeyRepository.findActivationKeyByToken("nope")).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () ->
                activationKeyService.findAndConsumeActivationKey("nope"));
    }

    @Test
    void isUserActivated_returnsTrueWhenActivated() {
        User user = makeUser();
        ActivationKey key = makeKey(false, true);
        when(activationKeyRepository.findActivationKeyByUser(user)).thenReturn(Optional.of(key));

        assertTrue(activationKeyService.isUserActivated(user));
    }

    @Test
    void isUserActivated_returnsFalseWhenNotActivated() {
        User user = makeUser();
        ActivationKey key = makeKey(false, false);
        when(activationKeyRepository.findActivationKeyByUser(user)).thenReturn(Optional.of(key));

        assertFalse(activationKeyService.isUserActivated(user));
    }

    private static User makeUser() {
        User user = new User();
        user.setId(42L);
        user.setHandle("alice");
        return user;
    }

    private static ActivationKey makeKey(boolean expired, boolean activated) {
        ActivationKey key = new ActivationKey();
        key.setToken("t");
        key.setUser(makeUser());
        key.setExpiresAt(expired ? Instant.now().minusSeconds(60) : Instant.now().plusSeconds(3600));
        if (activated) key.setActivatedAt(Instant.now().minusSeconds(30));
        return key;
    }
}
