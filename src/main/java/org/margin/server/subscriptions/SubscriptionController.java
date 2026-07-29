package org.margin.server.subscriptions;

import lombok.extern.slf4j.Slf4j;
import org.margin.server.shared.security.AuthenticatedUser;
import org.margin.server.shared.authorization.MarginAccessChecker;
import org.margin.server.subscriptions.config.SubscriptionPricingProperties;
import org.margin.server.subscriptions.models.SubscriptionTier;
import org.margin.server.subscriptions.models.dtos.CheckoutRequest;
import org.margin.server.subscriptions.models.dtos.CheckoutResponse;
import org.margin.server.subscriptions.models.dtos.SubscriptionDTO;
import org.margin.server.subscriptions.models.dtos.TierPriceDTO;
import org.margin.server.subscriptions.services.SubscriptionService;
import org.margin.server.subscriptions.services.SubscriptionWebhookService;
import org.margin.server.users.models.User;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Slf4j
@RestController
@RequestMapping("/api/subscriptions")
public class SubscriptionController {
    private final SubscriptionService subscriptionService;
    private final SubscriptionWebhookService subscriptionWebhookService;
    private final MarginAccessChecker marginAccessChecker;
    private final SubscriptionPricingProperties pricingProperties;
    private final Executor webhookExecutor;

    public SubscriptionController(SubscriptionService subscriptionService,
                                  SubscriptionWebhookService subscriptionWebhookService,
                                  MarginAccessChecker marginAccessChecker,
                                  SubscriptionPricingProperties pricingProperties,
                                  @Qualifier("webhookExecutor") Executor webhookExecutor) {
        this.subscriptionService = subscriptionService;
        this.subscriptionWebhookService = subscriptionWebhookService;
        this.marginAccessChecker = marginAccessChecker;
        this.pricingProperties = pricingProperties;
        this.webhookExecutor = webhookExecutor;
    }

    @GetMapping("/margin/{marginId}")
    public ResponseEntity<SubscriptionDTO> getSubscriptionForMargin(@PathVariable Long marginId,
                                                                    @AuthenticationPrincipal AuthenticatedUser user) {
        marginAccessChecker.requireMarginMember(user.id(), marginId);
        return ResponseEntity.ok(subscriptionService.getSubscriptionDtoForMargin(marginId));
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
                                                          @AuthenticationPrincipal AuthenticatedUser user) {
        marginAccessChecker.requireMarginOwner(user.id(), request.marginId());
        String checkoutUrl = subscriptionService.createCheckout(
                request.marginId(), request.tier(), user.id()
        );
        return ResponseEntity.ok(new CheckoutResponse(checkoutUrl));
    }

    @PostMapping("/margin/{marginId}/tier")
    public ResponseEntity<SubscriptionDTO> changeTier(@PathVariable Long marginId,
                                                      @RequestBody Map<String, String> body,
                                                      @AuthenticationPrincipal AuthenticatedUser user) {
        marginAccessChecker.requireMarginOwner(user.id(), marginId);
        SubscriptionTier newTier = SubscriptionTier.valueOf(body.get("tier"));
        SubscriptionDTO dto = subscriptionService.downgrade(marginId, newTier);
        return ResponseEntity.ok(dto);
    }

    @DeleteMapping("/margin/{marginId}")
    public ResponseEntity<Void> cancelSubscription(@PathVariable Long marginId,
                                                   @AuthenticationPrincipal AuthenticatedUser user) {
        marginAccessChecker.requireMarginOwner(user.id(), marginId);
        subscriptionService.cancelSubscription(marginId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/margin/{marginId}/reconcile")
    public ResponseEntity<SubscriptionDTO> reconcilePending(@PathVariable Long marginId,
                                                            @AuthenticationPrincipal AuthenticatedUser user) {
        marginAccessChecker.requireMarginOwner(user.id(), marginId);
        SubscriptionDTO dto = subscriptionWebhookService.reconcilePendingPayment(marginId);
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/margin/{marginId}/cancel-pending")
    public ResponseEntity<SubscriptionDTO> cancelPending(@PathVariable Long marginId,
                                                         @AuthenticationPrincipal AuthenticatedUser user) {
        marginAccessChecker.requireMarginOwner(user.id(), marginId);
        SubscriptionDTO dto = subscriptionWebhookService.cancelPendingPayment(marginId);
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
        }, webhookExecutor);
        return ResponseEntity.ok().build();
    }
}
