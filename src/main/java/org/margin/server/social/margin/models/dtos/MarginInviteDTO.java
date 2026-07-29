package org.margin.server.social.margin.models.dtos;

import org.margin.server.social.margin.entities.MarginInvite;

import java.time.Instant;

public record MarginInviteDTO(
        Long id,
        String inviteCode,
        String marginName,
        String invitedByUser,
        String invitedUser,
        String status,
        String type,
        Instant createdAt,
        Instant expiresAt,
        Integer maxUses,
        int currentUses
) {
    public static MarginInviteDTO from(MarginInvite invite, String invitedByName, String invitedUserName) {
        return new MarginInviteDTO(
                invite.getId(),
                invite.getInviteCode(),
                invite.getMargin().getName(),
                invitedByName,
                invitedUserName,
                invite.getStatus().name(),
                invite.getType().name(),
                invite.getCreatedAt(),
                invite.getExpiresAt(),
                invite.getMaxUses(),
                invite.getCurrentUses()
        );
    }
}