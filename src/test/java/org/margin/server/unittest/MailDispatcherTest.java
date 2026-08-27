package org.margin.server.unittest;

import jakarta.mail.MessagingException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.margin.server.email.EmailService;
import org.margin.server.email.MailDispatcher;
import org.margin.server.email.events.SendMailEvent;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.Executor;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MailDispatcherTest {

    @Mock
    private EmailService emailService;

    private final Executor direct = Runnable::run;

    private MailDispatcher dispatcher() {
        return new MailDispatcher(emailService, direct);
    }

    @Test
    @DisplayName("a dispatched mail reaches the mail sender")
    void dispatch_sends() throws Exception {
        dispatcher().dispatch(new SendMailEvent("a@margin.chat", "Hi", "<p>Hi</p>"));

        verify(emailService).sendEmail("a@margin.chat", "Hi", "<p>Hi</p>");
    }

    @Test
    @DisplayName("one bad recipient does not stop the rest of the batch")
    void dispatchAll_isolatesFailures() throws Exception {
        doThrow(new MessagingException("smtp is down"))
                .when(emailService).sendEmail(eqTo("b@margin.chat"), anyString(), anyString());

        dispatcher().dispatchAll(List.of(
                new SendMailEvent("a@margin.chat", "Hi", "body"),
                new SendMailEvent("b@margin.chat", "Hi", "body"),
                new SendMailEvent("c@margin.chat", "Hi", "body")));

        verify(emailService, times(3)).sendEmail(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("a guest address is refused before it reaches the mail sender")
    void dispatch_refusesGuestAddresses() throws Exception {
        EmailService real = new EmailService(null, null);
        org.springframework.test.util.ReflectionTestUtils.setField(real, "logOnly", false);

        real.sendEmail("guest_x@guests.margin.invalid", "Hi", "body");
    }

    @Test
    @DisplayName("a send failure never surfaces to the caller")
    void dispatch_swallowsFailure() throws Exception {
        doThrow(new MessagingException("smtp is down"))
                .when(emailService).sendEmail(anyString(), anyString(), anyString());

        dispatcher().dispatch(new SendMailEvent("a@margin.chat", "Hi", "body"));
    }

    private static String eqTo(String value) {
        return org.mockito.ArgumentMatchers.eq(value);
    }
}
