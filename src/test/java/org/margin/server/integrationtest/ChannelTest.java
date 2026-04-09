package org.margin.server.integrationtest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.margin.server.integrationtest.config.MarginTestRunner;
import org.margin.server.integrationtest.utils.ChannelTestUtils;
import org.margin.server.integrationtest.utils.MarginTestUtils;
import org.margin.server.integrationtest.utils.SpaceTestUtils;
import org.margin.server.integrationtest.utils.UserTestUtils;
import org.margin.server.social.channel.models.ChannelDTO;
import org.margin.server.social.margin.entities.Margin;
import org.margin.server.social.space.models.SpaceRole;
import org.margin.server.social.space.models.dtos.SpaceDTO;
import org.margin.server.users.models.User;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ChannelTest extends MarginTestRunner {

    private User admin;
    private User member;
    private Margin margin;
    private SpaceDTO space;
    private Long spaceId;

    @BeforeEach
    void setUp() {
        admin = UserTestUtils.createUser("admin", "admin@margin.chat");
        member = UserTestUtils.createUser("member", "member@margin.chat");
        margin = MarginTestUtils.createMargin("TestMargin", admin);
        MarginTestUtils.addUserToMargin(margin.getId(), admin, member);
        space = SpaceTestUtils.createSpace("Test Space", margin.getId(), admin);
        spaceId = space.spaceId();
        SpaceTestUtils.addMember(space.spaceId(), member, SpaceRole.MEMBER, admin);
    }

    @Test
    void createChannel() {
        ChannelDTO channel = ChannelTestUtils.createChannel(space.spaceId(), "New Channel", admin);

        assertNotNull(channel);
        assertEquals("New Channel", channel.name());
    }

    @Test
    void nonAdminCannotCreateChannel() {
        assertThrows(ResponseStatusException.class, () ->
                ChannelTestUtils.createChannel(spaceId, "Forbidden Channel", member)
        );
    }

    @Test
    void getChannelsForSpace() {
        ChannelTestUtils.createChannel(spaceId, "Channel A", admin);
        ChannelTestUtils.createChannel(spaceId, "Channel B", admin);

        List<ChannelDTO> channels = ChannelTestUtils.getChannelsForSpace(space.spaceId(), member);

        assertTrue(channels.size() >= 3);
    }

    @Test
    void nonSpaceMemberCannotGetChannels() {
        User outsider = UserTestUtils.createUser("outsider", "outsider@margin.chat");
        MarginTestUtils.addUserToMargin(margin.getId(), admin, outsider);
        assertThrows(ResponseStatusException.class, () ->
                ChannelTestUtils.getChannelsForSpace(spaceId, outsider)
        );
    }

    @Test
    void adminCanUpdateChannel() {
        ChannelDTO channel = ChannelTestUtils.createChannel(spaceId, "Old Name", admin);

        ChannelDTO updated = ChannelTestUtils.updateChannel(
                channel.id(), spaceId, "New Name", "New Desc", admin);

        assertEquals("New Name", updated.name());
        assertEquals("New Desc", updated.description());
    }

    @Test
    void nonAdminCannotUpdateChannel() {
        ChannelDTO channel = ChannelTestUtils.createChannel(spaceId, "Protected", admin);

        Long channelId = channel.id();
        assertThrows(ResponseStatusException.class, () ->
                ChannelTestUtils.updateChannel(channelId, spaceId, "Hacked", "Hacked", member)
        );
    }

    @Test
    void adminCanDeleteChannel() {
        ChannelDTO channel = ChannelTestUtils.createChannel(spaceId, "To Delete", admin);

        ChannelTestUtils.deleteChannel(channel.id(), admin);

        List<ChannelDTO> channels = ChannelTestUtils.getChannelsForSpace(spaceId, admin);
        boolean exists = channels.stream().anyMatch(c -> c.id().equals(channel.id()));
        assertFalse(exists);
    }

    @Test
    void nonAdminCannotDeleteChannel() {
        ChannelDTO channel = ChannelTestUtils.createChannel(spaceId, "No Delete", admin);

        Long channelId = channel.id();
        assertThrows(ResponseStatusException.class, () ->
                ChannelTestUtils.deleteChannel(channelId, member)
        );
    }

    @Test
    void spaceCreationCreatesDefaultChannel() {
        List<ChannelDTO> channels = ChannelTestUtils.getChannelsForSpace(spaceId, admin);

        assertFalse(channels.isEmpty());
        assertEquals("General Chat", channels.getFirst().name());
    }

    @Test
    void newSpaceMemberGetsAccessToExistingChannels() {
        ChannelTestUtils.createChannel(spaceId, "Pre-existing", admin);

        List<ChannelDTO> memberChannels = ChannelTestUtils.getChannelsForSpace(spaceId, member);

        boolean hasPreExisting = memberChannels.stream()
                .anyMatch(c -> c.name().equals("Pre-existing"));
        assertTrue(hasPreExisting);
    }
}