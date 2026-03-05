package org.margin.server.social.margin.models.dtos;

import org.margin.server.social.margin.models.MarginRole;

public record AddMarginMemberRequest(Long marginId, Long userId, MarginRole role) {}
