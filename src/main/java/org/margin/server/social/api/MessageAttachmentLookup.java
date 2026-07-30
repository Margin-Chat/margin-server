package org.margin.server.social.api;

import org.margin.server.social.api.MessageAttachmentDTO;

import java.util.List;
import java.util.Map;

public interface MessageAttachmentLookup {

    Map<Long, List<MessageAttachmentDTO>> findByMessageIds(List<Long> messageIds);
}
