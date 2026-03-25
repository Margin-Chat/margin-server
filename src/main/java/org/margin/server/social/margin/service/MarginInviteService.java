package org.margin.server.social.margin.service;

import lombok.RequiredArgsConstructor;
import org.margin.server.notifications.NotificationType;
import org.margin.server.notifications.services.NotificationService;
import org.margin.server.social.margin.entities.Margin;
import org.margin.server.social.margin.entities.MarginInvite;
import org.margin.server.social.margin.models.MarginRole;
import org.margin.server.social.margin.repositories.MarginInviteRepository;
import org.margin.server.social.margin.repositories.MarginMemberRepository;
import org.margin.server.social.space.services.SpacesService;
import org.margin.server.users.models.User;
import org.margin.server.websocket.services.WebSocketDeliveryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MarginInviteService {

    private final MarginInviteRepository marginInviteRepository;
    private final WebSocketDeliveryService webSocketDeliveryService;
    private final MarginMemberRepository marginMemberRepository;
    private final MarginService marginService;
    private final SpacesService spacesService;
    private final NotificationService notificationService;

    @Transactional
    public MarginInvite createLinkInvite(Margin margin, Integer maxUses, User invitedBy) {
        MarginInvite marginInvite = new MarginInvite();
        marginInvite.setMargin(margin);
        marginInvite.setInvitedBy(invitedBy);
        marginInvite.setCreatedAt(LocalDateTime.now());
        marginInvite.setExpiresAt(LocalDateTime.now().plusDays(7));
        marginInvite.setMaxUses(maxUses);
        marginInvite.setType(MarginInvite.InviteType.LINK);
        return marginInviteRepository.save(marginInvite);
    }

    @Transactional
    public MarginInvite createDirectInvite(Margin margin, User targetUser, User invitedBy) {
        if (marginMemberRepository.existsByMarginIdAndUserId(margin.getId(), targetUser.getId())) {
            throw new IllegalStateException("User is already a member of this margin");
        }

        if (marginInviteRepository.existsPendingInvite(margin.getId(), targetUser.getId(), MarginInvite.InviteStatus.PENDING)) {
            throw new IllegalStateException("User already has a pending invite to this margin");
        }

        MarginInvite marginInvite = new MarginInvite();
        marginInvite.setMargin(margin);
        marginInvite.setInvitedBy(invitedBy);
        marginInvite.setInvitedUser(targetUser);
        marginInvite.setCreatedAt(LocalDateTime.now());
        marginInvite.setExpiresAt(LocalDateTime.now().plusDays(7));
        marginInvite.setMaxUses(1);
        marginInvite.setType(MarginInvite.InviteType.DIRECT);
        MarginInvite savedInvited = marginInviteRepository.save(marginInvite);

        webSocketDeliveryService.notifyMarginInvite(savedInvited.getInviteCode(), targetUser.getId());
        notificationService.createForUsers(
                Collections.singletonList(targetUser),
                invitedBy,
                NotificationType.INVITED_TO_MARGIN,
                marginInvite.getId(), margin.getId());
        return savedInvited;
    }

    public Optional<MarginInvite> findInviteDetails(String code) {
        return marginInviteRepository.findByInviteCode(code);
    }

    @Transactional
    public Margin acceptLinkInvite(MarginInvite invite, User user) {
        marginService.addUserToMargin(
                invite.getMargin().getId(),
                user.getId(),
                MarginRole.MEMBER,
                invite.getInvitedBy(),
                false);

        spacesService.addUsersToDefaultSpacesForMargin(invite.getMargin().getId(), user);

        invite.setCurrentUses(invite.getCurrentUses() + 1);
        marginInviteRepository.save(invite);

        return marginService.getById(invite.getMargin().getId());
    }

    @Transactional
    public Margin acceptDirectInvite(MarginInvite invite, User user) {
        marginService.addUserToMargin(
                invite.getMargin().getId(),
                user.getId(),
                MarginRole.MEMBER,
                invite.getInvitedBy(),
                false);

        spacesService.addUsersToDefaultSpacesForMargin(invite.getMargin().getId(), user);

        invite.setStatus(MarginInvite.InviteStatus.ACCEPTED);
        marginInviteRepository.save(invite);

        return marginService.getById(invite.getMargin().getId());
    }

    public void declineDirectInvite(MarginInvite invite) {
        invite.setStatus(MarginInvite.InviteStatus.DECLINED);
        marginInviteRepository.save(invite);
    }

    public List<MarginInvite> getPendingInvites(User user) {
        return marginInviteRepository.findInvitesForUserByStatus(
                user.getId(),
                MarginInvite.InviteStatus.PENDING,
                LocalDateTime.now());
    }

    public boolean hasPendingInviteForMargin(Long marginId, Long userId) {
        return marginInviteRepository.existsPendingInvite(
                marginId, userId, MarginInvite.InviteStatus.PENDING);
    }

    public MarginInvite getById(Long id) {
        return marginInviteRepository.findById(id).orElseThrow();
    }
}