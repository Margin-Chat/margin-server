package org.margin.server.social.margin.models.dtos;

public record RemoveMarginMemberRequest(Long marginId, Long userIdToRemove) {
}