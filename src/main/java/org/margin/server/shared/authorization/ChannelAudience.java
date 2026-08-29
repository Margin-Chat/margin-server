package org.margin.server.shared.authorization;

import java.util.List;

public interface ChannelAudience {

    List<Long> memberIdsForChannel(Long channelId);
}
