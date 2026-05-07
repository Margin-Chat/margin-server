package org.margin.server.subscriptions;

import org.margin.server.social.margin.entities.Margin;
import org.margin.server.social.margin.service.MarginService;
import org.margin.server.social.margin.validations.MarginAuthorizationService;
import org.margin.server.subscriptions.models.dtos.SubscriptionDTO;
import org.margin.server.subscriptions.services.SubscriptionService;
import org.margin.server.users.models.User;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/subscriptions")
public class SubscriptionController {
    private final SubscriptionService subscriptionService;
    private final MarginAuthorizationService marginAuthorizationService;
    private final MarginService marginService;

    public SubscriptionController(SubscriptionService subscriptionService,
                                  MarginAuthorizationService marginAuthorizationService, MarginService marginService) {
        this.subscriptionService = subscriptionService;
        this.marginAuthorizationService = marginAuthorizationService;
        this.marginService = marginService;
    }

    @GetMapping("/margin/{marginId}")
    public ResponseEntity<SubscriptionDTO> getSubscriptionForMargin(@PathVariable Long marginId,
                                                                    @AuthenticationPrincipal User user) {
        marginAuthorizationService.requireMarginAdmin(user.getId(), marginId);
        Margin margin = marginService.getById(marginId);
        return ResponseEntity.ok(subscriptionService.getSubscriptionDtoForMargin(margin));
    }
}
