package org.margin.server.shared.voice;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface VoiceParticipantLookup {

    Map<Long, List<Long>> participantIdsByChannel(Collection<Long> channelIds);
}
