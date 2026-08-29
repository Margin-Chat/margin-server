package org.margin.server.sfu.models;

import org.margin.server.users.models.dtos.UserDTO;

public record ChannelCallInvitePayload(Long channelId, String channelName, UserDTO fromUser) {}
