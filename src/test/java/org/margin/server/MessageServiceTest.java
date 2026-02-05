package org.margin.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.margin.server.social.communication.messages.models.ChannelMessage;
import org.margin.server.social.communication.messages.models.DirectMessage;
import org.margin.server.social.communication.messages.models.dtos.ChannelMessageResult;
import org.margin.server.social.communication.messages.models.dtos.DirectMessageDTO;
import org.margin.server.social.communication.messages.repositories.ChannelMessageRepository;
import org.margin.server.social.communication.messages.repositories.DirectChatMessageRepository;
import org.margin.server.social.communication.messages.services.MessageService;
import org.margin.server.social.models.channel.Channel;
import org.margin.server.social.models.space.Space;
import org.margin.server.social.repositories.SpacesRepository;
import org.margin.server.users.models.User;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock
    private DirectChatMessageRepository directChatMessageRepository;

    @Mock
    private ChannelMessageRepository channelMessageRepository;

    @Mock
    private SpacesRepository spacesRepository;

    @InjectMocks
    private MessageService messageService;

    @Test
    void sendDirectMessage_savesAndReturnsDTO() {
        User fromUser = createUser(1L, "sender");
        User toUser = createUser(2L, "recipient");
        String content = "Hello!";

        DirectMessage savedMessage = new DirectMessage(fromUser, toUser, content);
        savedMessage.setId(100L);

        when(directChatMessageRepository.save(any(DirectMessage.class))).thenReturn(savedMessage);

        DirectMessageDTO result = messageService.sendDirectMessage(fromUser, toUser, content);

        assertNotNull(result);
        verify(directChatMessageRepository).save(any(DirectMessage.class));
    }

    @Test
    void sendChannelMessage_savesAndReturnsResult() {
        User fromUser = createUser(1L, "sender");
        Channel channel = createChannel(10L);
        String content = "Channel message";

        ChannelMessage savedMessage = new ChannelMessage(fromUser, channel, content);
        savedMessage.setId(200L);

        List<User> recipients = Arrays.asList(
                createUser(1L, "user1"),
                createUser(2L, "user2")
        );

        when(channelMessageRepository.save(any(ChannelMessage.class))).thenReturn(savedMessage);
        when(spacesRepository.getUsersForSpace(anyLong())).thenReturn(recipients);

        ChannelMessageResult result = messageService.sendChannelMessage(fromUser, channel, content);

        assertNotNull(result);
        assertNotNull(result.message());
        assertEquals(2, result.recipients().size());
        verify(channelMessageRepository).save(any(ChannelMessage.class));
        verify(spacesRepository).getUsersForSpace(channel.getSpace().getId());
    }

    @Test
    void setMessagesToRead_callsRepository() {
        Long fromUserId = 1L;
        Long toUserId = 2L;
        List<Long> messageIds = Arrays.asList(10L, 20L, 30L);

        messageService.setMessagesToRead(fromUserId, toUserId, messageIds);

        verify(directChatMessageRepository).setMessagesToRead(fromUserId, toUserId, messageIds);
    }

    @Test
    void getChatHistory_returnsAllMessagesSorted() {
        Long user1Id = 1L;
        Long user2Id = 2L;

        User user1 = createUser(user1Id, "user1");
        User user2 = createUser(user2Id, "user2");

        DirectMessage msg1 = new DirectMessage(user1, user2, "First");
        DirectMessage msg2 = new DirectMessage(user2, user1, "Second");

        when(directChatMessageRepository.findByFromUserIdAndToUserId(user1Id, user2Id))
                .thenReturn(List.of(msg1));
        when(directChatMessageRepository.findByFromUserIdAndToUserId(user2Id, user1Id))
                .thenReturn(List.of(msg2));

        List<DirectMessageDTO> result = messageService.getChatHistory(user1Id, user2Id);

        assertEquals(2, result.size());
        verify(directChatMessageRepository).findByFromUserIdAndToUserId(user1Id, user2Id);
        verify(directChatMessageRepository).findByFromUserIdAndToUserId(user2Id, user1Id);
    }

    private User createUser(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        return user;
    }

    private Channel createChannel(Long id) {
        Channel channel = new Channel();
        channel.setId(id);
        Space space = new Space();
        space.setId(1L);
        channel.setSpace(space);
        return channel;
    }
}