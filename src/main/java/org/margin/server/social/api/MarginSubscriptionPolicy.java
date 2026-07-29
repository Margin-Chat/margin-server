package org.margin.server.social.api;

import org.margin.server.social.margin.entities.Margin;

public interface MarginSubscriptionPolicy {

    void onMarginCreated(Margin margin);

    void validateAddMarginMember(Margin margin);

    void notifyIfApproachingMemberLimit(Margin margin);
}
