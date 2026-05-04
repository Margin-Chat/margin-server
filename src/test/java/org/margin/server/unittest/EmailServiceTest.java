package org.margin.server.unittest;

import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.margin.server.email.services.EmailService;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private EmailService emailService;

    @BeforeEach
    void injectBaseUrl() {
        ReflectionTestUtils.setField(emailService, "baseUrl", "https://margin.test");
        ReflectionTestUtils.setField(emailService, "fromAddress", "noreply@margin.test");
    }

    @Test
    void buildRegistrationMail_includesDisplayName() {
        String html = emailService.buildRegistrationMail("Alice", "abc-123");

        assertTrue(html.contains("Welcome to margin, Alice"));
    }

    @Test
    void buildRegistrationMail_pointsAtLandingActivationPath() {
        String html = emailService.buildRegistrationMail("Alice", "abc-123");

        assertTrue(html.contains("https://margin.test/email-activation/abc-123"),
                "Activation link should target landing's email-activation path");
    }

    @Test
    void buildRegistrationMail_escapesIntoTokenSegment() {
        String html = emailService.buildRegistrationMail("Bob", "xyz-789");

        assertFalse(html.contains("/api/auth/activate/"),
                "Should no longer point at the backend activate path directly");
    }

    @Test
    void sendEmail_invokesMailSenderWithConfiguredMessage() throws MessagingException {
        MimeMessage mime = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mime);

        emailService.sendEmail("user@example.com", "Email activation for margin", "<p>hi</p>");

        verify(mailSender).send(mime);
        assertEquals("Email activation for margin", mime.getSubject());
        assertEquals(1, mime.getAllRecipients().length);
        assertEquals("user@example.com", mime.getAllRecipients()[0].toString());
        assertEquals(1, mime.getFrom().length);
        assertEquals("noreply@margin.test", mime.getFrom()[0].toString());
    }
}
