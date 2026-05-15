package org.margin.server.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
public class EmailService {
    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${margin.app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Value("${margin.mail.from:noreply@localhost}")
    private String fromAddress;

    @Value("${margin.mail.logo-url:}")
    private String logoUrl;

    public EmailService(JavaMailSender mailSender, TemplateEngine templateEngine) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
    }

    public void sendEmail(String to, String subject, String htmlBody) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, "utf-8");
        helper.setFrom(fromAddress);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(htmlBody, true);
        mailSender.send(message);
    }

    public String buildInvoiceMail(String ownerName, String marginName, String tier,
                                   String amount, String currency,
                                   String paymentId, Instant nextBillingDate) {
        String formattedNext = nextBillingDate != null
                ? DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH).withZone(ZoneOffset.UTC).format(nextBillingDate)
                : "—";

        Context ctx = new Context(Locale.ENGLISH);
        ctx.setVariable("logoUrl", logoUrl.isBlank() ? null : logoUrl);
        ctx.setVariable("ownerName", ownerName);
        ctx.setVariable("marginName", marginName);
        ctx.setVariable("tier", tier);
        ctx.setVariable("amount", amount);
        ctx.setVariable("currency", currency);
        ctx.setVariable("paymentId", paymentId);
        ctx.setVariable("nextBillingDate", formattedNext);

        return templateEngine.process("email/invoice", ctx);
    }

    public String buildRegistrationMail(String displayName, String activationToken) {
        Context ctx = new Context(Locale.ENGLISH);
        ctx.setVariable("logoUrl", logoUrl.isBlank() ? null : logoUrl);
        ctx.setVariable("displayName", displayName);
        ctx.setVariable("activationUrl", baseUrl + "/email-activation/" + activationToken);

        return templateEngine.process("email/registration", ctx);
    }
}
