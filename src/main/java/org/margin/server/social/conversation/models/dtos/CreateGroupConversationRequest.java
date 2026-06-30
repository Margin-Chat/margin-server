package org.margin.server.social.conversation.models.dtos;

import java.util.List;

public record CreateGroupConversationRequest(
        List<String> memberEmails,
        String name,
        boolean encrypted
) {
}
