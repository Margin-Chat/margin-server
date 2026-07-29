package org.margin.server.integrationtest.utils;

import org.margin.server.social.conversation.controllers.ThreadController;
import static org.margin.server.integrationtest.utils.UserTestUtils.principalOf;
import org.margin.server.social.conversation.models.Conversation;
import org.margin.server.social.conversation.models.dtos.CreateThreadPostRequest;
import org.margin.server.social.conversation.models.dtos.ThreadConversationDTO;
import org.margin.server.social.conversation.models.dtos.ThreadSummaryDTO;
import org.margin.server.social.conversation.services.ConversationService;
import org.margin.server.social.messages.services.MessageService;
import org.margin.server.users.models.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ThreadTestUtils {

    private static ThreadController threadController;
    private static MessageService messageService;
    private static ConversationService conversationService;

    @Autowired
    public ThreadTestUtils(ThreadController threadController,
                           MessageService messageService,
                           ConversationService conversationService) {
        ThreadTestUtils.threadController = threadController;
        ThreadTestUtils.messageService = messageService;
        ThreadTestUtils.conversationService = conversationService;
    }

    public static ThreadConversationDTO createPost(User user, Long channelId, String title, String body) {
        return threadController.createPost(principalOf(user), channelId, new CreateThreadPostRequest(title, body));
    }

    public static List<ThreadSummaryDTO> getPostsForChannel(User user, Long channelId) {
        return threadController.getPostsForChannel(principalOf(user), channelId);
    }

    public static ThreadConversationDTO getThread(User user, Long threadConversationId) {
        return threadController.getThread(principalOf(user), threadConversationId);
    }

    public static List<ThreadSummaryDTO> getFollowedThreads(User user) {
        return threadController.getFollowedThreads(principalOf(user));
    }

    public static void follow(User user, Long threadConversationId) {
        threadController.followThread(principalOf(user), threadConversationId);
    }

    public static void unfollow(User user, Long threadConversationId) {
        threadController.unfollowThread(principalOf(user), threadConversationId);
    }

    public static void sendMessage(User from, Long conversationId, String content) {
        Conversation conversation = conversationService.getById(conversationId);
        messageService.sendMessage(from, content, conversation.getId(), List.of());
    }
}
