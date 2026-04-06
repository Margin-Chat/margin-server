package org.margin.server.social.space.services;

import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.margin.server.social.channel.entities.Channel;
import org.margin.server.social.channel.repositories.ChannelRepository;
import org.margin.server.social.channel.services.ChannelService;
import org.margin.server.social.conversation.services.ConversationService;
import org.margin.server.social.margin.entities.Margin;
import org.margin.server.social.margin.repositories.MarginMemberRepository;
import org.margin.server.social.space.exceptions.SpaceNotFoundException;
import org.margin.server.social.space.exceptions.UserNotInMargin;
import org.margin.server.social.space.models.Space;
import org.margin.server.social.space.models.SpaceMember;
import org.margin.server.social.space.models.SpaceRole;
import org.margin.server.social.space.models.dtos.CreateSpaceDTO;
import org.margin.server.social.space.models.dtos.SpaceDTO;
import org.margin.server.social.space.models.dtos.SpaceMemberDTO;
import org.margin.server.social.space.repositories.SpaceMemberRepository;
import org.margin.server.social.space.repositories.SpacesRepository;
import org.margin.server.users.exceptions.UserNotFoundException;
import org.margin.server.users.models.User;
import org.margin.server.websocket.services.WebSocketDeliveryService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class SpacesService {
    private final SpacesRepository spacesRepository;
    private final SpaceMemberRepository spaceMemberRepository;
    private final ChannelRepository channelRepository;
    private final ChannelService channelService;
    private final MarginMemberRepository marginMemberRepository;
    private final WebSocketDeliveryService webSocketDeliveryService;
    private final SpacesCreationService spacesCreationService;
    private final ConversationService conversationService;

    public SpacesService(SpacesRepository spacesRepository,
                         SpaceMemberRepository spaceMemberRepository,
                         ChannelRepository channelRepository,
                         ChannelService channelService,
                         MarginMemberRepository marginMemberRepository,
                         WebSocketDeliveryService webSocketDeliveryService,
                         SpacesCreationService spacesCreationService,
                         ConversationService conversationService) {
        this.spacesRepository = spacesRepository;
        this.spaceMemberRepository = spaceMemberRepository;
        this.channelRepository = channelRepository;
        this.channelService = channelService;
        this.marginMemberRepository = marginMemberRepository;
        this.webSocketDeliveryService = webSocketDeliveryService;
        this.spacesCreationService = spacesCreationService;
        this.conversationService = conversationService;
    }

    public List<Space> getSpaces() {
        return spacesRepository.getSpaces()
                .stream()
                .toList();
    }

    @Transactional
    public List<Space> getSpacesForUserInMargin(User user, Long marginId) {
        return spacesRepository.findByUserAndMargin(user.getId(), marginId);
    }

    public List<Space> getSpacesForMargin(Long marginId) {
        return spacesRepository.findByMargin_Id(marginId);
    }

    @Transactional
    public Space createNewSpace(CreateSpaceDTO dto, User user, Margin margin, boolean isDefault) {
        Optional<Space> spaceByName = spacesRepository.getSpaceByName(dto.name(), margin.getId());

        if (spaceByName.isPresent()) {
            throw new DuplicateKeyException("Space name already exists");
        }

        Space space = spacesCreationService.create(dto.name(), dto.description(), dto.visibility(), margin, isDefault);
        channelService.createNewChannel(space, "General Chat", "A channel for general conversation");
        addNewUserToSpace(user, space, SpaceRole.ADMIN);

        return space;
    }

    public @NotNull Space getById(Long id) {
        return spacesRepository.findById(id).orElseThrow(() -> new RuntimeException("Space not found"));
    }

    @Transactional
    public List<Space> getDefaultSpacesForMargin(Long marginId) {
        return spacesRepository.findDefaultSpacesByMarginId(marginId);
    }

    @Transactional
    public void addUsersToDefaultSpacesForMargin(Long marginId, User user) {
        List<Space> defaultSpacesForMargin = getDefaultSpacesForMargin(marginId);

        for (Space space : defaultSpacesForMargin) {
            addNewUserToSpace(user, space, SpaceRole.MEMBER);
        }
    }

    public SpaceMember addNewUserToSpace(User user, Space space, SpaceRole role) {
        if (!marginMemberRepository
                .existsByUser_IdAndMargin_Id(user.getId(), space.getMargin().getId())) {
            throw new UserNotInMargin(user.getId());
        }

        if (spaceMemberRepository.existsSpaceMemberByUserAndSpace(user.getId(), space.getId())) {
            throw new DuplicateKeyException("Can't add duplicate space member");
        }

        SpaceMember spaceMember = spacesCreationService.createMember(user, space, role);
        space.getChannels().forEach(c -> conversationService.createNewConversationMember(c.getConversation(), user));

        webSocketDeliveryService.notifyUserJoinedSpace(
                space.getMembers().stream().map(SpaceMember::getUser).toList(),
                user,
                space.getId());

        return spaceMember;
    }

    @Transactional
    public Space updateSpace(SpaceDTO spaceDTO) {
        Space space = spacesRepository.findById(spaceDTO.spaceId())
                .orElseThrow(() -> new SpaceNotFoundException(spaceDTO.spaceId()));

        space.setName(spaceDTO.spaceName());
        space.setDescription(spaceDTO.spaceDescription());
        return spacesRepository.save(space);
    }

    @Transactional
    public void deleteSpace(Long spaceId) {
        Space space = spacesRepository.findById(spaceId).orElseThrow(() -> new SpaceNotFoundException(spaceId));

        List<SpaceMember> spaceMemberBySpace = spaceMemberRepository.findSpaceMemberBySpace(space);
        spaceMemberRepository.deleteAll(spaceMemberBySpace);

        List<Channel> channelBySpace = channelRepository.findChannelBySpace(space);
        channelRepository.deleteAll(channelBySpace);

        spacesRepository.deleteById(spaceId);
    }

    @Transactional
    public SpaceMember updateSpaceMemberRole(Space space, SpaceMemberDTO spaceMemberDTO) {
        SpaceMember spaceMember = spaceMemberRepository.findByUser_IdAndSpace_Id(spaceMemberDTO.user().id(),
                space.getId()).orElseThrow(UserNotFoundException::new);

        spaceMember.setRole(spaceMemberDTO.role());
        return spaceMemberRepository.save(spaceMember);
    }
}