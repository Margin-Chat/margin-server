package org.margin.server.unittest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.margin.server.social.conversation.models.Conversation;
import org.margin.server.social.conversation.models.ConversationType;
import org.margin.server.social.conversation.services.ConversationService;
import org.margin.server.social.conversation.services.ConversationValidationService;
import org.margin.server.social.messages.services.MessageService;
import org.margin.server.users.models.User;
import org.margin.server.websocket.models.WebSocketMessageIn;
import org.margin.server.websocket.models.WebSocketMessageType;
import org.margin.server.websocket.models.payloads.SendMessagePayload;
import org.margin.server.websocket.processors.SendMessageProcessor;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

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
        message.setPayload(new SendMessagePayload("Hello", "https://example.com/image.png"));

        when(conversationService.getById(10L)).thenReturn(conversation);
        doNothing().when(messageService).sendMessage(sender, "Hello", conversation, "https://example.com/image.png");

        processor.process(sender, message);

        verify(conversationService).getById(10L);
        verify(messageService).sendMessage(sender, "Hello", conversation, "https://example.com/image.png");
    }

    @Test
    void process_passesCorrectPayloadToMessageService() {
        User sender = createUser(1L, "sender");

        Conversation conversation = new Conversation();
        conversation.setId(10L);
        conversation.setType(ConversationType.GROUP);

        String content = "Test message content";
        String image = "https://example.com/image.png";

        WebSocketMessageIn<SendMessagePayload> message = new WebSocketMessageIn<>();
        message.setRecipientId(10L);
        message.setPayload(new SendMessagePayload(content, image));

        when(conversationService.getById(10L)).thenReturn(conversation);

        processor.process(sender, message);

        ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);
        verify(messageService).sendMessage(eq(sender), contentCaptor.capture(), eq(conversation), eq(image));
        assertEquals(content, contentCaptor.getValue());
    }

    @Test
    void process_throwsWhenConversationNotFound() {
        User sender = createUser(1L, "sender");

        WebSocketMessageIn<SendMessagePayload> message = new WebSocketMessageIn<>();
        message.setRecipientId(999L);
        message.setPayload(new SendMessagePayload("Hello", null));

        when(conversationService.getById(999L))
                .thenThrow(new RuntimeException("Conversation not found"));

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

        when(conversationService.getById(10L)).thenReturn(conversation);
        doThrow(new RuntimeException("DB error")).when(messageService).sendMessage(sender, "Hello", conversation, null);

        assertThrows(RuntimeException.class, () -> processor.process(sender, message));
    }

    private User createUser(Long id, String handle) {
        User user = new User();
        user.setId(id);
        user.setHandle(handle);
        return user;
    }
}