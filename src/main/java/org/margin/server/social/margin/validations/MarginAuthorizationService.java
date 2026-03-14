package org.margin.server.social.margin.validations;

import org.margin.server.social.channel.channel.Channel;
import org.margin.server.social.channel.repositories.ChannelRepository;
import org.margin.server.social.margin.service.MarginService;
import org.margin.server.social.margin.models.MarginRole;
import org.margin.server.social.margin.models.MarginMember;
import org.margin.server.social.space.models.SpaceMember;
import org.margin.server.social.space.models.SpaceRole;
import org.margin.server.social.space.repositories.SpaceMemberRepository;
import org.margin.server.social.space.repositories.SpacesRepository;
import org.margin.server.users.models.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

@Service
public class MarginAuthorizationService {

    private static final Set<MarginRole> MARGIN_ADMIN_ROLES = EnumSet.of(MarginRole.ADMIN, MarginRole.OWNER);
    private static final Set<SpaceRole> SPACE_ADMIN_ROLES = EnumSet.of(SpaceRole.ADMIN);

    private final MarginService marginService;
    private final SpaceMemberRepository spaceMemberRepository;
    private final ChannelRepository channelRepository;
    private final SpacesRepository spacesRepository;

    public MarginAuthorizationService(MarginService marginService,
                                      SpaceMemberRepository spaceMemberRepository, ChannelRepository channelRepository, SpacesRepository spacesRepository) {
        this.marginService = marginService;
        this.spaceMemberRepository = spaceMemberRepository;
        this.channelRepository = channelRepository;
        this.spacesRepository = spacesRepository;
    }

    public void requireMarginMember(Long userId, Long marginId) {
        marginService.findMember(userId, marginId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "User is not a member of this margin"));
    }

    public void requireMarginAdmin(Long userId, Long marginId) {
        MarginMember member = marginService.findMember(userId, marginId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "User is not a member of this margin"));

        if (!MARGIN_ADMIN_ROLES.contains(member.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Margin admin or owner role required");
        }
    }

    public void requireSpaceAdmin(Long userId, Long spaceId, Long marginId) {
        boolean isMarginAdmin = marginService.findMember(userId, marginId)
                .map(m -> MARGIN_ADMIN_ROLES.contains(m.getRole()))
                .orElse(false);

        if (isMarginAdmin) return;

        SpaceMember spaceMember = spaceMemberRepository
                .findByUser_IdAndSpace_Id(userId, spaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "User is not a member of this space"));

        if (!SPACE_ADMIN_ROLES.contains(spaceMember.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Space admin role required");
        }
    }

    public void requireChannelMembership(Long userId, Long channelId) {
        if (!spaceMemberRepository.existsByChannelIdAndUserId(userId, channelId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User does not have access to the channel");
        }
    }
}