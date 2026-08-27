package org.margin.server.email;

import lombok.extern.slf4j.Slf4j;
import org.margin.server.email.events.SendMailEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Collection;
import java.util.concurrent.Executor;

@Slf4j
@Component
public class MailDispatcher {

    private final EmailService emailService;
    private final Executor executor;

    public MailDispatcher(EmailService emailService, Executor mailExecutor) {
        this.emailService = emailService;
        this.executor = mailExecutor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onSendMail(SendMailEvent event) {
        dispatch(event);
    }

    public void dispatch(SendMailEvent event) {
        executor.execute(() -> send(event));
    }

    public void dispatchAll(Collection<SendMailEvent> events) {
        events.forEach(this::dispatch);
    }

    private void send(SendMailEvent event) {
        try {
            emailService.sendEmail(event.to(), event.subject(), event.htmlBody());
        } catch (Exception e) {
            log.warn("Could not send \"{}\" to {}: {}", event.subject(), event.to(), e.getMessage());
        }
    }
}
