package org.margin.server.social.channel.models;

import org.margin.server.social.channel.entities.Channel;
import org.margin.server.users.models.dtos.UserDTO;

import java.util.List;

public record ChannelDTO(
        Long id,
        String name,
        String description,
        Long conversationId,
        ChannelType channelType,
        Long spaceId,
        List<UserDTO> voiceParticipants
) {
    public ChannelDTO(Long id,
                      String name,
                      String description,
                      Long conversationId,
                      ChannelType channelType,
                      Long spaceId) {
        this(id, name, description, conversationId, channelType, spaceId, List.of());
    }

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

    public ChannelDTO withVoiceParticipants(List<UserDTO> participants) {
        return new ChannelDTO(id, name, description, conversationId, channelType, spaceId, participants);
    }
}
