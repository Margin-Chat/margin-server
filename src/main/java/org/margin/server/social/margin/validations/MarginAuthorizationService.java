package org.margin.server.social.margin.validations;

import org.margin.server.social.margin.entities.MarginMember;
import org.margin.server.social.margin.models.MarginRole;
import org.margin.server.social.margin.service.MarginService;
import org.margin.server.social.space.models.SpaceMember;
import org.margin.server.social.space.models.SpaceRole;
import org.margin.server.shared.authorization.MarginAccessChecker;
import org.margin.server.shared.membership.MarginMembershipLookup;
import org.margin.server.social.margin.repositories.MarginMemberRepository;
import org.margin.server.social.space.repositories.SpaceMemberRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.EnumSet;
import java.util.Set;

@Service
public class MarginAuthorizationService implements MarginAccessChecker, MarginMembershipLookup {

    private static final Set<MarginRole> MARGIN_ADMIN_ROLES = EnumSet.of(MarginRole.ADMIN, MarginRole.OWNER);
    private static final Set<SpaceRole> SPACE_ADMIN_ROLES = EnumSet.of(SpaceRole.ADMIN);

    private final MarginService marginService;
    private final SpaceMemberRepository spaceMemberRepository;
    private final MarginMemberRepository marginMemberRepository;

    public MarginAuthorizationService(MarginService marginService,
                                      SpaceMemberRepository spaceMemberRepository,
                                      MarginMemberRepository marginMemberRepository) {
        this.marginService = marginService;
        this.spaceMemberRepository = spaceMemberRepository;
        this.marginMemberRepository = marginMemberRepository;
    }

    @Override
    public void requireMarginMember(Long userId, Long marginId) {
        marginService.findMember(userId, marginId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "User is not a member of this margin"));
    }

    @Override
    public void requireMarginAdmin(Long userId, Long marginId) {
        MarginMember member = marginService.findMember(userId, marginId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "User is not a member of this margin"));

        if (!MARGIN_ADMIN_ROLES.contains(member.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Margin admin or owner role required");
        }
    }

    @Override
    public void requireMarginOwner(Long userId, Long marginId) {
        MarginMember member = marginService.findMember(userId, marginId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Margin owner role required"));

        if (!member.getRole().equals(MarginRole.OWNER)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Margin owner role required");
        }
    }

    @Override
    public void requireSpaceAdmin(Long userId, Long spaceId) {
        SpaceMember spaceMember = spaceMemberRepository
                .findByUserIdAndSpaceId(userId, spaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "User is not a member of this space"));

        if (!SPACE_ADMIN_ROLES.contains(spaceMember.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Space admin role required");
        }
    }

    @Override
    public void requireChannelMember(Long userId, Long channelId) {
        if (!spaceMemberRepository.existsByChannelIdAndUserId(userId, channelId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User does not have access to the channel");
        }
    }

    @Override
    public void requireSpaceMember(Long id, Long spaceId) {
        if (!spaceMemberRepository.existsSpaceMemberByUserAndSpace(id, spaceId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User does not have access to the space");
        }
    }

    @Override
    public boolean hasAnyMembership(Long userId) {
        return marginMemberRepository.existsByUserId(userId);
    }
}
