package org.margin.server.social.conversation.models.dtos;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.time.Instant;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = DirectConversationDTO.class, name = "DIRECT"),
        @JsonSubTypes.Type(value = GroupConversationDTO.class, name = "GROUP"),
        @JsonSubTypes.Type(value = ChannelConversationDTO.class, name = "CHANNEL"),
        @JsonSubTypes.Type(value = ThreadConversationDTO.class, name = "THREAD")
})
public sealed interface ConversationDTO permits DirectConversationDTO, GroupConversationDTO, ChannelConversationDTO, ThreadConversationDTO {
    Long id();

    String type();

    Instant createdAt();
}