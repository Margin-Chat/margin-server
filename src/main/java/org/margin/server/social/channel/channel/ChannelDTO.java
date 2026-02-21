package org.margin.server.social.channel.channel;

import org.margin.server.social.conversation.Conversation;

public record ChannelDTO(
        Long id,
        String name,
        String description,
        Long conversationId,
        Long spaceId
) {
    public ChannelDTO(Channel channel) {
        this(
                channel.getId(),
                channel.getName(),
                channel.getDescription(),
                channel.getConversation().getId(),
                channel.getSpace().getId()
        );
    }
}
