package org.margin.server.social.channel.channel;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.margin.server.social.conversation.Conversation;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChannelDTO {
    private Long id;
    private String name;
    private String description;
    private Long conversationId;
    private Long spaceId;

    public ChannelDTO(Channel channel, Conversation conversation) {
        this.id = channel.getId();
        this.name = channel.getName();
        this.description = channel.getDescription();
        this.conversationId = conversation.getId();
        this.spaceId = channel.getSpace().getId();
    }
}
