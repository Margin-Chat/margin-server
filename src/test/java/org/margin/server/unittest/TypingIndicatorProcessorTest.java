package org.margin.server.unittest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.margin.server.social.conversation.exceptions.ConversationValidationException;
import org.margin.server.social.conversation.models.Conversation;
import org.margin.server.social.conversation.models.ConversationType;
import org.margin.server.social.conversation.services.ConversationService;
import org.margin.server.social.conversation.services.ConversationValidationService;
import org.margin.server.users.models.User;
import org.margin.server.websocket.models.WebSocketMessageIn;
import org.margin.server.websocket.models.WebSocketMessageType;
import org.margin.server.websocket.models.payloads.TypingIndicatorPayload;
import org.margin.server.websocket.processors.TypingIndicatorProcessor;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.margin.server.unittest.utils.UserTestUtils.createUser;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TypingIndicatorProcessorTest {

    @Mock
    private ConversationService conversationService;

    @Mock
    private ConversationValidationService conversationValidationService;

    @InjectMocks
    private TypingIndicatorProcessor processor;

    @Test
    void getType_returnsSendTypingIndicator() {
        assertEquals(WebSocketMessageType.SEND_TYPING_INDICATOR, processor.getType());
    }

    @Test
    void process_validatesMembershipThenNotifiesTyping() {
        User sender = createUser(1L, "sender");

        Conversation conversation = new Conversation();
        conversation.setId(10L);
        conversation.setType(ConversationType.DIRECT);

        WebSocketMessageIn<TypingIndicatorPayload> message = new WebSocketMessageIn<>();
        message.setRecipientId(10L);
        message.setPayload(new TypingIndicatorPayload(true));

        when(conversationService.getById(10L)).thenReturn(conversation);

        processor.process(sender, message);

        verify(conversationValidationService).validateUserIsInConversation(sender, conversation);
        ArgumentCaptor<Boolean> isTypingCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(conversationService).notifyTyping(eq(sender), eq(conversation), isTypingCaptor.capture());
        assertTrue(isTypingCaptor.getValue());
    }

    @Test
    void process_forwardsIsTypingFalse() {
        User sender = createUser(1L, "sender");

        Conversation conversation = new Conversation();
        conversation.setId(10L);
        conversation.setType(ConversationType.DIRECT);

        WebSocketMessageIn<TypingIndicatorPayload> message = new WebSocketMessageIn<>();
        message.setRecipientId(10L);
        message.setPayload(new TypingIndicatorPayload(false));

        when(conversationService.getById(10L)).thenReturn(conversation);

        processor.process(sender, message);

        ArgumentCaptor<Boolean> isTypingCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(conversationService).notifyTyping(eq(sender), eq(conversation), isTypingCaptor.capture());
        assertFalse(isTypingCaptor.getValue());
    }

    @Test
    void process_doesNotNotifyWhenUserNotInConversation() {
        User sender = createUser(1L, "sender");

        Conversation conversation = new Conversation();
        conversation.setId(10L);
        conversation.setType(ConversationType.DIRECT);

        WebSocketMessageIn<TypingIndicatorPayload> message = new WebSocketMessageIn<>();
        message.setRecipientId(10L);
        message.setPayload(new TypingIndicatorPayload(true));

        when(conversationService.getById(10L)).thenReturn(conversation);
        doThrow(new ConversationValidationException("User is not a part of the conversation"))
                .when(conversationValidationService).validateUserIsInConversation(sender, conversation);

        assertThrows(ConversationValidationException.class, () -> processor.process(sender, message));

        verify(conversationService, never()).notifyTyping(any(), any(), anyBoolean());
    }
}
