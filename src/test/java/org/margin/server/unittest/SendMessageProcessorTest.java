package org.margin.server.unittest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.margin.server.social.conversation.models.Conversation;
import org.margin.server.social.api.ConversationType;
import org.margin.server.social.conversation.services.ConversationService;
import org.margin.server.social.conversation.services.ConversationValidationService;
import org.margin.server.social.messages.services.MessageService;
import org.margin.server.users.models.User;
import org.margin.server.websocket.models.WebSocketMessageIn;
import org.margin.server.websocket.models.WebSocketMessageType;

import java.util.List;
import org.margin.server.websocket.models.payloads.SendMessagePayload;
import org.margin.server.websocket.processors.SendMessageProcessor;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;
import static org.margin.server.unittest.utils.UserTestUtils.*;

@ExtendWith(MockitoExtension.class)
class SendMessageProcessorTest {

    @Mock
    private ConversationService conversationService;

    @Mock
    private MessageService messageService;

    @Mock
    private ConversationValidationService conversationValidationService;

    @InjectMocks
    private SendMessageProcessor processor;

    @Test
    void getType_returnsSendMessage() {
        assertEquals(WebSocketMessageType.SEND_MESSAGE, processor.getType());
    }

    @Test
    void process_savesMessageAndNotifiesRecipients() {
        User sender = createUser(1L, "sender");

        Conversation conversation = new Conversation();
        conversation.setId(10L);
        conversation.setType(ConversationType.DIRECT);

        WebSocketMessageIn<SendMessagePayload> message = new WebSocketMessageIn<>();
        message.setType(WebSocketMessageType.SEND_MESSAGE);
        message.setRecipientId(10L);
        message.setPayload(new SendMessagePayload("Hello", List.of(7L)));

        doNothing().when(messageService).sendMessage(sender.getId(), "Hello", conversation.getId(), List.of(7L));

        processor.process(sender, message);

        verify(messageService).sendMessage(sender.getId(), "Hello", conversation.getId(), List.of(7L));
    }

    @Test
    void process_passesCorrectPayloadToMessageService() {
        User sender = createUser(1L, "sender");

        Conversation conversation = new Conversation();
        conversation.setId(10L);
        conversation.setType(ConversationType.GROUP);

        String content = "Test message content";
        List<Long> ids = List.of(11L, 12L);

        WebSocketMessageIn<SendMessagePayload> message = new WebSocketMessageIn<>();
        message.setRecipientId(10L);
        message.setPayload(new SendMessagePayload(content, ids));


        processor.process(sender, message);

        ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);
        verify(messageService).sendMessage(eq(sender.getId()), contentCaptor.capture(), eq(conversation.getId()), eq(ids));
        assertEquals(content, contentCaptor.getValue());
    }

    @Test
    void process_propagatesWhenConversationInvalid() {
        User sender = createUser(1L, "sender");

        WebSocketMessageIn<SendMessagePayload> message = new WebSocketMessageIn<>();
        message.setRecipientId(999L);
        message.setPayload(new SendMessagePayload("Hello", null));

        doThrow(new RuntimeException("Conversation not found"))
                .when(conversationValidationService).validateUserIsInConversation(sender.getId(), 999L);

        assertThrows(RuntimeException.class, () -> processor.process(sender, message));
        verifyNoInteractions(messageService);
    }

    @Test
    void process_doesNotNotifyWhenMessageServiceFails() {
        User sender = createUser(1L, "sender");

        Conversation conversation = new Conversation();
        conversation.setId(10L);
        conversation.setType(ConversationType.DIRECT);

        WebSocketMessageIn<SendMessagePayload> message = new WebSocketMessageIn<>();
        message.setRecipientId(10L);
        message.setPayload(new SendMessagePayload("Hello", null));

        doThrow(new RuntimeException("DB error")).when(messageService).sendMessage(sender.getId(), "Hello", conversation.getId(), null);

        assertThrows(RuntimeException.class, () -> processor.process(sender, message));
    }

}