package org.margin.server.subscriptions.models;

import org.springframework.modulith.NamedInterface;

import lombok.Getter;

@NamedInterface("api")
@Getter
public enum SubscriptionTier {
    FREE(1),
    SMALL(2),
    MEDIUM(3),
    CUSTOM(4);

    private final int level;

    SubscriptionTier(int level) {
        this.level = level;
    }

    public boolean isLowerTier(SubscriptionTier other) {
        if (other == null) {
            return false;
        }
        return this.level < other.getLevel();
    }
}
