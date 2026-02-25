package org.margin.server.social.channel.channel;

public record ChannelDTO(
        Long id,
        String name,
        String description,
        Long conversationId,
        ChannelType channelType,
        Long spaceId
) {
    public ChannelDTO(Channel channel) {
        this(
                channel.getId(),
                channel.getName(),
                channel.getDescription(),
                channel.getConversation().getId(),
                channel.getChannelType(),
                channel.getSpace().getId()
        );
    }
}
