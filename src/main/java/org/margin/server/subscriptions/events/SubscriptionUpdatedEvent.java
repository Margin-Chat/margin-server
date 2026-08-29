package org.margin.server.subscriptions.events;

import org.margin.server.subscriptions.models.dtos.SubscriptionDTO;

public record SubscriptionUpdatedEvent(Long recipientId, SubscriptionDTO subscription) {
}
