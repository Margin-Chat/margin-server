package org.margin.server.integrationtest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.margin.server.integrationtest.config.MarginTestRunner;
import org.margin.server.integrationtest.utils.*;
import org.margin.server.social.conversation.models.Conversation;
import org.margin.server.social.messages.models.Message;
import org.margin.server.social.messages.models.MessageReaction;
import org.margin.server.users.models.User;

import java.net.http.WebSocket;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ReactMessageTest extends MarginTestRunner {

    private WebSocket reactorWs;
    private WebSocket observerWs;

    @AfterEach
    void tearDown() {
        WebSocketTestUtils.close(reactorWs, observerWs);
    }

    @Test
    void addReaction_persistsAndDeliversToConversationMembers() throws Exception {
        User reactor = UserTestUtils.createUser("reactor", "reactor@margin.chat");
        User observer = UserTestUtils.createUser("observer", "observer@margin.chat");
        Conversation conversation = ConversationTestUtils.createDirectConversation(reactor, observer);

        Message message = sendAndPersistMessage(reactor, conversation, "Hello!");

        CompletableFuture<String> received = new CompletableFuture<>();
        observerWs = WebSocketTestUtils.connect(observer,
                WebSocketTestUtils.listenerThatCompletes(received, "RECEIVE_ADD_REACTION"));
        reactorWs = WebSocketTestUtils.connect(reactor);

        String frame = WebSocketFrameTestBuilder.ofType("SEND_ADD_REACTION")
                .recipientId(conversation.getId())
                .payload(WebSocketFrameTestBuilder.object()
                        .put("messageId", message.getId())
                        .put("emoji", "👍"))
                .build();

        reactorWs.sendText(frame, true).get(5, TimeUnit.SECONDS);

        String receivedText = received.get(5, TimeUnit.SECONDS);
        assertTrue(receivedText.contains("RECEIVE_ADD_REACTION"));
        assertTrue(receivedText.contains("👍"));

        Thread.sleep(300);

        List<MessageReaction> reactions = MessageReactionTestUtils.getReactionsForMessage(message.getId());
        assertEquals(1, reactions.size());
        assertEquals("👍", reactions.getFirst().getEmoji());
        assertEquals(reactor.getId(), reactions.getFirst().getUser().getId());
    }

    @Test
    void addReaction_reactorReceivesOwnReactionEcho() throws Exception {
        User reactor = UserTestUtils.createUser("reactor", "reactor@margin.chat");
        User other = UserTestUtils.createUser("other", "other@margin.chat");
        Conversation conversation = ConversationTestUtils.createDirectConversation(reactor, other);

        Message message = sendAndPersistMessage(reactor, conversation, "Hi");

        CompletableFuture<String> echo = new CompletableFuture<>();
        reactorWs = WebSocketTestUtils.connect(reactor,
                WebSocketTestUtils.listenerThatCompletes(echo, "RECEIVE_ADD_REACTION"));

        String frame = WebSocketFrameTestBuilder.ofType("SEND_ADD_REACTION")
                .recipientId(conversation.getId())
                .payload(WebSocketFrameTestBuilder.object()
                        .put("messageId", message.getId())
                        .put("emoji", "❤️"))
                .build();

        reactorWs.sendText(frame, true).get(5, TimeUnit.SECONDS);

        String echoText = echo.get(5, TimeUnit.SECONDS);
        assertTrue(echoText.contains("❤️"));
    }

    @Test
    void removeReaction_deletesFromDbAndNotifiesMembers() throws Exception {
        User reactor = UserTestUtils.createUser("reactor", "reactor@margin.chat");
        User observer = UserTestUtils.createUser("observer", "observer@margin.chat");
        Conversation conversation = ConversationTestUtils.createDirectConversation(reactor, observer);

        Message message = sendAndPersistMessage(reactor, conversation, "Hello!");

        reactorWs = WebSocketTestUtils.connect(reactor);
        addReactionAndWait(reactorWs, conversation.getId(), message.getId(), "👍");

        List<MessageReaction> before = MessageReactionTestUtils.getReactionsForMessage(message.getId());
        assertEquals(1, before.size());

        CompletableFuture<String> received = new CompletableFuture<>();
        observerWs = WebSocketTestUtils.connect(observer,
                WebSocketTestUtils.listenerThatCompletes(received, "RECEIVE_REMOVE_REACTION"));

        String removeFrame = WebSocketFrameTestBuilder.ofType("SEND_REMOVE_REACTION")
                .recipientId(conversation.getId())
                .payload(WebSocketFrameTestBuilder.object()
                        .put("messageId", message.getId())
                        .put("emoji", "👍"))
                .build();

        reactorWs.sendText(removeFrame, true).get(5, TimeUnit.SECONDS);

        String receivedText = received.get(5, TimeUnit.SECONDS);
        assertTrue(receivedText.contains("RECEIVE_REMOVE_REACTION"));
        assertTrue(receivedText.contains("👍"));

        Thread.sleep(300);

        List<MessageReaction> after = MessageReactionTestUtils.getReactionsForMessage(message.getId());
        assertTrue(after.isEmpty());
    }

    @Test
    void addReaction_toNonExistentConversation_doesNotPersist() throws Exception {
        User reactor = UserTestUtils.createUser("reactor", "reactor@margin.chat");

        reactorWs = WebSocketTestUtils.connect(reactor);

        String frame = WebSocketFrameTestBuilder.ofType("SEND_ADD_REACTION")
                .recipientId(99999L)
                .payload(WebSocketFrameTestBuilder.object()
                        .put("messageId", 99999L)
                        .put("emoji", "👍"))
                .build();

        reactorWs.sendText(frame, true).get(5, TimeUnit.SECONDS);
        Thread.sleep(300);

        List<MessageReaction> reactions = MessageReactionTestUtils.getReactionsForMessage(99999L);
        assertTrue(reactions.isEmpty());
    }

    private Message sendAndPersistMessage(User sender, Conversation conversation, String content) throws Exception {
        WebSocket tempWs = WebSocketTestUtils.connect(sender);
        String frame = WebSocketFrameTestBuilder.ofType("SEND_MESSAGE")
                .recipientId(conversation.getId())
                .payload(content)
                .build();
        tempWs.sendText(frame, true).get(5, TimeUnit.SECONDS);
        Thread.sleep(300);
        WebSocketTestUtils.close(tempWs);

        List<Message> messages = MessageTestUtils.getMessages(conversation.getId());
        assertFalse(messages.isEmpty(), "Expected message to be persisted");
        return messages.getFirst();
    }

    private void addReactionAndWait(WebSocket ws, Long conversationId, Long messageId, String emoji) throws Exception {
        String frame = WebSocketFrameTestBuilder.ofType("SEND_ADD_REACTION")
                .recipientId(conversationId)
                .payload(WebSocketFrameTestBuilder.object()
                        .put("messageId", messageId)
                        .put("emoji", emoji))
                .build();
        ws.sendText(frame, true).get(5, TimeUnit.SECONDS);
        Thread.sleep(300);
    }
}
