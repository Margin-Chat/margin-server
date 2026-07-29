package org.margin.server.social.messages.models.dtos;

import org.margin.server.social.api.MessageAttachmentDTO;
import org.margin.server.social.conversation.models.ConversationType;
import org.margin.server.social.messages.models.Message;
import org.margin.server.users.models.dtos.UserDTO;

import java.time.Instant;
import java.util.List;

public record MessageDTO(
        Long id,
        Long conversationId,
        ConversationType conversationType,
        UserDTO user,
        String content,
        boolean isEdited,
        Instant createdAt,
        Long marginId,
        String channelName,
        List<MessageReactionDTO> reactions,
        List<MessageAttachmentDTO> attachments,
        Long parentConversationId
) {

    public static Builder from(Message message) {
        return new Builder(message);
    }

    public static class Builder {
        private final Message message;
        private boolean isUserOnline = false;
        private Long marginId = null;
        private String channelName = null;
        private List<MessageReactionDTO> reactions = List.of();
        private List<MessageAttachmentDTO> attachments = List.of();

        private Builder(Message message) {
            this.message = message;
        }

        public Builder withOnline(boolean isUserOnline) {
            this.isUserOnline = isUserOnline;
            return this;
        }

        public Builder withMarginId(Long marginId) {
            this.marginId = marginId;
            return this;
        }

        public Builder withChannelName(String channelName) {
            this.channelName = channelName;
            return this;
        }

        public Builder withReactions(List<MessageReactionDTO> reactions) {
            this.reactions = reactions != null ? reactions : List.of();
            return this;
        }

        public Builder withAttachments(List<MessageAttachmentDTO> attachments) {
            this.attachments = attachments != null ? attachments : List.of();
            return this;
        }

        public MessageDTO build() {
            return new MessageDTO(
                    message.getId(),
                    message.getConversation().getId(),
                    message.getConversation().getType(),
                    new UserDTO(message.getFromUser(), isUserOnline),
                    message.getMessage(),
                    message.getIsEdited(),
                    message.getCreatedAt(),
                    marginId,
                    channelName,
                    reactions,
                    attachments,
                    message.getConversation().getParentConversationId()
            );
        }
    }
}
