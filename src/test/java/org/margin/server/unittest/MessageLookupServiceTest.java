package org.margin.server.unittest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.margin.server.social.api.MessageLookup;
import org.margin.server.social.channel.entities.Channel;
import org.margin.server.social.conversation.models.Conversation;
import org.margin.server.social.conversation.repositories.ConversationMemberRepository;
import org.margin.server.social.margin.entities.Margin;
import org.margin.server.social.messages.models.Message;
import org.margin.server.social.messages.repositories.MessageRepository;
import org.margin.server.social.messages.services.MessageLookupService;
import org.margin.server.social.space.models.Space;
import org.margin.server.users.models.User;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.margin.server.unittest.utils.UserTestUtils.createUser;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageLookupServiceTest {

    @Mock
    private MessageRepository messageRepository;
    @Mock
    private ConversationMemberRepository conversationMemberRepository;

    @InjectMocks
    private MessageLookupService messageLookupService;

    @Test
    @DisplayName("resolves the margin id through channel -> space -> margin for a channel message")
    void contextOf_channelMessage_resolvesMarginId() {
        Margin margin = new Margin();
        margin.setId(99L);
        Space space = new Space();
        space.setMargin(margin);
        Channel channel = new Channel();
        channel.setSpace(space);

        Conversation conversation = new Conversation();
        conversation.setId(5L);
        conversation.setChannel(channel);

        when(messageRepository.findById(10L)).thenReturn(Optional.of(message(conversation, createUser(2L))));

        MessageLookup.MessageContext context = messageLookupService.contextOf(10L);

        assertThat(context.authorId()).isEqualTo(2L);
        assertThat(context.conversationId()).isEqualTo(5L);
        assertThat(context.marginId()).isEqualTo(99L);
    }

    @Test
    @DisplayName("leaves the margin id null for a message outside any channel")
    void contextOf_directMessage_hasNoMarginId() {
        Conversation conversation = new Conversation();
        conversation.setId(5L);

        when(messageRepository.findById(10L)).thenReturn(Optional.of(message(conversation, createUser(2L))));

        assertThat(messageLookupService.contextOf(10L).marginId()).isNull();
    }

    @Test
    @DisplayName("threadFollowerIds maps conversation members to ids")
    void threadFollowerIds_mapsToIds() {
        when(conversationMemberRepository.findUserIdsByConversationId(5L))
                .thenReturn(List.of(1L, 2L));

        assertThat(messageLookupService.threadFollowerIds(5L)).containsExactly(1L, 2L);
    }

    private static Message message(Conversation conversation, User author) {
        Message message = new Message();
        message.setId(10L);
        message.setFromUserId(author.getId());
        message.setConversation(conversation);
        return message;
    }
}
