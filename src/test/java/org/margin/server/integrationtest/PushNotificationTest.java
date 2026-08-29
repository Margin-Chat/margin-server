package org.margin.server.integrationtest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.margin.server.integrationtest.config.CapturingPushSenderConfig;
import org.margin.server.integrationtest.config.CapturingPushSenderConfig.CapturedPush;
import org.margin.server.integrationtest.config.MarginTestRunner;
import org.margin.server.integrationtest.utils.ConversationTestUtils;
import org.margin.server.integrationtest.utils.UserTestUtils;
import org.margin.server.notifications.push.PushPlatform;
import org.margin.server.notifications.push.PushTokenRepository;
import org.margin.server.notifications.push.PushTokenService;
import org.margin.server.social.announcements.models.dtos.CreateAnnouncementRequest;
import org.margin.server.social.announcements.services.AnnouncementService;
import org.margin.server.social.conversation.models.Conversation;
import org.margin.server.social.margin.entities.Margin;
import org.margin.server.integrationtest.utils.MarginTestUtils;
import org.margin.server.social.messages.services.MessageService;
import org.margin.server.users.models.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PushNotificationTest extends MarginTestRunner {

    @Autowired
    private PushTokenService pushTokenService;
    @Autowired
    private PushTokenRepository pushTokenRepository;
    @Autowired
    private MessageService messageService;
    @Autowired
    private AnnouncementService announcementService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private User alice;
    private User bob;

    @BeforeEach
    void setUp() {
        CapturingPushSenderConfig.reset();
        alice = UserTestUtils.createUser("alice", "alice@margin.chat");
        bob = UserTestUtils.createUser("bob", "bob@margin.chat");
    }

    @Test
    void registeringSameTokenForAnotherUser_movesItToThatUser() {
        pushTokenService.register(alice.getId(), PushPlatform.ANDROID, "device-token-1");
        pushTokenService.register(bob.getId(), PushPlatform.ANDROID, "device-token-1");

        var tokens = pushTokenRepository.findByToken("device-token-1");
        assertTrue(tokens.isPresent());
        assertEquals(bob.getId(), tokens.get().getUserId());
        assertEquals(1, pushTokenRepository.count());
    }

    @Test
    void unregister_ignoresTokensOwnedByOtherUsers() {
        pushTokenService.register(alice.getId(), PushPlatform.ANDROID, "device-token-2");

        pushTokenService.unregister(bob.getId(), "device-token-2");
        assertTrue(pushTokenRepository.findByToken("device-token-2").isPresent());

        pushTokenService.unregister(alice.getId(), "device-token-2");
        assertFalse(pushTokenRepository.findByToken("device-token-2").isPresent());
    }

    @Test
    void offlineRecipient_receivesPush_senderDoesNot() {
        pushTokenService.register(alice.getId(), PushPlatform.ANDROID, "alice-device");
        pushTokenService.register(bob.getId(), PushPlatform.ANDROID, "bob-device");
        Conversation conv = ConversationTestUtils.createDirectConversation(alice, bob);

        messageService.sendMessage(alice.getId(), "hello bob", conv.getId(), List.of());

        CapturedPush push = awaitSinglePush();
        assertEquals("bob-device", push.token());
        assertEquals("alice", push.message().title());
        assertEquals("hello bob", push.message().body());
        assertEquals(String.valueOf(conv.getId()), push.message().data().get("conversationId"));
        assertEquals("message", push.message().data().get("type"));
    }

    @Test
    void encryptedLookingContent_getsGenericBody() {
        pushTokenService.register(bob.getId(), PushPlatform.ANDROID, "bob-device");
        Conversation conv = ConversationTestUtils.createDirectConversation(alice, bob);

        String ciphertext = Base64.getEncoder().encodeToString(
                "{\"iv\":\"abc\",\"message\":\"zzz\",\"recipientKey\":\"k\",\"senderKey\":\"k\"}".getBytes());
        messageService.sendMessage(alice.getId(), ciphertext, conv.getId(), List.of());

        CapturedPush push = awaitSinglePush();
        assertEquals("New message", push.message().body());
    }

    @Test
    void invalidToken_isDeletedFromRegistry() {
        pushTokenService.register(bob.getId(), PushPlatform.ANDROID, "bob-dead-device");
        Conversation conv = ConversationTestUtils.createDirectConversation(alice, bob);
        CapturingPushSenderConfig.failNextSendAsInvalidToken = true;

        messageService.sendMessage(alice.getId(), "are you there?", conv.getId(), List.of());

        awaitCondition(() -> pushTokenRepository.findByToken("bob-dead-device").isEmpty());
        assertEquals(0, CapturingPushSenderConfig.captured.size());
    }

    @Test
    void conversationInvite_pushesRecipient() {
        pushTokenService.register(bob.getId(), PushPlatform.ANDROID, "bob-device");

        ConversationTestUtils.sendInvite(alice, bob.getEmail());

        CapturedPush push = awaitSinglePush();
        assertEquals("bob-device", push.token());
        assertEquals("alice", push.message().title());
        assertEquals("invite", push.message().data().get("type"));
    }

    @Test
    void announcement_pushesMembersButNotAuthor() {
        Margin margin = MarginTestUtils.createMargin("PushMargin", alice);
        MarginTestUtils.addUserToMargin(margin.getId(), alice, bob);
        pushTokenService.register(alice.getId(), PushPlatform.ANDROID, "alice-device");
        pushTokenService.register(bob.getId(), PushPlatform.ANDROID, "bob-device");

        new TransactionTemplate(transactionManager).executeWithoutResult(tx ->
                announcementService.createAnnouncement(
                        new CreateAnnouncementRequest(margin.getId(), "Big news", "We shipped push"), alice.getId()));

        CapturedPush push = awaitSinglePush();
        assertEquals("bob-device", push.token());
        assertEquals("announcement", push.message().data().get("type"));
        assertEquals(String.valueOf(margin.getId()), push.message().data().get("marginId"));
    }

    private CapturedPush awaitSinglePush() {
        awaitCondition(() -> !CapturingPushSenderConfig.captured.isEmpty());
        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        assertEquals(1, CapturingPushSenderConfig.captured.size(),
                "expected exactly one push, got: " + CapturingPushSenderConfig.captured);
        return CapturingPushSenderConfig.captured.get(0);
    }

    private void awaitCondition(java.util.function.BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        assertTrue(condition.getAsBoolean(), "condition not met within 5s");
    }
}
