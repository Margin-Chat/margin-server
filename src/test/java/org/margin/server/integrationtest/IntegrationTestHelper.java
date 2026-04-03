package org.margin.server.integrationtest;

import org.margin.server.social.conversation.models.Conversation;
import org.margin.server.social.conversation.models.ConversationMember;
import org.margin.server.social.conversation.models.ConversationType;
import org.margin.server.social.conversation.repositories.ConversationMemberRepository;
import org.margin.server.social.conversation.repositories.ConversationRepository;
import org.margin.server.users.models.User;
import org.margin.server.users.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

@Component
public class IntegrationTestHelper {

    private static UserRepository userRepository;
    private static ConversationRepository conversationRepository;
    private static ConversationMemberRepository conversationMemberRepository;

    @Autowired
    IntegrationTestHelper(
            UserRepository userRepository,
            ConversationRepository conversationRepository,
            ConversationMemberRepository conversationMemberRepository
    ) {
        IntegrationTestHelper.userRepository = userRepository;
        IntegrationTestHelper.conversationRepository = conversationRepository;
        IntegrationTestHelper.conversationMemberRepository = conversationMemberRepository;
    }

    public static User createUser(String username, String email) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setDisplayName(username);
        user.setPassword("hashed-password");
        user.setCreatedAt(Instant.now());
        return userRepository.save(user);
    }

    public static Conversation createDirectConversation(User userA, User userB) {
        Conversation conversation = new Conversation();
        conversation.setType(ConversationType.DIRECT);
        conversation = conversationRepository.save(conversation);
        createConversationMember(conversation, userA);
        createConversationMember(conversation, userB);
        return conversation;
    }

    private static void createConversationMember(Conversation conversation, User user) {
        ConversationMember member = new ConversationMember();
        member.setConversation(conversation);
        member.setUser(user);
        member.setJoinedAt(Instant.now());
        conversationMemberRepository.save(member);
    }

    public static WebSocket connectWebSocket(int port, String token, WebSocket.Listener listener) throws Exception {
        CompletableFuture<Void> connected = new CompletableFuture<>();

        WebSocket ws = HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .buildAsync(
                        URI.create("ws://localhost:" + port + "/ws?token=" + token),
                        new WebSocket.Listener() {
                            @Override
                            public void onOpen(WebSocket webSocket) {
                                connected.complete(null);
                                listener.onOpen(webSocket);
                            }

                            @Override
                            public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                                return listener.onText(webSocket, data, last);
                            }
                        })
                .get(5, TimeUnit.SECONDS);

        connected.get(5, TimeUnit.SECONDS);
        return ws;
    }

    public static WebSocket.Listener listenerThatCompletes(CompletableFuture<String> future, String containsText) {
        return new WebSocket.Listener() {
            @Override
            public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
                String text = data.toString();
                if (text.contains(containsText)) {
                    future.complete(text);
                }
                ws.request(1);
                return null;
            }
        };
    }

    public static void closeWebSocket(WebSocket... sockets) {
        for (WebSocket ws : sockets) {
            if (ws != null) ws.sendClose(WebSocket.NORMAL_CLOSURE, "done");
        }
    }

    public static String wsFrame(String type, long recipientId, String payload) {
        return """
            {
              "type": "%s",
              "recipientId": %d,
              "payload": "%s"
            }
            """.formatted(type, recipientId, payload);
    }
}