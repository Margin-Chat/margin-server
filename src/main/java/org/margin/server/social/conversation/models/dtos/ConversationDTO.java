package org.margin.server.social.conversation.models.dtos;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.time.LocalDateTime;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = DirectConversationDTO.class, name = "DIRECT"),
        @JsonSubTypes.Type(value = GroupConversationDTO.class, name = "GROUP"),
        @JsonSubTypes.Type(value = ChannelConversationDTO.class, name = "CHANNEL")
})
public sealed interface ConversationDTO permits DirectConversationDTO, GroupConversationDTO, ChannelConversationDTO {
    Long id();

    String type();

    LocalDateTime createdAt();
}