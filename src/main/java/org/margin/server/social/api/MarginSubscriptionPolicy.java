package org.margin.server.social.api;

public interface MarginSubscriptionPolicy {

    void onMarginCreated(Long marginId);

    void validateAddMarginMember(Long marginId);

    void notifyIfApproachingMemberLimit(Long marginId);
}
