package org.margin.server.shared.membership;

public interface MarginMembershipLookup {
    boolean hasAnyMembership(Long userId);
}
