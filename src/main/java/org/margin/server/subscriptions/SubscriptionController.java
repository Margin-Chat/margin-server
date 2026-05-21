package org.margin.server.subscriptions;

import lombok.extern.slf4j.Slf4j;
import org.margin.server.social.margin.MarginLookup;
import org.margin.server.social.margin.entities.Margin;
import org.margin.server.social.margin.validations.MarginAuthorizationService;
import org.margin.server.subscriptions.config.SubscriptionPricingProperties;
import org.margin.server.subscriptions.models.SubscriptionTier;
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
import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequestMapping("/api/subscriptions")
public class SubscriptionController {
    private final SubscriptionService subscriptionService;
    private final SubscriptionWebhookService subscriptionWebhookService;
    private final MarginAuthorizationService marginAuthorizationService;
    private final SubscriptionPricingProperties pricingProperties;
    private final MarginLookup marginLookup;

    public SubscriptionController(SubscriptionService subscriptionService,
                                  SubscriptionWebhookService subscriptionWebhookService,
                                  MarginAuthorizationService marginAuthorizationService,
                                  SubscriptionPricingProperties pricingProperties,
                                  MarginLookup marginLookup) {
        this.subscriptionService = subscriptionService;
        this.subscriptionWebhookService = subscriptionWebhookService;
        this.marginAuthorizationService = marginAuthorizationService;
        this.pricingProperties = pricingProperties;
        this.marginLookup = marginLookup;
    }

    @GetMapping("/margin/{marginId}")
    public ResponseEntity<SubscriptionDTO> getSubscriptionForMargin(@PathVariable Long marginId,
                                                                    @AuthenticationPrincipal User user) {
        marginAuthorizationService.requireMarginMember(user.getId(), marginId);
        Margin margin = marginLookup.getById(marginId);
        return ResponseEntity.ok(subscriptionService.getSubscriptionDtoForMargin(margin));
    }

    @GetMapping("/tiers")
    public ResponseEntity<List<TierPriceDTO>> getTiers() {
        List<TierPriceDTO> tiers = List.of(SubscriptionTier.FREE, SubscriptionTier.SMALL, SubscriptionTier.MEDIUM)
                .stream()
                .map(tier -> {
                    SubscriptionPricingProperties.TierConfig config = pricingProperties.tiers().get(tier);
                    return new TierPriceDTO(
                            tier,
                            config.price(),
                            pricingProperties.currency(),
                            config.maxMembers(),
                            config.maxStorageGb(),
                            config.maxCallParticipants()
                    );
                })
                .toList();
        return ResponseEntity.ok(tiers);
    }

    @PostMapping("/checkout")
    public ResponseEntity<CheckoutResponse> startCheckout(@RequestBody CheckoutRequest request,
                                                          @AuthenticationPrincipal User user) {
        marginAuthorizationService.requireMarginOwner(user.getId(), request.marginId());
        String checkoutUrl = subscriptionService.createCheckout(
                marginLookup.getById(request.marginId()), request.tier(), user
        );
        return ResponseEntity.ok(new CheckoutResponse(checkoutUrl));
    }

    @PostMapping("/margin/{marginId}/tier")
    public ResponseEntity<SubscriptionDTO> changeTier(@PathVariable Long marginId,
                                                      @RequestBody Map<String, String> body,
                                                      @AuthenticationPrincipal User user) {
        marginAuthorizationService.requireMarginOwner(user.getId(), marginId);
        SubscriptionTier newTier = SubscriptionTier.valueOf(body.get("tier"));
        SubscriptionDTO dto = subscriptionService.downgrade(marginLookup.getById(marginId), newTier);
        return ResponseEntity.ok(dto);
    }

    @DeleteMapping("/margin/{marginId}")
    public ResponseEntity<Void> cancelSubscription(@PathVariable Long marginId,
                                                   @AuthenticationPrincipal User user) {
        marginAuthorizationService.requireMarginOwner(user.getId(), marginId);
        subscriptionService.cancelSubscription(marginLookup.getById(marginId));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/margin/{marginId}/reconcile")
    public ResponseEntity<SubscriptionDTO> reconcilePending(@PathVariable Long marginId,
                                                            @AuthenticationPrincipal User user) {
        marginAuthorizationService.requireMarginOwner(user.getId(), marginId);
        SubscriptionDTO dto = subscriptionWebhookService.reconcilePendingPayment(marginLookup.getById(marginId));
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/margin/{marginId}/cancel-pending")
    public ResponseEntity<SubscriptionDTO> cancelPending(@PathVariable Long marginId,
                                                         @AuthenticationPrincipal User user) {
        marginAuthorizationService.requireMarginOwner(user.getId(), marginId);
        SubscriptionDTO dto = subscriptionWebhookService.cancelPendingPayment(marginLookup.getById(marginId));
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/mollie/webhook")
    public ResponseEntity<Void> mollieWebhook(@RequestParam Map<String, String> form) {
        String paymentId = form.get("id");
        if (paymentId == null) {
            log.warn("Mollie webhook missing id: {}", form);
            return ResponseEntity.ok().build();
        }
        CompletableFuture.runAsync(() -> {
            try {
                subscriptionWebhookService.handleWebhook(paymentId);
            } catch (Exception e) {
                log.error("Failed to process Mollie webhook for payment {}", paymentId, e);
            }
        });
        return ResponseEntity.ok().build();
    }
}
