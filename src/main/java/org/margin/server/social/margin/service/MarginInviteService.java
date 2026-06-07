package org.margin.server.social.margin.service;

import org.margin.server.notifications.events.UserInvitedToMarginEvent;
import org.margin.server.social.margin.entities.Margin;
import org.margin.server.social.margin.entities.MarginInvite;
import org.margin.server.social.margin.models.MarginRole;
import org.margin.server.social.margin.repositories.MarginInviteRepository;
import org.margin.server.social.margin.repositories.MarginMemberRepository;
import org.margin.server.social.space.services.SpacesService;
import org.margin.server.users.models.User;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class MarginInviteService {

    private final MarginInviteRepository marginInviteRepository;
    private final MarginMemberRepository marginMemberRepository;
    private final MarginService marginService;
    private final SpacesService spacesService;
    private final ApplicationEventPublisher eventPublisher;

    public MarginInviteService(MarginInviteRepository marginInviteRepository,
                               MarginMemberRepository marginMemberRepository,
                               MarginService marginService,
                               SpacesService spacesService,
                               ApplicationEventPublisher eventPublisher) {
        this.marginInviteRepository = marginInviteRepository;
        this.marginMemberRepository = marginMemberRepository;
        this.marginService = marginService;
        this.spacesService = spacesService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public MarginInvite createLinkInvite(Margin margin, Integer maxUses, User invitedBy) {
        MarginInvite marginInvite = new MarginInvite();
        marginInvite.setMargin(margin);
        marginInvite.setInvitedBy(invitedBy);
        marginInvite.setCreatedAt(Instant.now());
        marginInvite.setExpiresAt(Instant.now().plus(Duration.ofDays(7)));
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
        marginInvite.setCreatedAt(Instant.now());
        marginInvite.setExpiresAt(Instant.now().plus(Duration.ofDays(7)));
        marginInvite.setMaxUses(1);
        marginInvite.setType(MarginInvite.InviteType.DIRECT);

        MarginInvite saved = marginInviteRepository.save(marginInvite);

        eventPublisher.publishEvent(
                new UserInvitedToMarginEvent(targetUser, invitedBy, saved.getId(), saved.getInviteCode(), margin.getId()));

        return saved;
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
                false
        );

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
                false
        );

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
                Instant.now()
        );
    }

    public boolean hasPendingInviteForMargin(Long marginId, Long userId) {
        return marginInviteRepository.existsPendingInvite(
                marginId,
                userId,
                MarginInvite.InviteStatus.PENDING
        );
    }

    public MarginInvite getById(Long id) {
        return marginInviteRepository.findById(id).orElseThrow();
    }
}
