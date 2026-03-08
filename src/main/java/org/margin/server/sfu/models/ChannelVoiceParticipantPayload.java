package org.margin.server.sfu.models;

import org.margin.server.users.models.dtos.UserDTO;

public record ChannelVoiceParticipantPayload(Long channelId, UserDTO user) {}
