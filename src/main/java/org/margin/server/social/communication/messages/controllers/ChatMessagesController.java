package org.margin.server.social.communication.messages.controllers;

import org.margin.server.social.communication.messages.models.DirectMessage;
import org.margin.server.social.communication.messages.models.DirectMessageDTO;
import org.margin.server.social.communication.messages.models.SpaceChannelMessage;
import org.margin.server.social.communication.messages.models.dtos.SetMessagesToReadRequest;
import org.margin.server.social.communication.messages.repositories.DirectChatMessageRepository;
import org.margin.server.social.communication.messages.services.MessageService;
import org.margin.server.social.services.SpaceChannelService;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/chat_messages")
public class ChatMessagesController {
    private final DirectChatMessageRepository directChatMessageRepository;
    private final SpaceChannelService spaceChannelService;
    private final MessageService messageService;

    public ChatMessagesController(DirectChatMessageRepository directChatMessageRepository,
                                  SpaceChannelService spaceChannelService,
                                  MessageService messageService) {
        this.directChatMessageRepository = directChatMessageRepository;
        this.spaceChannelService = spaceChannelService;
        this.messageService = messageService;
    }

    @GetMapping("get_chat_history")
    public List<DirectMessage> getChatHistory(@RequestParam Long userId, @RequestParam Long toUserId) {
        List<DirectMessage> messagesFromUser = directChatMessageRepository.findByFromUserIdAndToUserId(userId, toUserId);
        List<DirectMessage> messagesToUser = directChatMessageRepository.findByFromUserIdAndToUserId(toUserId, userId);

        List<DirectMessage> allMessages = new ArrayList<>();
        allMessages.addAll(messagesFromUser);
        allMessages.addAll(messagesToUser);

        allMessages.sort(Comparator.comparing(DirectMessage::getCreatedAt));

        return allMessages;
    }

    @GetMapping("{channelId}/get_messages_for_channel")
    public List<SpaceChannelMessage> getMessagesForChannel(@PathVariable Long channelId) {
        return spaceChannelService.getMessagesForChannel(channelId);
    }

    @GetMapping("get_unread_messages_for_user")
    public List<DirectMessage> getUnreadMessagesForUser(@RequestParam Long userId) {
        return directChatMessageRepository.findByToUserIdAndWhereIsReadIsFalse(userId);
    }

    @PostMapping("set_messages_to_read")
    public void setMessagesToRead(@RequestBody SetMessagesToReadRequest request) {
        messageService.setMessagesToRead(
                request.fromUserId(),
                request.toUserId(),
                request.messageIds()
        );
    }
}