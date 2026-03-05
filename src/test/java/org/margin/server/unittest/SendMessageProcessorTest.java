package org.margin.server.unittest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.margin.server.notifications.NotificationService;
import org.margin.server.social.conversation.Conversation;
import org.margin.server.social.conversation.ConversationType;
import org.margin.server.social.conversation.services.ConversationService;
import org.margin.server.social.messages.models.dtos.MessageDTO;
import org.margin.server.social.messages.models.dtos.MessageResult;
import org.margin.server.social.messages.services.MessageService;
import org.margin.server.users.models.User;
import org.margin.server.websocket.models.WebSocketMessageIn;
import org.margin.server.websocket.models.WebSocketMessageType;
import org.margin.server.websocket.processors.SendMessageProcessor;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SendMessageProcessorTest {

    @Mock
    private ConversationService conversationService;

    @Mock
    private MessageService messageService;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private SendMessageProcessor processor;

    @Test
    void getType_returnsSendMessage() {
        assertEquals(WebSocketMessageType.SEND_MESSAGE, processor.getType());
    }

    @Test
    void process_savesMessageAndNotifiesRecipients() {
        User sender = createUser(1L, "sender");
        User recipient = createUser(2L, "recipient");

        Conversation conversation = new Conversation();
        conversation.setId(10L);
        conversation.setType(ConversationType.DIRECT);

        MessageDTO messageDTO = mock(MessageDTO.class);
        MessageResult result = new MessageResult(messageDTO, List.of(sender, recipient));

        WebSocketMessageIn<String> message = new WebSocketMessageIn<>();
        message.setType(WebSocketMessageType.SEND_MESSAGE);
        message.setRecipientId(10L);
        message.setPayload("Hello");

        when(conversationService.getById(10L)).thenReturn(conversation);
        when(messageService.sendMessage(eq(sender), eq(conversation), eq("Hello")))
                .thenReturn(result);

        processor.process(sender, message);

        verify(conversationService).getById(10L);
        verify(messageService).sendMessage(sender, conversation, "Hello");
        verify(notificationService).notifyMessage(messageDTO, List.of(sender, recipient), ConversationType.DIRECT);
    }

    @Test
    void process_passesCorrectPayloadToMessageService() {
        User sender = createUser(1L, "sender");

        Conversation conversation = new Conversation();
        conversation.setId(10L);
        conversation.setType(ConversationType.GROUP);

        String payload = "Test message content";
        MessageDTO messageDTO = mock(MessageDTO.class);
        MessageResult result = new MessageResult(messageDTO, List.of(sender));

        WebSocketMessageIn<String> message = new WebSocketMessageIn<>();
        message.setRecipientId(10L);
        message.setPayload(payload);

        when(conversationService.getById(10L)).thenReturn(conversation);
        when(messageService.sendMessage(any(), any(), any())).thenReturn(result);

        processor.process(sender, message);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(messageService).sendMessage(eq(sender), eq(conversation), payloadCaptor.capture());
        assertEquals(payload, payloadCaptor.getValue());
    }

    @Test
    void process_notifiesAllRecipients() {
        User sender = createUser(1L, "sender");
        User recipient1 = createUser(2L, "recipient1");
        User recipient2 = createUser(3L, "recipient2");
        List<User> recipients = List.of(sender, recipient1, recipient2);

        Conversation conversation = new Conversation();
        conversation.setId(10L);
        conversation.setType(ConversationType.GROUP);

        MessageDTO messageDTO = mock(MessageDTO.class);
        MessageResult result = new MessageResult(messageDTO, recipients);

        WebSocketMessageIn<String> message = new WebSocketMessageIn<>();
        message.setRecipientId(10L);
        message.setPayload("Group message");

        when(conversationService.getById(10L)).thenReturn(conversation);
        when(messageService.sendMessage(any(), any(), any())).thenReturn(result);

        processor.process(sender, message);

        ArgumentCaptor<List> recipientsCaptor = ArgumentCaptor.forClass(List.class);
        verify(notificationService).notifyMessage(eq(messageDTO), recipientsCaptor.capture(), eq(ConversationType.GROUP));
        assertEquals(3, recipientsCaptor.getValue().size());
    }

    @Test
    void process_throwsWhenConversationNotFound() {
        User sender = createUser(1L, "sender");

        WebSocketMessageIn<String> message = new WebSocketMessageIn<>();
        message.setRecipientId(999L);
        message.setPayload("Hello");

        when(conversationService.getById(999L))
                .thenThrow(new RuntimeException("Conversation not found"));

        assertThrows(RuntimeException.class, () -> processor.process(sender, message));
        verifyNoInteractions(notificationService);
    }

    @Test
    void process_doesNotNotifyWhenMessageServiceFails() {
        User sender = createUser(1L, "sender");

        Conversation conversation = new Conversation();
        conversation.setId(10L);
        conversation.setType(ConversationType.DIRECT);

        WebSocketMessageIn<String> message = new WebSocketMessageIn<>();
        message.setRecipientId(10L);
        message.setPayload("Hello");

        when(conversationService.getById(10L)).thenReturn(conversation);
        when(messageService.sendMessage(any(), any(), any()))
                .thenThrow(new RuntimeException("DB error"));

        assertThrows(RuntimeException.class, () -> processor.process(sender, message));
        verifyNoInteractions(notificationService);
    }

    private User createUser(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        return user;
    }
}