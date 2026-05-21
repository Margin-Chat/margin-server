package org.margin.server.integrationtest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.margin.server.integrationtest.config.MarginTestRunner;
import org.margin.server.integrationtest.utils.ChannelTestUtils;
import org.margin.server.integrationtest.utils.MarginTestUtils;
import org.margin.server.integrationtest.utils.SubscriptionTestUtils;
import org.margin.server.integrationtest.utils.UserTestUtils;
import org.margin.server.social.channel.models.ChannelDTO;
import org.margin.server.social.margin.entities.Margin;
import org.margin.server.social.margin.models.dtos.MarginDTO;
import org.margin.server.subscriptions.entities.Subscription;
import org.margin.server.subscriptions.exceptions.SubscriptionLimitExceededException;
import org.margin.server.subscriptions.models.SubscriptionStatus;
import org.margin.server.subscriptions.models.SubscriptionTier;
import org.margin.server.users.models.User;

import static org.junit.jupiter.api.Assertions.*;

class SubscriptionTest extends MarginTestRunner {
    private User admin;
    private Margin margin;

    @BeforeEach
    void setUp() {
        admin = UserTestUtils.createUser("subadmin", "subadmin@margin.chat");
        margin = MarginTestUtils.createMargin("SubMargin", admin);
    }

    @Test
    void creatingMarginCreatesFreeSubscription() {
        Subscription subscription = SubscriptionTestUtils.getForMargin(margin);

        assertEquals(SubscriptionTier.FREE, subscription.getTier());
        assertEquals(SubscriptionStatus.ACTIVE, subscription.getStatus());
        assertEquals(margin.getId(), subscription.getMargin().getId());
    }

    @Test
    void freeTierSubscriptionHasDefaultLimits() {
        Subscription subscription = SubscriptionTestUtils.getForMargin(margin);

        assertEquals(10, subscription.getLimits().getMaxMembers());
        assertEquals(0, subscription.getLimits().getMaxStorageGb());
        assertEquals(5, subscription.getLimits().getMaxCallParticipants());
    }

    @Test
    void freeTierHasNoBillingFieldsSet() {
        Subscription subscription = SubscriptionTestUtils.getForMargin(margin);

        assertNull(subscription.getSubscriptionId());
        assertNull(subscription.getTrialEndsAt());
        assertNull(subscription.getCurrentPeriodStart());
        assertNull(subscription.getCurrentPeriodEnd());
    }

    @Test
    void getMaxCallParticipantsReturnsFreeTierDefault() {
        Long channelId = firstChannelId();

        int max = SubscriptionTestUtils.getMaxCallParticipantsForChannel(channelId);

        assertEquals(5, max);
    }

    @Test
    void getMaxCallParticipantsReflectsOverriddenLimits() {
        SubscriptionTestUtils.overrideLimits(margin, 25, 5, 50);
        Long channelId = firstChannelId();

        int max = SubscriptionTestUtils.getMaxCallParticipantsForChannel(channelId);

        assertEquals(50, max);
    }

    @Test
    void addingMemberAtMemberCapThrows() {
        SubscriptionTestUtils.overrideLimits(margin, 1, 5, 10);
        User extra = UserTestUtils.createUser("extra", "extra@margin.chat");

        assertThrows(SubscriptionLimitExceededException.class, () ->
                MarginTestUtils.addUserToMargin(margin.getId(), admin, extra));

        assertEquals(1, MarginTestUtils.getMembersFromMargin(margin.getId()).size());
    }

    @Test
    void addingMemberBelowMemberCapSucceeds() {
        SubscriptionTestUtils.overrideLimits(margin, 5, 5, 10);
        User extra = UserTestUtils.createUser("extra", "extra@margin.chat");

        assertDoesNotThrow(() ->
                MarginTestUtils.addUserToMargin(margin.getId(), admin, extra));

        assertEquals(2, MarginTestUtils.getMembersFromMargin(margin.getId()).size());
    }

    private Long firstChannelId() {
        MarginDTO dto = MarginTestUtils.getMarginDto(margin.getId(), admin);
        Long spaceId = dto.spaces().getFirst().spaceId();
        ChannelDTO channel = ChannelTestUtils.getChannelsForSpace(spaceId, admin).getFirst();
        return channel.id();
    }
}