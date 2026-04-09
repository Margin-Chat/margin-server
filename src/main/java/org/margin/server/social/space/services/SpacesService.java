package org.margin.server.social.space.services;

import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.margin.server.social.channel.entities.Channel;
import org.margin.server.social.channel.repositories.ChannelRepository;
import org.margin.server.social.channel.services.ChannelService;
import org.margin.server.social.conversation.services.ConversationService;
import org.margin.server.social.margin.entities.Margin;
import org.margin.server.social.margin.repositories.MarginMemberRepository;
import org.margin.server.social.margin.service.MarginMapper;
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
import org.margin.server.users.models.dtos.UserDTO;
import org.margin.server.websocket.connection.ConnectionManager;
import org.margin.server.websocket.services.WebSocketDeliveryService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final MarginMapper marginMapper;
    private final ConnectionManager connectionManager;

    public SpacesService(SpacesRepository spacesRepository,
                         SpaceMemberRepository spaceMemberRepository,
                         ChannelRepository channelRepository,
                         ChannelService channelService,
                         MarginMemberRepository marginMemberRepository,
                         WebSocketDeliveryService webSocketDeliveryService,
                         SpacesCreationService spacesCreationService,
                         ConversationService conversationService,
                         MarginMapper marginMapper,
                         ConnectionManager connectionManager) {
        this.spacesRepository = spacesRepository;
        this.spaceMemberRepository = spaceMemberRepository;
        this.channelRepository = channelRepository;
        this.channelService = channelService;
        this.marginMemberRepository = marginMemberRepository;
        this.webSocketDeliveryService = webSocketDeliveryService;
        this.spacesCreationService = spacesCreationService;
        this.conversationService = conversationService;
        this.marginMapper = marginMapper;
        this.connectionManager = connectionManager;
    }

    @Transactional(readOnly = true)
    public List<SpaceDTO> getSpacesForMargin(Long marginId) {
        return spacesRepository.findByMargin_Id(marginId).stream()
                .map(marginMapper::spaceToDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SpaceDTO> getSpacesForUserInMargin(User user, Long marginId) {
        return spacesRepository.findByUserAndMargin(user.getId(), marginId).stream()
                .map(marginMapper::spaceToDto)
                .toList();
    }

    @Transactional
    public SpaceDTO createNewSpace(CreateSpaceDTO dto, User user, Margin margin, boolean isDefault) {
        Optional<Space> spaceByName = spacesRepository.getSpaceByName(dto.name(), margin.getId());
        if (spaceByName.isPresent()) {
            throw new DuplicateKeyException("Space name already exists");
        }

        Space space = spacesCreationService.create(dto.name(), dto.description(), dto.visibility(), margin, isDefault);
        channelService.createNewChannel(space, "General Chat", "A channel for general conversation");
        addNewUserToSpaceInternal(user, space, SpaceRole.ADMIN);

        return marginMapper.spaceToDto(space);
    }

    @Transactional(readOnly = true)
    public @NotNull Space getById(Long id) {
        return spacesRepository.findById(id)
                .orElseThrow(() -> new SpaceNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<Space> getDefaultSpacesForMargin(Long marginId) {
        return spacesRepository.findDefaultSpacesByMarginId(marginId);
    }

    @Transactional
    public void addUsersToDefaultSpacesForMargin(Long marginId, User user) {
        List<Space> defaultSpacesForMargin = getDefaultSpacesForMargin(marginId);
        for (Space space : defaultSpacesForMargin) {
            addNewUserToSpaceInternal(user, space, SpaceRole.MEMBER);
        }
    }

    @Transactional
    protected SpaceMember addNewUserToSpaceInternal(User user, Space space, SpaceRole role) {
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
    public SpaceMemberDTO addNewUserToSpace(User user, Long spaceId, SpaceRole role) {
        Space space = spacesRepository.findById(spaceId)
                .orElseThrow(() -> new SpaceNotFoundException(spaceId));

        SpaceMember spaceMember = addNewUserToSpaceInternal(user, space, role);
        User memberUser = spaceMember.getUser();

        return new SpaceMemberDTO(
                new UserDTO(
                        memberUser.getId(),
                        memberUser.getHandle(),
                        memberUser.getDisplayName(),
                        memberUser.getEmail(),
                        memberUser.getProfilePictureUrl(),
                        memberUser.getCreatedAt(),
                        connectionManager.isUserOnline(memberUser.getId())
                ),
                spaceMember.getSpace().getId(),
                spaceMember.getRole(),
                spaceMember.getJoinedAt()
        );
    }

    @Transactional
    public SpaceDTO updateSpace(SpaceDTO spaceDTO) {
        Space space = spacesRepository.findById(spaceDTO.spaceId())
                .orElseThrow(() -> new SpaceNotFoundException(spaceDTO.spaceId()));

        space.setName(spaceDTO.spaceName());
        space.setDescription(spaceDTO.spaceDescription());
        Space saved = spacesRepository.save(space);

        return marginMapper.spaceToDto(saved);
    }

    @Transactional
    public void deleteSpace(Long spaceId) {
        Space space = spacesRepository.findById(spaceId)
                .orElseThrow(() -> new SpaceNotFoundException(spaceId));

        List<SpaceMember> spaceMemberBySpace = spaceMemberRepository.findSpaceMemberBySpace(space);
        spaceMemberRepository.deleteAll(spaceMemberBySpace);

        List<Channel> channelBySpace = channelRepository.findChannelBySpace(space);
        channelRepository.deleteAll(channelBySpace);

        spacesRepository.deleteById(spaceId);
    }

    @Transactional
    public SpaceMemberDTO updateSpaceMemberRole(Space space, SpaceMemberDTO spaceMemberDTO) {
        SpaceMember spaceMember = spaceMemberRepository
                .findByUser_IdAndSpace_Id(spaceMemberDTO.user().id(), space.getId())
                .orElseThrow(UserNotFoundException::new);

        spaceMember.setRole(spaceMemberDTO.role());
        SpaceMember saved = spaceMemberRepository.save(spaceMember);

        User user = saved.getUser();

        return new SpaceMemberDTO(
                new UserDTO(
                        user.getId(),
                        user.getHandle(),
                        user.getDisplayName(),
                        user.getEmail(),
                        user.getProfilePictureUrl(),
                        user.getCreatedAt(),
                        connectionManager.isUserOnline(user.getId())
                ),
                saved.getSpace().getId(),
                saved.getRole(),
                saved.getJoinedAt()
        );
    }

    @Transactional
    public void removeUserFromSpaces(Long userId, List<Long> spaces) {
        List<Space> fetchedSpaces = spaces.stream()
                .map(this::getById)
                .toList();

        for (Space space : fetchedSpaces) {
            channelService.removeUserFromChannels(userId, space.getChannels());
            List<SpaceMember> members = space.getMembers();
            members.removeIf(m -> m.getUser().getId().equals(userId));
            spaceMemberRepository.saveAll(members);
        }
    }

    @Transactional
    public void removeUserFromSpaces(Long userId, Margin margin) {
        List<Space> spaces = spacesRepository.findByMargin_Id(margin.getId());
        for (Space space : spaces) {
            channelService.removeUserFromChannels(userId, space.getChannels());
            List<SpaceMember> members = space.getMembers();
            members.removeIf(m -> m.getUser().getId().equals(userId));
            spaceMemberRepository.saveAll(members);
        }
    }
}
