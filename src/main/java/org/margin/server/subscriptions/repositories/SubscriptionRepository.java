package org.margin.server.subscriptions.repositories;

import org.margin.server.social.margin.entities.Margin;
import org.margin.server.subscriptions.entities.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    Optional<Subscription> findByMargin(Margin margin);
}
