package org.margin.server.subscriptions.models.dtos;

public record SubscriptionLimitsDTO(
        int maxMembers,
        int maxStorageGb,
        int maxCallParticipants
) {
}
