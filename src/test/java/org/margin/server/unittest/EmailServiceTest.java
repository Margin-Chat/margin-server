package org.margin.server.unittest;

import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.margin.server.email.EmailService;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.time.Instant;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    private EmailService emailService;

    @BeforeEach
    void setUp() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");

        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);

        emailService = new EmailService(mailSender, engine);
        ReflectionTestUtils.setField(emailService, "baseUrl", "https://margin.test");
        ReflectionTestUtils.setField(emailService, "fromAddress", "noreply@margin.test");
        ReflectionTestUtils.setField(emailService, "logoUrl", "");
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
    void buildInvoiceMail_includesOwnerNameAndWorkspace() {
        String html = emailService.buildInvoiceMail(
                "Alice", "ACME Corp", "SMALL", "9.99", "EUR",
                "tr_abc123", Instant.parse("2026-06-13T00:00:00Z"));

        assertTrue(html.contains("Alice"));
        assertTrue(html.contains("ACME Corp"));
    }

    @Test
    void buildInvoiceMail_includesTierAndAmount() {
        String html = emailService.buildInvoiceMail(
                "Alice", "ACME Corp", "SMALL", "9.99", "EUR",
                "tr_abc123", Instant.parse("2026-06-13T00:00:00Z"));

        assertTrue(html.contains("SMALL"));
        assertTrue(html.contains("9.99"));
        assertTrue(html.contains("EUR"));
    }

    @Test
    void buildInvoiceMail_includesPaymentReference() {
        String html = emailService.buildInvoiceMail(
                "Alice", "ACME Corp", "SMALL", "9.99", "EUR",
                "tr_abc123", Instant.parse("2026-06-13T00:00:00Z"));

        assertTrue(html.contains("tr_abc123"));
    }

    @Test
    void buildInvoiceMail_includesFormattedNextBillingDate() {
        String html = emailService.buildInvoiceMail(
                "Alice", "ACME Corp", "SMALL", "9.99", "EUR",
                "tr_abc123", Instant.parse("2026-06-13T00:00:00Z"));

        assertTrue(html.contains("13 Jun 2026"));
    }

    @Test
    void buildInvoiceMail_nullNextBillingDate_showsDash() {
        String html = emailService.buildInvoiceMail(
                "Alice", "ACME Corp", "SMALL", "9.99", "EUR",
                "tr_abc123", null);

        assertTrue(html.contains("—"));
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
