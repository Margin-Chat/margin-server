package org.margin.server.subscriptions.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "subscription_limits")
public class SubscriptionLimits {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id", nullable = false, unique = true)
    private Subscription subscription;

    @Column(nullable = false)
    private int maxMembers = 25;

    @Column(nullable = false)
    private int maxStorageGb = 5;

    @Column(nullable = false)
    private int maxCallParticipants = 10;
}