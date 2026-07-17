package org.margin.server.integrationtest.utils;

import org.margin.server.social.channel.controllers.ChannelController;
import org.margin.server.social.channel.models.ChannelDTO;
import org.margin.server.social.channel.models.ChannelType;
import org.margin.server.users.models.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ChannelTestUtils {

    private static ChannelController channelController;

    @Autowired
    public ChannelTestUtils(ChannelController channelController) {
        ChannelTestUtils.channelController = channelController;
    }

    public static ChannelDTO createChannel(Long spaceId, String name, String description, User user) {
        return createChannel(spaceId, name, description, ChannelType.Communication, user);
    }

    public static ChannelDTO createChannel(Long spaceId, String name, String description,
                                           ChannelType channelType, User user) {
        var dto = new ChannelDTO(null, name, description, spaceId, channelType, spaceId);
        return channelController.createChannel(user, dto);
    }

    public static ChannelDTO createThreadChannel(Long spaceId, String name, User user) {
        return createChannel(spaceId, name, "Test thread channel", ChannelType.Thread, user);
    }

    public static ChannelDTO createChannel(Long spaceId, String name, User user) {
        return createChannel(spaceId, name, "Test channel", user);
    }

    public static List<ChannelDTO> getChannelsForSpace(Long spaceId, User user) {
        return channelController.getChannelsForSpace(user, spaceId);
    }

    public static ChannelDTO updateChannel(Long channelId, Long spaceId, String name, String description, User user) {
        var dto = new ChannelDTO(channelId, name, description, spaceId, ChannelType.Communication, spaceId);
        return channelController.updateChannel(user, dto);
    }

    public static void deleteChannel(Long channelId, User user) {
        channelController.deleteChannel(user, channelId);
    }
}