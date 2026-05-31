package org.margin.server.unittest.utils;

import org.margin.server.social.channel.entities.Channel;
import org.margin.server.social.margin.entities.Margin;
import org.margin.server.social.space.models.Space;

import static org.margin.server.unittest.utils.MarginTestUtils.createMargin;
import static org.margin.server.unittest.utils.SpaceTestUtils.createSpace;

public class ChannelTestUtils {

    public static Channel createChannel(Long id) {
        Channel channel = new Channel();
        channel.setId(id);
        return channel;
    }

    public static Channel createChannelWithSpaceAndMargin(Long channelId, Long spaceId, Long marginId) {
        Margin margin = createMargin(marginId, null);
        Space space = createSpace(spaceId, margin);
        Channel channel = createChannel(channelId);
        channel.setSpace(space);
        return channel;
    }
}
