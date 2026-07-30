package org.margin.server.subscriptions.repositories;

import org.margin.server.subscriptions.entities.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    Optional<Subscription> findByMarginId(Long marginId);

    Optional<Subscription> findByMollieCustomerId(String mollieCustomerId);

    @Query("SELECT s FROM Subscription s WHERE s.pendingPaymentId IS NOT NULL AND s.updatedAt < :before")
    List<Subscription> findStalePendingPayments(@Param("before") Instant before);

    @Query("SELECT s FROM Subscription s WHERE s.status IN ('CANCELLED', 'PAST_DUE') AND s.currentPeriodEnd < :now")
    List<Subscription> findExpiredSubscriptions(@Param("now") Instant now);
}
