package org.margin.server.authentication.listeners;

import lombok.extern.slf4j.Slf4j;
import org.margin.server.authentication.entities.ActivationKey;
import org.margin.server.authentication.entities.UserSecurity;
import org.margin.server.authentication.services.ActivationKeyService;
import org.margin.server.authentication.services.UserSecurityService;
import org.margin.server.email.EmailService;
import org.margin.server.email.events.SendMailEvent;
import org.margin.server.users.events.GuestPromotedEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
public class GuestPromotedListener {

    @Value("${margin.mail.require-email-activation:true}")
    private boolean requireEmailActivation;

    private final UserSecurityService userSecurityService;
    private final ActivationKeyService activationKeyService;
    private final EmailService emailService;
    private final ApplicationEventPublisher eventPublisher;

    public GuestPromotedListener(UserSecurityService userSecurityService,
                                 ActivationKeyService activationKeyService,
                                 EmailService emailService,
                                 ApplicationEventPublisher eventPublisher) {
        this.userSecurityService = userSecurityService;
        this.activationKeyService = activationKeyService;
        this.emailService = emailService;
        this.eventPublisher = eventPublisher;
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onGuestPromoted(GuestPromotedEvent event) {
        UserSecurity security = new UserSecurity(event.userId());
        security.setFailedLoginAttempts(0);
        userSecurityService.save(security);

        ActivationKey activationKey = activationKeyService.generateActivationKey(event.userId());

        if (!requireEmailActivation) {
            activationKeyService.findAndConsumeActivationKey(activationKey.getToken());
            log.info("Email activation disabled — claimed account {} auto-activated", event.userId());
            return;
        }

        eventPublisher.publishEvent(new SendMailEvent(
                event.email(),
                "Email activation for margin",
                emailService.buildRegistrationMail(event.displayName(), activationKey.getToken())));
    }
}
