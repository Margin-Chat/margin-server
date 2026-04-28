package org.margin.server.email.services;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailService {
    private final JavaMailSender mailSender;

    @Value("${margin.app.base-url:http://localhost:8080}")
    private String baseUrl;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendEmail(String to, String subject, String htmlBody) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, "utf-8");
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(htmlBody, true);
        mailSender.send(message);
    }

    public String buildRegistrationMail(String displayName, String activationToken) {
        String activationUrl = String.format("%s/api/auth/activate/%s", baseUrl, activationToken);

        return """
                <div style="font-family:sans-serif;max-width:480px;margin:0 auto;padding:32px">
                  <h1 style="font-size:24px;margin-bottom:8px">Welcome to margin, %s!</h1>
                  <p style="color:#555">Please confirm your email address to activate your account.</p>
                  <a href="%s" style="display:inline-block;margin-top:16px;padding:12px 24px;background:#000;color:#fff;text-decoration:none;border-radius:6px">Activate account</a>
                  <hr style="border:none;border-top:1px solid #eee;margin:24px 0"/>
                  <p style="font-size:12px;color:#aaa">If you didn't create this account, you can ignore this email.</p>
                </div>
                """.formatted(displayName, activationUrl);
    }
}