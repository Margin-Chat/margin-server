package org.margin.server.shared.membership;

/**
 * Whether a user belongs to any margin. Lives in shared so the users module can consult it
 * without depending on social, which already depends on users.
 */
public interface MarginMembershipLookup {

    boolean hasAnyMembership(Long userId);
}
