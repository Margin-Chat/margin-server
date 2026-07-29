package org.margin.server.social.space.services;

import org.margin.server.social.channel.services.ChannelService;
import org.margin.server.social.conversation.services.ConversationService;
import org.margin.server.social.margin.repositories.MarginMemberRepository;
import org.margin.server.social.space.exceptions.SpaceNotFoundException;
import org.margin.server.social.space.exceptions.UserNotInMargin;
import org.margin.server.social.space.models.Space;
import org.margin.server.social.space.models.SpaceMember;
import org.margin.server.social.space.models.SpaceRole;
import org.margin.server.social.space.models.dtos.SpaceDTO;
import org.margin.server.social.space.models.dtos.SpaceMemberDTO;
import org.margin.server.social.space.repositories.SpaceMemberRepository;
import org.margin.server.social.space.repositories.SpacesRepository;
import org.margin.server.users.exceptions.UserNotFoundException;
import org.margin.server.users.models.User;
import org.margin.server.social.space.events.UserJoinedSpaceEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class SpacesActions {

    private final SpaceMemberRepository spaceMemberRepository;
    private final MarginMemberRepository marginMemberRepository;
    private final SpacesCreationService spacesCreationService;
    private final ConversationService conversationService;
    private final ApplicationEventPublisher eventPublisher;
    private final SpacesRepository spacesRepository;
    private final ChannelService channelService;

    public SpacesActions(SpaceMemberRepository spaceMemberRepository,
                         MarginMemberRepository marginMemberRepository,
                         SpacesCreationService spacesCreationService,
                         ConversationService conversationService,
                         ApplicationEventPublisher eventPublisher,
                         SpacesRepository spacesRepository,
                         ChannelService channelService) {
        this.spaceMemberRepository = spaceMemberRepository;
        this.marginMemberRepository = marginMemberRepository;
        this.spacesCreationService = spacesCreationService;
        this.conversationService = conversationService;
        this.eventPublisher = eventPublisher;
        this.spacesRepository = spacesRepository;
        this.channelService = channelService;
    }

    @Transactional
    public SpaceMember addUserToSpace(Long userId, Space space, SpaceRole role) {
        if (!marginMemberRepository.existsByUserIdAndMarginId(userId, space.getMargin().getId())) {
            throw new UserNotInMargin(userId);
        }

        if (spaceMemberRepository.existsSpaceMemberByUserAndSpace(userId, space.getId())) {
            throw new DuplicateKeyException("Can't add duplicate space member");
        }

        SpaceMember spaceMember = spacesCreationService.createMember(userId, space, role);
        space.getChannels().forEach(c -> conversationService.createNewConversationMember(c.getConversation(), userId));

        eventPublisher.publishEvent(new UserJoinedSpaceEvent(
                space.getMembers().stream().map(m -> m.getUserId()).toList(),
                userId,
                space.getId()));

        return spaceMember;
    }

    public Space updateSpace(SpaceDTO spaceDTO) {
        Space space = spacesRepository.findById(spaceDTO.spaceId())
                .orElseThrow(() -> new SpaceNotFoundException(spaceDTO.spaceId()));

        if (spaceDTO.spaceName() != null) {
            space.setName(spaceDTO.spaceName());
        }
        if (spaceDTO.spaceDescription() != null) {
            space.setDescription(spaceDTO.spaceDescription());
        }
        if (spaceDTO.visibility() != null) {
            space.setVisibility(spaceDTO.visibility());
        }
        return space;
    }

    @Transactional
    public void deleteSpace(Long spaceId) {
        Space space = spacesRepository.findById(spaceId)
                .orElseThrow(() -> new SpaceNotFoundException(spaceId));

        Instant now = Instant.now();

        space.getChannels().forEach(channel -> {
            if (channel.getConversation() != null) {
                channel.getConversation().setDeletedAt(now);
            }
            channel.setDeletedAt(now);
        });

        spaceMemberRepository.deleteAll(spaceMemberRepository.findSpaceMemberBySpace(space));
        space.setDeletedAt(now);
        spacesRepository.save(space);
    }

    public SpaceMember prepareRoleUpdate(Space space, SpaceMemberDTO dto) {
        SpaceMember spaceMember = spaceMemberRepository
                .findByUserIdAndSpaceId(dto.user().id(), space.getId())
                .orElseThrow(UserNotFoundException::new);

        spaceMember.setRole(dto.role());
        return spaceMember;
    }

    @Transactional
    public void removeUserFromSpaces(Long userId, List<Space> spaces) {
        for (Space space : spaces) {
            channelService.removeUserFromChannels(userId, space.getChannels());
            List<SpaceMember> members = space.getMembers();
            members.removeIf(m -> m.getUserId().equals(userId));
            spaceMemberRepository.saveAll(members);
        }
    }
}
