package org.margin.server.unittest;

import jakarta.mail.MessagingException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.margin.server.authentication.entities.PasswordResetToken;
import org.margin.server.authentication.repositories.PasswordResetTokenRepository;
import org.margin.server.authentication.services.PasswordResetService;
import org.margin.server.email.EmailService;
import org.margin.server.users.models.User;
import org.margin.server.users.repositories.UserRepository;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.margin.server.unittest.utils.UserTestUtils.createUser;
import static org.margin.server.unittest.utils.UserTestUtils.createEncryption;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordResetTokenRepository tokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private EmailService emailService;

    @InjectMocks
    private PasswordResetService passwordResetService;

    @Test
    void requestReset_knownEmail_deletesOldTokenAndSavesNew() throws MessagingException {
        User user = makeUser();
        when(userRepository.findByEmail("alice@margin.chat")).thenReturn(Optional.of(user));
        when(emailService.buildPasswordResetMail(any(), any())).thenReturn("<html/>");
        when(tokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        passwordResetService.requestPasswordReset("alice@margin.chat");

        verify(tokenRepository).deleteByUser(user);
        ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(captor.capture());
        PasswordResetToken saved = captor.getValue();
        assertNotNull(saved.getToken());
        assertEquals(user, saved.getUser());
        assertTrue(saved.getExpiresAt().isAfter(Instant.now()));
    }

    @Test
    void requestReset_knownEmail_sendsEmail() throws MessagingException {
        User user = makeUser();
        when(userRepository.findByEmail("alice@margin.chat")).thenReturn(Optional.of(user));
        when(emailService.buildPasswordResetMail(eq("Alice"), any())).thenReturn("<html/>");
        when(tokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        passwordResetService.requestPasswordReset("alice@margin.chat");

        verify(emailService).sendEmail(eq("alice@margin.chat"), eq("Reset your margin password"), eq("<html/>"));
    }

    @Test
    void requestReset_unknownEmail_doesNothing() {
        when(userRepository.findByEmail("nobody@margin.chat")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> passwordResetService.requestPasswordReset("nobody@margin.chat"));

        verifyNoInteractions(tokenRepository, emailService);
    }

    @Test
    void requestReset_emailFailure_doesNotThrow() throws MessagingException {
        User user = makeUser();
        when(userRepository.findByEmail("alice@margin.chat")).thenReturn(Optional.of(user));
        when(emailService.buildPasswordResetMail(any(), any())).thenReturn("<html/>");
        when(tokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        doThrow(new MessagingException("SMTP down")).when(emailService).sendEmail(any(), any(), any());

        assertDoesNotThrow(() -> passwordResetService.requestPasswordReset("alice@margin.chat"));
    }

    @Test
    void resetPassword_validToken_encodesPersistsPasswordAndClearsEncryptionKeys() {
        User user = makeUser();
        PasswordResetToken token = makeToken(user, false, false);
        when(tokenRepository.findByToken("valid-token")).thenReturn(Optional.of(token));
        when(passwordEncoder.encode("new-pass")).thenReturn("hashed");

        passwordResetService.resetPassword("valid-token", "new-pass");

        assertEquals("hashed", user.getPassword());
        assertNull(user.getEncryption().getPublicKey());
        assertNull(user.getEncryption().getEncryptedPrivateKey());
        assertNull(user.getEncryption().getSalt());
        assertNull(user.getEncryption().getIv());
        verify(userRepository).save(user);
        assertNotNull(token.getUsedAt());
        verify(tokenRepository).save(token);
    }

    @Test
    void resetPassword_expiredToken_throwsBadRequest() {
        PasswordResetToken token = makeToken(makeUser(), true, false);
        when(tokenRepository.findByToken("expired")).thenReturn(Optional.of(token));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                passwordResetService.resetPassword("expired", "new-pass"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().toLowerCase().contains("expired"));
        verifyNoInteractions(userRepository);
    }

    @Test
    void resetPassword_usedToken_throwsBadRequest() {
        PasswordResetToken token = makeToken(makeUser(), false, true);
        when(tokenRepository.findByToken("used")).thenReturn(Optional.of(token));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                passwordResetService.resetPassword("used", "new-pass"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().toLowerCase().contains("already been used"));
        verifyNoInteractions(userRepository);
    }

    @Test
    void resetPassword_unknownToken_throwsBadRequest() {
        when(tokenRepository.findByToken("nope")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                passwordResetService.resetPassword("nope", "new-pass"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    private static User makeUser() {
        User user = createUser(1L, null, "Alice", "alice@margin.chat");
        user.setEncryption(createEncryption("old-public", "old-encrypted-private", "old-salt", "old-iv"));
        return user;
    }

    private static PasswordResetToken makeToken(User user, boolean expired, boolean used) {
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setToken("valid-token");
        token.setExpiresAt(expired ? Instant.now().minusSeconds(60) : Instant.now().plusSeconds(3600));
        if (used) token.setUsedAt(Instant.now().minusSeconds(30));
        return token;
    }
}
