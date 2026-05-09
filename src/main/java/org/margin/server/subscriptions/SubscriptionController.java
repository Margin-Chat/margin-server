package org.margin.server.subscriptions;

import lombok.extern.slf4j.Slf4j;
import org.margin.server.social.margin.entities.Margin;
import org.margin.server.social.margin.service.MarginService;
import org.margin.server.social.margin.validations.MarginAuthorizationService;
import org.margin.server.subscriptions.config.SubscriptionPricingProperties;
import org.margin.server.subscriptions.models.dtos.CheckoutRequest;
import org.margin.server.subscriptions.models.dtos.CheckoutResponse;
import org.margin.server.subscriptions.models.dtos.SubscriptionDTO;
import org.margin.server.subscriptions.models.dtos.TierPriceDTO;
import org.margin.server.subscriptions.services.SubscriptionService;
import org.margin.server.subscriptions.services.SubscriptionWebhookService;
import org.margin.server.users.models.User;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/subscriptions")
public class SubscriptionController {
    private final SubscriptionService subscriptionService;
    private final SubscriptionWebhookService subscriptionWebhookService;
    private final MarginAuthorizationService marginAuthorizationService;
    private final MarginService marginService;
    private final SubscriptionPricingProperties pricingProperties;

    public SubscriptionController(SubscriptionService subscriptionService,
                                  SubscriptionWebhookService subscriptionWebhookService,
                                  MarginAuthorizationService marginAuthorizationService,
                                  MarginService marginService,
                                  SubscriptionPricingProperties pricingProperties) {
        this.subscriptionService = subscriptionService;
        this.subscriptionWebhookService = subscriptionWebhookService;
        this.marginAuthorizationService = marginAuthorizationService;
        this.marginService = marginService;
        this.pricingProperties = pricingProperties;
    }

    @GetMapping("/margin/{marginId}")
    public ResponseEntity<SubscriptionDTO> getSubscriptionForMargin(@PathVariable Long marginId,
                                                                    @AuthenticationPrincipal User user) {
        marginAuthorizationService.requireMarginAdmin(user.getId(), marginId);
        Margin margin = marginService.getById(marginId);
        return ResponseEntity.ok(subscriptionService.getSubscriptionDtoForMargin(margin));
    }

    @GetMapping("/tiers")
    public ResponseEntity<List<TierPriceDTO>> getTiers() {
        List<TierPriceDTO> tiers = pricingProperties.prices().entrySet().stream()
                .map(e ->
                        new TierPriceDTO(e.getKey(), e.getValue(), pricingProperties.currency()))
                .toList();
        return ResponseEntity.ok(tiers);
    }

    @PostMapping("/checkout")
    public ResponseEntity<CheckoutResponse> startCheckout(@RequestBody CheckoutRequest request,
                                                          @AuthenticationPrincipal User user) {
        marginAuthorizationService.requireMarginAdmin(user.getId(), request.marginId());
        String checkoutUrl = subscriptionService.createCheckout(
                marginService.getById(request.marginId()), request.tier(), user
        );
        return ResponseEntity.ok(new CheckoutResponse(checkoutUrl));
    }

    @PostMapping("/mollie/webhook")
    public ResponseEntity<Void> mollieWebhook(@RequestParam Map<String, String> form) {
        String paymentId = form.get("id");
        if (paymentId == null) {
            log.warn("Mollie webhook missing id: {}", form);
            return ResponseEntity.ok().build();
        }
        try {
            subscriptionWebhookService.handleWebhook(paymentId);
        } catch (Exception e) {
            log.error("Failed to process Mollie webhook for payment {}", paymentId, e);
        }
        return ResponseEntity.ok().build();
    }
}
