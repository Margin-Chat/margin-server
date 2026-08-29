package org.margin.server.unittest.utils;

import org.margin.server.social.margin.entities.Margin;
import org.margin.server.social.margin.entities.MarginInvite;
import org.margin.server.users.models.User;

import java.time.Duration;
import java.time.Instant;

public class MarginInviteTestUtils {

    public static MarginInvite createLinkInvite(Margin margin, User invitedBy) {
        MarginInvite invite = new MarginInvite();
        invite.setId(1L);
        invite.setMargin(margin);
        invite.setInvitedByUserId(invitedBy.getId());
        invite.setType(MarginInvite.InviteType.LINK);
        invite.setStatus(MarginInvite.InviteStatus.PENDING);
        invite.setInviteCode("abc-123");
        invite.setExpiresAt(Instant.now().plus(Duration.ofDays(7)));
        return invite;
    }

    public static MarginInvite createDirectInvite(Margin margin, User invitedBy, User invitedUser) {
        MarginInvite invite = new MarginInvite();
        invite.setId(1L);
        invite.setMargin(margin);
        invite.setInvitedByUserId(invitedBy.getId());
        invite.setInvitedUserId(invitedUser.getId());
        invite.setType(MarginInvite.InviteType.DIRECT);
        invite.setStatus(MarginInvite.InviteStatus.PENDING);
        invite.setMaxUses(1);
        invite.setInviteCode("direct-123");
        invite.setExpiresAt(Instant.now().plus(Duration.ofDays(7)));
        return invite;
    }
}
