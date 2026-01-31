package org.margin.server.social.communication.messages.controllers;

import org.margin.server.social.communication.messages.models.DirectMessage;
import org.margin.server.social.communication.messages.models.dtos.DirectMessageDTO;
import org.margin.server.social.communication.messages.models.dtos.SetMessagesToReadRequest;
import org.margin.server.social.communication.messages.models.dtos.ChannelMessageDTO;
import org.margin.server.social.communication.messages.repositories.DirectChatMessageRepository;
import org.margin.server.social.communication.messages.services.MessageService;
import org.margin.server.social.services.ChannelService;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/chat_messages")
public class ChatMessagesController {
    private final DirectChatMessageRepository directChatMessageRepository;
    private final ChannelService channelService;
    private final MessageService messageService;

    public ChatMessagesController(DirectChatMessageRepository directChatMessageRepository,
                                  ChannelService channelService,
                                  MessageService messageService) {
        this.directChatMessageRepository = directChatMessageRepository;
        this.channelService = channelService;
        this.messageService = messageService;
    }

    @GetMapping("get_chat_history")
    public List<DirectMessageDTO> getChatHistory(@RequestParam Long userId, @RequestParam Long toUserId) {
        return messageService.getChatHistory(userId, toUserId);
    }

    @GetMapping("{channelId}/get_messages_for_channel")
    public List<ChannelMessageDTO> getMessagesForChannel(@PathVariable Long channelId) {
        return channelService.getMessagesForChannel(channelId);
    }

    @GetMapping("get_unread_messages_for_user")
    public List<DirectMessageDTO> getUnreadMessagesForUser(@RequestParam Long userId) {
        return directChatMessageRepository.findByToUserIdAndWhereIsReadIsFalse(userId).stream()
                .map(DirectMessageDTO::fromEntity)
                .collect(Collectors.toList());
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