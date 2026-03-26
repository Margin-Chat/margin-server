package org.margin.server.social.space.services;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.margin.server.social.channel.channel.Channel;
import org.margin.server.social.channel.repositories.ChannelRepository;
import org.margin.server.social.channel.services.ChannelService;
import org.margin.server.social.conversation.models.Conversation;
import org.margin.server.social.conversation.models.ConversationMember;
import org.margin.server.social.conversation.repositories.ConversationMemberRepository;
import org.margin.server.social.conversation.repositories.ConversationRepository;
import org.margin.server.social.margin.entities.Margin;
import org.margin.server.social.margin.repositories.MarginMemberRepository;
import org.margin.server.social.space.exceptions.SpaceNotFoundException;
import org.margin.server.social.space.exceptions.UserNotInMargin;
import org.margin.server.social.space.models.Space;
import org.margin.server.social.space.models.SpaceMember;
import org.margin.server.social.space.models.SpaceRole;
import org.margin.server.social.space.models.dtos.CreateSpaceDTO;
import org.margin.server.social.space.models.dtos.SpaceDTO;
import org.margin.server.social.space.repositories.SpaceMemberRepository;
import org.margin.server.social.space.repositories.SpacesRepository;
import org.margin.server.users.models.User;
import org.margin.server.users.repositories.UserRepository;
import org.margin.server.websocket.services.WebSocketDeliveryService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SpacesService {
    private final SpacesRepository spacesRepository;
    private final SpaceMemberRepository spaceMemberRepository;
    private final UserRepository userRepository;
    private final ChannelRepository channelRepository;
    private final ChannelService channelService;
    private final MarginMemberRepository marginMemberRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository conversationMemberRepository;
    private final EntityManager entityManager;
    private final WebSocketDeliveryService webSocketDeliveryService;

    public SpacesService(SpacesRepository spacesRepository,
                         SpaceMemberRepository spaceMemberRepository,
                         UserRepository userRepository,
                         ChannelRepository channelRepository,
                         ChannelService channelService,
                         MarginMemberRepository marginMemberRepository,
                         ConversationRepository conversationRepository,
                         ConversationMemberRepository conversationMemberRepository,
                         EntityManager entityManager, WebSocketDeliveryService webSocketDeliveryService) {
        this.spacesRepository = spacesRepository;
        this.spaceMemberRepository = spaceMemberRepository;
        this.userRepository = userRepository;
        this.channelRepository = channelRepository;
        this.channelService = channelService;
        this.marginMemberRepository = marginMemberRepository;
        this.conversationRepository = conversationRepository;
        this.conversationMemberRepository = conversationMemberRepository;
        this.entityManager = entityManager;
        this.webSocketDeliveryService = webSocketDeliveryService;
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

        Space newSpace = new Space();
        newSpace.setName(dto.name());
        newSpace.setDescription(dto.description());
        newSpace.setVisibility(dto.visibility());
        newSpace.setMargin(margin);
        newSpace.setDefault(isDefault);
        Space space = spacesRepository.save(newSpace);

        log.info("Created space {}", space.getId());
        space = spacesRepository.findById(space.getId()).orElseThrow();
        channelService.createChannel(space, "General Chat", "A channel for general conversation");
        entityManager.flush();
        entityManager.refresh(space);

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

    @Transactional
    public SpaceMember addNewUserToSpace(User user, Space space, SpaceRole role) {
        if (!marginMemberRepository
                .existsByUser_IdAndMargin_Id(user.getId(), space.getMargin().getId())) {
            throw new UserNotInMargin(user.getId());
        }

        if (spaceMemberRepository.existsSpaceMemberByUserAndSpace(user, space)) {
            throw new DuplicateKeyException("Can't add duplicate space member");
        }

        SpaceMember spaceMember = createSpaceMember(user, space, role);
        for (Channel channel : space.getChannels()) {
            Conversation conversation = conversationRepository.findByChannel(channel)
                    .orElseThrow(() -> new RuntimeException("No conversation for channel " + channel.getId()));
            ConversationMember conversationMember = new ConversationMember();
            conversationMember.setUser(user);
            conversationMember.setConversation(conversation);
            conversationMember.setJoinedAt(Instant.now());
            conversationMemberRepository.save(conversationMember);
        }

        webSocketDeliveryService.notifyUserJoinedSpace(
                spaceMemberRepository.findSpaceMemberBySpace(space).stream()
                        .map(SpaceMember::getUser)
                        .collect(Collectors.toList()),
                user,
                space.getId());

        return spaceMemberRepository.save(spaceMember);
    }

    @Transactional
    protected SpaceMember createSpaceMember(User u, Space space, SpaceRole role) {
        SpaceMember spaceMember = new SpaceMember();
        spaceMember.setSpace(space);
        spaceMember.setUser(u);
        spaceMember.setRole(role);
        spaceMember.setJoinedAt(Instant.now());
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
}