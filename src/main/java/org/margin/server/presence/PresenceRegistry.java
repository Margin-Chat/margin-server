package org.margin.server.presence;

import java.util.Set;

public interface PresenceRegistry {

    boolean isUserOnline(Long userId);

    Set<Long> getOnlineUserIds();
}
